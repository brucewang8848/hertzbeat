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

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.BucketStat;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.SimplifiedObjectMeta;
import lombok.extern.slf4j.Slf4j;
import org.apache.hertzbeat.collector.dispatch.DispatchConstants;
import org.apache.hertzbeat.common.constants.CommonConstants;
import org.apache.hertzbeat.common.entity.job.Metrics;
import org.apache.hertzbeat.common.entity.job.protocol.ObjectStorageProtocol;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Alibaba Cloud OSS (Object Storage Service) collector.
 */
@Slf4j
public class OssCollectImpl extends AbstractObjectStorageCollectImpl<OSS> {

    @Override
    public String supportProtocol() {
        return DispatchConstants.PROTOCOL_OSS;
    }

    @Override
    protected ObjectStorageProtocol getProtocol(Metrics metrics) {
        return metrics.getOss();
    }

    @Override
    protected OSS buildClient(ObjectStorageProtocol protocol) {
        CredentialsProvider credentialsProvider = new DefaultCredentialProvider(
                protocol.getAccessKey(), protocol.getSecretKey());

        ClientBuilderConfiguration config = new ClientBuilderConfiguration();
        long timeoutMs = parseTimeout(protocol.getTimeout());
        config.setSocketTimeout((int) timeoutMs);
        config.setConnectionTimeout((int) timeoutMs);
        config.setSignatureVersion(SignVersion.V4);

        String region = parseRegionFromEndpoint(protocol.getEndpoint());

        return OSSClientBuilder.create()
                .credentialsProvider(credentialsProvider)
                .clientConfiguration(config)
                .region(region)
                .endpoint(protocol.getEndpoint())
                .build();
    }

    /**
     * Parse region from endpoint URL.
     * e.g., https://oss-cn-hangzhou.aliyuncs.com -> cn-hangzhou
     */
    private String parseRegionFromEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "";
        }
        String url = endpoint.replaceAll("^https?://", "");
        if (url.startsWith("oss-")) {
            int dotIndex = url.indexOf('.');
            if (dotIndex > 4) {
                return url.substring(4, dotIndex);
            }
        }
        return "";
    }

    @Override
    protected void closeClient(OSS client) {
        if (client != null) {
            try {
                client.shutdown();
            } catch (Exception e) {
                log.warn("Shutdown OSS client failed", e);
            }
        }
    }

    @Override
    protected Map<String, String> doHeadObject(OSS client, ObjectStorageProtocol protocol) {
        String resolvedKey = resolveDateVariables(protocol.getObjectKey(), protocol.getTimezone());
        Map<String, String> data = new HashMap<>(8);
        data.put("objectKey", resolvedKey);

        try {
            SimplifiedObjectMeta meta = client.getSimplifiedObjectMeta(protocol.getBucket(), resolvedKey);
            data.put("exists", "true");
            data.put("fileSize", String.valueOf(meta.getSize()));
            Date lastModified = meta.getLastModified();
            data.put("lastModified", lastModified != null
                    ? lastModified.toString() : CommonConstants.NULL_VALUE);
        } catch (OSSException e) {
            if (e.getErrorCode() != null && e.getErrorCode().contains("NoSuchKey")) {
                data.put("exists", "false");
                data.put("fileSize", "0");
                data.put("lastModified", CommonConstants.NULL_VALUE);
            } else {
                throw e;
            }
        }

        return data;
    }

    @Override
    protected Map<String, String> doListObjects(OSS client, ObjectStorageProtocol protocol) {
        String resolvedKey = resolveDateVariables(protocol.getObjectKey(), protocol.getTimezone());
        Map<String, String> data = new HashMap<>(8);
        data.put("prefix", resolvedKey);

        ListObjectsRequest request = new ListObjectsRequest(protocol.getBucket());
        request.setPrefix(resolvedKey);
        request.setMaxKeys(getMaxListKeys());

        ObjectListing listing = client.listObjects(request);
        List<OSSObjectSummary> objects = listing.getObjectSummaries();

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
            long latestTime = 0;

            for (OSSObjectSummary obj : objects) {
                long size = obj.getSize();
                totalSize += size;
                if (size == 0 && obj.getKey() != null && obj.getKey().endsWith("/")) {
                    continue;
                }
                actualFileCount++;
                Date lastModified = obj.getLastModified();
                long lastModifiedTime = lastModified != null ? lastModified.getTime() : 0;
                if (lastModifiedTime > latestTime) {
                    latestTime = lastModifiedTime;
                    latestFile = obj.getKey();
                    latestModified = lastModified != null ? lastModified.toString() : CommonConstants.NULL_VALUE;
                }
            }

            data.put("fileCount", String.valueOf(actualFileCount));
            data.put("latestFile", latestFile != null ? latestFile : CommonConstants.NULL_VALUE);
            data.put("latestModified", latestModified != null ? latestModified : CommonConstants.NULL_VALUE);
            data.put("totalSize", String.valueOf(totalSize));
        }

        return data;
    }

    @Override
    protected Map<String, String> doHeadBucket(OSS client, ObjectStorageProtocol protocol) {
        Map<String, String> data = new HashMap<>(4);
        data.put("bucket", protocol.getBucket());

        try {
            boolean exists = client.doesBucketExist(protocol.getBucket());
            data.put("exists", Boolean.toString(exists));
            data.put("accessible", Boolean.toString(exists));
        } catch (OSSException e) {
            if (e.getErrorCode() != null && e.getErrorCode().contains("NoSuchBucket")) {
                data.put("exists", "false");
                data.put("accessible", "false");
            } else {
                data.put("exists", "true");
                data.put("accessible", "false");
            }
        }

        return data;
    }

    @Override
    protected Map<String, String> doGetBucketStorageInfo(OSS client, ObjectStorageProtocol protocol) {
        Map<String, String> data = new HashMap<>(4);
        data.put("bucket", protocol.getBucket());

        try {
            BucketStat stat = client.getBucketStat(protocol.getBucket());
            data.put("bucketSize", String.valueOf(stat.getStorageSize()));
            data.put("objectCount", String.valueOf(stat.getObjectCount()));
        } catch (OSSException e) {
            data.put("bucketSize", CommonConstants.NULL_VALUE);
            data.put("objectCount", CommonConstants.NULL_VALUE);
        }

        return data;
    }
}
