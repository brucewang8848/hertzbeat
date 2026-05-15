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

import com.obs.services.ObsClient;
import com.obs.services.ObsConfiguration;
import com.obs.services.exception.ObsException;
import com.obs.services.model.BucketStorageInfo;
import com.obs.services.model.ListObjectsRequest;
import com.obs.services.model.ObjectListing;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.ObsObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.hertzbeat.collector.dispatch.DispatchConstants;
import org.apache.hertzbeat.common.constants.CommonConstants;
import org.apache.hertzbeat.common.entity.job.Metrics;
import org.apache.hertzbeat.common.entity.job.protocol.ObjectStorageProtocol;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Huawei OBS (Object Storage Service) collector.
 */
@Slf4j
public class ObsCollectImpl extends AbstractObjectStorageCollectImpl<ObsClient> {

    @Override
    public String supportProtocol() {
        return DispatchConstants.PROTOCOL_OBS;
    }

    @Override
    protected ObjectStorageProtocol getProtocol(Metrics metrics) {
        return metrics.getObs();
    }

    @Override
    protected ObsClient buildClient(ObjectStorageProtocol protocol) {
        ObsConfiguration config = new ObsConfiguration();
        config.setEndPoint(protocol.getEndpoint());
        long timeoutMs = parseTimeout(protocol.getTimeout());
        config.setSocketTimeout((int) timeoutMs);
        config.setConnectionTimeout((int) timeoutMs);
        return new ObsClient(protocol.getAccessKey(), protocol.getSecretKey(), config);
    }

    @Override
    protected void closeClient(ObsClient client) {
        try {
            client.close();
        } catch (Exception e) {
            log.warn("Close OBS client failed", e);
        }
    }

    @Override
    protected Map<String, String> doHeadObject(ObsClient client, ObjectStorageProtocol protocol) {
        String resolvedKey = resolveDateVariables(protocol.getObjectKey(), protocol.getTimezone());
        Map<String, String> data = new HashMap<>(8);
        data.put("objectKey", resolvedKey);

        try {
            ObjectMetadata metadata = client.getObjectMetadata(protocol.getBucket(), resolvedKey);
            data.put("exists", "true");
            data.put("fileSize", String.valueOf(metadata.getContentLength()));
            data.put("lastModified", metadata.getLastModified() != null
                    ? metadata.getLastModified().toString() : CommonConstants.NULL_VALUE);
        } catch (ObsException e) {
            if (e.getResponseCode() == 404) {
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
    protected Map<String, String> doListObjects(ObsClient client, ObjectStorageProtocol protocol) {
        String resolvedKey = resolveDateVariables(protocol.getObjectKey(), protocol.getTimezone());
        Map<String, String> data = new HashMap<>(8);
        data.put("prefix", resolvedKey);

        ListObjectsRequest request = new ListObjectsRequest(protocol.getBucket());
        request.setPrefix(resolvedKey);
        request.setMaxKeys(getMaxListKeys());

        ObjectListing listing = client.listObjects(request);
        List<ObsObject> objects = listing.getObjects();

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

            for (ObsObject obj : objects) {
                long size = obj.getMetadata().getContentLength();
                totalSize += size;
                if (size == 0 && obj.getObjectKey().endsWith("/")) {
                    continue;
                }
                actualFileCount++;
                long lastModified = obj.getMetadata().getLastModified() != null
                        ? obj.getMetadata().getLastModified().getTime() : 0;
                if (lastModified > latestTime) {
                    latestTime = lastModified;
                    latestFile = obj.getObjectKey();
                    latestModified = obj.getMetadata().getLastModified() != null
                            ? obj.getMetadata().getLastModified().toString() : CommonConstants.NULL_VALUE;
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
    protected Map<String, String> doHeadBucket(ObsClient client, ObjectStorageProtocol protocol) {
        Map<String, String> data = new HashMap<>(4);
        data.put("bucket", protocol.getBucket());

        try {
            boolean exists = client.headBucket(protocol.getBucket());
            data.put("exists", Boolean.toString(exists));
            data.put("accessible", Boolean.toString(exists));
        } catch (ObsException e) {
            if (e.getResponseCode() == 404) {
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
    protected Map<String, String> doGetBucketStorageInfo(ObsClient client, ObjectStorageProtocol protocol) {
        Map<String, String> data = new HashMap<>(4);
        data.put("bucket", protocol.getBucket());

        try {
            BucketStorageInfo storageInfo = client.getBucketStorageInfo(protocol.getBucket());
            data.put("bucketSize", String.valueOf(storageInfo.getSize()));
            data.put("objectCount", String.valueOf(storageInfo.getObjectNumber()));
        } catch (ObsException e) {
            data.put("bucketSize", CommonConstants.NULL_VALUE);
            data.put("objectCount", CommonConstants.NULL_VALUE);
        }

        return data;
    }
}
