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

package org.apache.hertzbeat.collector.collect.s3;

import lombok.extern.slf4j.Slf4j;
import org.apache.hertzbeat.collector.collect.AbstractCollect;
import org.apache.hertzbeat.collector.dispatch.DispatchConstants;
import org.apache.hertzbeat.common.constants.CommonConstants;
import org.apache.hertzbeat.common.entity.job.Metrics;
import org.apache.hertzbeat.common.entity.job.protocol.S3Protocol;
import org.apache.hertzbeat.common.entity.message.CollectRep;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetBucketLocationRequest;
import software.amazon.awssdk.services.s3.model.GetBucketLocationResponse;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URI;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * S3 compatible object storage collector.
 * <p>
 * Supports monitoring of Amazon S3, MinIO, Huawei OBS, Alibaba OSS and other S3 compatible storage.
 * Supports multiple S3 operation types (headObject, listObjects, headBucket, getBucketLocation, getBucketVersioning).
 * Object keys support atomic date variables: {yyyy}, {yy}, {MM}, {dd}, {HH}, {mm}, {ss}.
 * </p>
 * <p>
 * responseTime handling: Consistent with HertzBeat built-in collectors (e.g. FTP),
 * put the elapsed time into the data Map after collection, write it once when building ValueRow,
 * to avoid secondary modification of built MetricsData (v1.7.2 based on Apache Arrow, does not support toBuilder).
 * </p>
 */
@Slf4j
public class S3CollectImpl extends AbstractCollect {

    /** Date variable regex pattern: matches {yyyy}, {yy}, {MM}, {dd}, {HH}, {mm}, {ss} */
    private static final Pattern DATE_VAR_PATTERN =
            Pattern.compile("\\{(yyyy|yy|MM|dd|HH|mm|ss)\\}");

    /** Default timeout in milliseconds */
    private static final long DEFAULT_TIMEOUT_MS = 30000;

    /** Max keys returned by ListObjectsV2 for directory monitoring */
    private static final int MAX_LIST_KEYS = 1000;

    /** S3 operation type constants */
    public static final String OPERATION_HEAD_OBJECT = "headObject";
    public static final String OPERATION_LIST_OBJECTS = "listObjects";
    public static final String OPERATION_HEAD_BUCKET = "headBucket";
    public static final String OPERATION_GET_BUCKET_LOCATION = "getBucketLocation";
    public static final String OPERATION_GET_BUCKET_VERSIONING = "getBucketVersioning";

    @Override
    public void preCheck(Metrics metrics) throws IllegalArgumentException {
        if (metrics == null || metrics.getS3() == null) {
            throw new IllegalArgumentException("S3 collect must has s3 params");
        }
        S3Protocol s3 = metrics.getS3();
        Assert.hasText(s3.getEndpoint(), "S3 endpoint is required");
        Assert.hasText(s3.getBucket(), "S3 bucket name is required");
        Assert.hasText(s3.getAccessKey(), "S3 access key is required");
        Assert.hasText(s3.getSecretKey(), "S3 secret key is required");
        // Validate aliasFields is not empty to avoid NPE in appendValueRow
        if (CollectionUtils.isEmpty(metrics.getAliasFields())) {
            throw new IllegalArgumentException("S3 aliasFields is required");
        }
        // Validate endpoint URI format
        try {
            URI.create(s3.getEndpoint());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("S3 endpoint format is invalid: " + s3.getEndpoint());
        }
        // Validate timezone if configured
        if (s3.getTimezone() != null && !s3.getTimezone().isBlank()) {
            try {
                ZoneId.of(s3.getTimezone());
            } catch (DateTimeException e) {
                throw new IllegalArgumentException("S3 timezone is invalid: " + s3.getTimezone());
            }
        }
        // Validate required parameters based on operation type
        String operation = getOperation(s3);
        if (OPERATION_HEAD_OBJECT.equals(operation) || OPERATION_LIST_OBJECTS.equals(operation)) {
            Assert.hasText(s3.getObjectKey(), "S3 object key is required for operation: " + operation);
        }
    }

    @Override
    public void collect(CollectRep.MetricsData.Builder builder, Metrics metrics) {
        S3Protocol s3 = metrics.getS3();
        String operation = getOperation(s3);

        S3Client s3Client = null;
        try {
            s3Client = buildS3Client(s3);

            // Start timing, then put responseTime into data Map together
            long startTime = System.currentTimeMillis();

            Map<String, String> data = switch (operation) {
                case OPERATION_HEAD_OBJECT -> collectHeadObject(s3Client, s3);
                case OPERATION_LIST_OBJECTS -> collectListObjects(s3Client, s3);
                case OPERATION_HEAD_BUCKET -> collectHeadBucket(s3Client, s3);
                case OPERATION_GET_BUCKET_LOCATION -> collectGetBucketLocation(s3Client, s3);
                case OPERATION_GET_BUCKET_VERSIONING -> collectGetBucketVersioning(s3Client, s3);
                default -> throw new IllegalArgumentException("Unsupported S3 operation: " + operation);
            };

            long responseTime = System.currentTimeMillis() - startTime;
            data.put("responseTime", String.valueOf(responseTime));

            // Build ValueRow according to aliasFields order, write all data at once (including responseTime)
            appendValueRow(builder, metrics, data);
        } catch (Exception e) {
            builder.setCode(CollectRep.Code.FAIL);
            builder.setMsg("S3 collect error: " + e.getMessage());
            log.error("S3 collect failed for bucket: {}, operation: {}", s3.getBucket(), operation, e);
        } finally {
            if (s3Client != null) {
                try {
                    s3Client.close();
                } catch (Exception e) {
                    log.warn("Close S3 client failed", e);
                }
            }
        }
    }

    @Override
    public String supportProtocol() {
        return DispatchConstants.PROTOCOL_S3;
    }

    /**
     * Get operation type, default to headObject (for backward compatibility)
     */
    private String getOperation(S3Protocol s3) {
        if (StringUtils.hasText(s3.getOperation())) {
            return s3.getOperation();
        }
        // Backward compatibility: infer from objectKey ending with /
        if (s3.getObjectKey() != null && s3.getObjectKey().endsWith("/")) {
            return OPERATION_LIST_OBJECTS;
        }
        return OPERATION_HEAD_OBJECT;
    }

    /**
     * HeadObject operation: get object metadata
     */
    private Map<String, String> collectHeadObject(S3Client s3Client, S3Protocol s3) {
        String resolvedKey = resolveDateVariables(s3.getObjectKey(), s3.getTimezone());
        Map<String, String> data = new HashMap<>(8);
        data.put("objectKey", resolvedKey);

        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(s3.getBucket())
                    .key(resolvedKey)
                    .build();

            HeadObjectResponse response = s3Client.headObject(request);
            data.put("exists", "true");
            Long contentLength = response.contentLength();
            data.put("fileSize", contentLength != null ? String.valueOf(contentLength) : "0");
            data.put("lastModified", response.lastModified() != null ? response.lastModified().toString() : CommonConstants.NULL_VALUE);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                data.put("exists", "false");
                data.put("fileSize", "0");
                data.put("lastModified", CommonConstants.NULL_VALUE);
            } else {
                throw e;
            }
        }

        return data;
    }

    /**
     * ListObjects operation: list objects (directory statistics)
     */
    private Map<String, String> collectListObjects(S3Client s3Client, S3Protocol s3) {
        String resolvedKey = resolveDateVariables(s3.getObjectKey(), s3.getTimezone());
        Map<String, String> data = new HashMap<>(8);
        data.put("prefix", resolvedKey);

        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(s3.getBucket())
                .prefix(resolvedKey)
                .maxKeys(MAX_LIST_KEYS)
                .build();

        ListObjectsV2Response response = s3Client.listObjectsV2(request);
        List<S3Object> objects = response.contents();

        if (objects == null || objects.isEmpty()) {
            data.put("fileCount", "0");
            data.put("latestFile", CommonConstants.NULL_VALUE);
            data.put("latestModified", CommonConstants.NULL_VALUE);
            data.put("totalSize", "0");
        } else {
            long totalSize = 0;
            int actualFileCount = 0;
            String latestFile = null;
            String latestModified = null;
            Instant latestInstant = null;

            for (S3Object obj : objects) {
                totalSize += obj.size();
                if (obj.size() == 0 && obj.key().endsWith("/")) {
                    continue;
                }
                actualFileCount++;
                Instant objLastModified = obj.lastModified();
                if (objLastModified == null) {
                    continue;
                }
                if (latestInstant == null || objLastModified.isAfter(latestInstant)) {
                    latestInstant = objLastModified;
                    latestFile = obj.key();
                    latestModified = objLastModified.toString();
                }
            }

            data.put("fileCount", String.valueOf(actualFileCount));
            data.put("latestFile", latestFile != null ? latestFile : CommonConstants.NULL_VALUE);
            data.put("latestModified", latestModified != null ? latestModified : CommonConstants.NULL_VALUE);
            data.put("totalSize", String.valueOf(totalSize));
        }

        return data;
    }

    /**
     * HeadBucket operation: check bucket existence and accessibility
     */
    private Map<String, String> collectHeadBucket(S3Client s3Client, S3Protocol s3) {
        Map<String, String> data = new HashMap<>(4);
        data.put("bucket", s3.getBucket());

        try {
            HeadBucketRequest request = HeadBucketRequest.builder()
                    .bucket(s3.getBucket())
                    .build();
            s3Client.headBucket(request);
            data.put("exists", "true");
            data.put("accessible", "true");
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                data.put("exists", "false");
                data.put("accessible", "false");
            } else {
                data.put("exists", "true");
                data.put("accessible", "false");
            }
        }

        return data;
    }

    /**
     * GetBucketLocation operation: get bucket region
     */
    private Map<String, String> collectGetBucketLocation(S3Client s3Client, S3Protocol s3) {
        Map<String, String> data = new HashMap<>(4);
        data.put("bucket", s3.getBucket());

        try {
            GetBucketLocationRequest request = GetBucketLocationRequest.builder()
                    .bucket(s3.getBucket())
                    .build();
            GetBucketLocationResponse response = s3Client.getBucketLocation(request);
            String location = response.locationConstraintAsString();
            data.put("region", location != null ? location : "us-east-1");
        } catch (S3Exception e) {
            data.put("region", CommonConstants.NULL_VALUE);
        }

        return data;
    }

    /**
     * GetBucketVersioning operation: get versioning status
     */
    private Map<String, String> collectGetBucketVersioning(S3Client s3Client, S3Protocol s3) {
        Map<String, String> data = new HashMap<>(4);
        data.put("bucket", s3.getBucket());

        try {
            GetBucketVersioningRequest request = GetBucketVersioningRequest.builder()
                    .bucket(s3.getBucket())
                    .build();
            GetBucketVersioningResponse response = s3Client.getBucketVersioning(request);
            String status = response.statusAsString();
            data.put("versioning", status != null ? status : "Disabled");
        } catch (S3Exception e) {
            data.put("versioning", CommonConstants.NULL_VALUE);
        }

        return data;
    }

    /**
     * Resolve atomic date variables in object key
     */
    String resolveDateVariables(String objectKey, String timezone) {
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
     * Build S3Client instance
     */
    private S3Client buildS3Client(S3Protocol s3) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey());
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);

        long timeoutMs = parseTimeout(s3.getTimeout());

        S3Configuration.Builder configBuilder = S3Configuration.builder()
                .pathStyleAccessEnabled(Boolean.parseBoolean(s3.getPathStyle()));

        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .apiCallAttemptTimeout(Duration.ofMillis(timeoutMs))
                .apiCallTimeout(Duration.ofMillis(timeoutMs))
                .build();

        S3ClientBuilder clientBuilder = S3Client.builder();
        clientBuilder.endpointOverride(URI.create(s3.getEndpoint()))
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(configBuilder.build())
                .overrideConfiguration(overrideConfig);

        if (s3.getRegion() != null && !s3.getRegion().isBlank()) {
            clientBuilder.region(Region.of(s3.getRegion()));
        }

        return clientBuilder.build();
    }

    /**
     * Parse timeout configuration
     */
    private long parseTimeout(String timeout) {
        if (timeout != null && !timeout.isBlank()) {
            try {
                return Long.parseLong(timeout);
            } catch (NumberFormatException e) {
                log.warn("Invalid timeout value: {}, using default: {}", timeout, DEFAULT_TIMEOUT_MS);
            }
        }
        return DEFAULT_TIMEOUT_MS;
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
