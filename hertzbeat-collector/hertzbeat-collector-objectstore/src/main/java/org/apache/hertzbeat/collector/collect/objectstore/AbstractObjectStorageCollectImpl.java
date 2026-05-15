/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hertzbeat.collector.collect.objectstore;

import lombok.extern.slf4j.Slf4j;
import org.apache.hertzbeat.collector.collect.AbstractCollect;
import org.apache.hertzbeat.common.constants.CommonConstants;
import org.apache.hertzbeat.common.entity.job.Metrics;
import org.apache.hertzbeat.common.entity.job.protocol.ObjectStorageProtocol;
import org.apache.hertzbeat.common.entity.message.CollectRep;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abstract base class for object storage collectors (OBS, OSS, COS, etc.).
 * <p>
 * Extracts common logic: preCheck, collect template, resolveDateVariables,
 * parseTimeout, appendValueRow, and operation inference.
 * Subclasses only need to implement SDK-specific operations.
 * </p>
 *
 * @param <C> the SDK client type (e.g. ObsClient, OSS)
 */
@Slf4j
public abstract class AbstractObjectStorageCollectImpl<C> extends AbstractCollect {

    /** Date variable regex pattern: matches {yyyy}, {yy}, {MM}, {dd}, {HH}, {mm}, {ss} */
    private static final Pattern DATE_VAR_PATTERN =
            Pattern.compile("\\{(yyyy|yy|MM|dd|HH|mm|ss)\\}");

    /** Default timeout in milliseconds */
    private static final long DEFAULT_TIMEOUT_MS = 30000;

    /** Max keys returned by ListObjects for directory monitoring */
    private static final int MAX_LIST_KEYS = 1000;

    /** Operation type constants */
    public static final String OPERATION_HEAD_OBJECT = "headObject";
    public static final String OPERATION_LIST_OBJECTS = "listObjects";
    public static final String OPERATION_HEAD_BUCKET = "headBucket";
    public static final String OPERATION_GET_BUCKET_STORAGE_INFO = "getBucketStorageInfo";

    /**
     * Get storage type name from protocol (e.g. "obs" -> "OBS", "oss" -> "OSS").
     */
    private String getStorageType() {
        String protocol = supportProtocol();
        return protocol.toUpperCase();
    }

    @Override
    public void preCheck(Metrics metrics) throws IllegalArgumentException {
        if (metrics == null) {
            throw new IllegalArgumentException(getStorageType() + " collect metrics is null");
        }
        ObjectStorageProtocol protocol = getProtocol(metrics);
        if (protocol == null) {
            throw new IllegalArgumentException(getStorageType() + " collect must has "
                    + supportProtocol() + " params");
        }
        Assert.hasText(protocol.getEndpoint(), getStorageType() + " endpoint is required");
        Assert.hasText(protocol.getBucket(), getStorageType() + " bucket name is required");
        Assert.hasText(protocol.getAccessKey(), getStorageType() + " access key is required");
        Assert.hasText(protocol.getSecretKey(), getStorageType() + " secret key is required");
        if (CollectionUtils.isEmpty(metrics.getAliasFields())) {
            throw new IllegalArgumentException(getStorageType() + " aliasFields is required");
        }
        if (protocol.getTimezone() != null && !protocol.getTimezone().isBlank()) {
            try {
                ZoneId.of(protocol.getTimezone());
            } catch (DateTimeException e) {
                throw new IllegalArgumentException(getStorageType() + " timezone is invalid: "
                        + protocol.getTimezone());
            }
        }
        String operation = getOperation(protocol);
        if (OPERATION_HEAD_OBJECT.equals(operation) || OPERATION_LIST_OBJECTS.equals(operation)) {
            Assert.hasText(protocol.getObjectKey(),
                    getStorageType() + " object key is required for operation: " + operation);
        }
    }

    @Override
    public void collect(CollectRep.MetricsData.Builder builder, Metrics metrics) {
        ObjectStorageProtocol protocol = getProtocol(metrics);
        String operation = getOperation(protocol);

        C client = null;
        try {
            client = buildClient(protocol);

            long startTime = System.currentTimeMillis();

            Map<String, String> data = switch (operation) {
                case OPERATION_HEAD_OBJECT -> doHeadObject(client, protocol);
                case OPERATION_LIST_OBJECTS -> doListObjects(client, protocol);
                case OPERATION_HEAD_BUCKET -> doHeadBucket(client, protocol);
                case OPERATION_GET_BUCKET_STORAGE_INFO -> doGetBucketStorageInfo(client, protocol);
                default -> throw new IllegalArgumentException("Unsupported " + getStorageType()
                        + " operation: " + operation);
            };

            long responseTime = System.currentTimeMillis() - startTime;
            data.put("responseTime", String.valueOf(responseTime));

            appendValueRow(builder, metrics, data);
        } catch (Exception e) {
            builder.setCode(CollectRep.Code.FAIL);
            builder.setMsg(getStorageType() + " collect error: " + e.getMessage());
            log.error("{} collect failed for bucket: {}, operation: {}", getStorageType(),
                    protocol.getBucket(), operation, e);
        } finally {
            if (client != null) {
                closeClient(client);
            }
        }
    }

    // ---- Abstract methods to be implemented by subclasses ----

    /**
     * Get the protocol instance from metrics.
     */
    protected abstract ObjectStorageProtocol getProtocol(Metrics metrics);

    /**
     * Build the SDK client instance.
     */
    protected abstract C buildClient(ObjectStorageProtocol protocol);

    /**
     * Close the SDK client instance.
     */
    protected abstract void closeClient(C client);

    /**
     * HeadObject: get object metadata (existence, size, lastModified).
     */
    protected abstract Map<String, String> doHeadObject(C client, ObjectStorageProtocol protocol);

    /**
     * ListObjects: list objects under prefix (fileCount, totalSize, latestFile, latestModified).
     */
    protected abstract Map<String, String> doListObjects(C client, ObjectStorageProtocol protocol);

    /**
     * HeadBucket: check bucket existence and accessibility.
     */
    protected abstract Map<String, String> doHeadBucket(C client, ObjectStorageProtocol protocol);

    /**
     * GetBucketStorageInfo: get bucket storage statistics (bucketSize, objectCount).
     */
    protected abstract Map<String, String> doGetBucketStorageInfo(C client, ObjectStorageProtocol protocol);

    // ---- Common utility methods ----

    /**
     * Get operation type, default to headObject (for backward compatibility)
     */
    protected String getOperation(ObjectStorageProtocol protocol) {
        if (StringUtils.hasText(protocol.getOperation())) {
            return protocol.getOperation();
        }
        if (protocol.getObjectKey() != null && protocol.getObjectKey().endsWith("/")) {
            return OPERATION_LIST_OBJECTS;
        }
        return OPERATION_HEAD_OBJECT;
    }

    /**
     * Resolve atomic date variables in object key
     */
    protected String resolveDateVariables(String objectKey, String timezone) {
        if (objectKey == null || !objectKey.contains("{")) {
            return objectKey;
        }

        ZoneId zoneId = (timezone != null && !timezone.isBlank())
                ? ZoneId.of(timezone)
                : ZoneId.systemDefault();

        ZonedDateTime now = ZonedDateTime.now(zoneId);
        Matcher matcher = DATE_VAR_PATTERN.matcher(objectKey);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String replacement = switch (matcher.group(1)) {
                case "yyyy" -> String.format("%04d", now.getYear());
                case "yy" -> String.format("%02d", now.getYear() % 100);
                case "MM" -> String.format("%02d", now.getMonthValue());
                case "dd" -> String.format("%02d", now.getDayOfMonth());
                case "HH" -> String.format("%02d", now.getHour());
                case "mm" -> String.format("%02d", now.getMinute());
                case "ss" -> String.format("%02d", now.getSecond());
                default -> matcher.group(0);
            };
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Parse timeout configuration
     */
    protected long parseTimeout(String timeout) {
        if (timeout != null && !timeout.isBlank()) {
            try {
                return Long.parseLong(timeout);
            } catch (NumberFormatException e) {
                log.warn("Invalid timeout value: {}, using default: {}", timeout, DEFAULT_TIMEOUT_MS);
            }
        }
        return DEFAULT_TIMEOUT_MS;
    }

    protected long getDefaultTimeoutMs() {
        return DEFAULT_TIMEOUT_MS;
    }

    protected int getMaxListKeys() {
        return MAX_LIST_KEYS;
    }

    /**
     * Build collected data as ValueRow and append to MetricsData
     */
    private void appendValueRow(CollectRep.MetricsData.Builder builder, Metrics metrics, Map<String, String> data) {
        CollectRep.ValueRow.Builder valueRowBuilder = CollectRep.ValueRow.newBuilder();
        for (String column : metrics.getAliasFields()) {
            String value = data.get(column);
            value = (value != null) ? value : CommonConstants.NULL_VALUE;
            valueRowBuilder.addColumn(value);
        }
        builder.addValueRow(valueRowBuilder.build());
    }
}
