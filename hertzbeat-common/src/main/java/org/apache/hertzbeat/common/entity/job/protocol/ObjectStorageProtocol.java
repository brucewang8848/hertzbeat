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

package org.apache.hertzbeat.common.entity.job.protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Base protocol for object storage services (OBS, OSS, COS, etc.)
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public abstract class ObjectStorageProtocol implements Protocol {

    /**
     * Service endpoint
     */
    private String endpoint;

    /**
     * Region
     */
    private String region;

    /**
     * Access Key ID
     */
    private String accessKey;

    /**
     * Secret Access Key
     */
    private String secretKey;

    /**
     * Bucket name
     */
    private String bucket;

    /**
     * Object key (supports atomic date variables: {yyyy}, {yy}, {MM}, {dd}, {HH}, {mm}, {ss})
     * <p>
     * File monitoring example: data/{yyyy}/{MM}/{dd}/report.csv
     * Directory monitoring example: data/{yyyy}/{MM}/{dd}/
     * </p>
     */
    private String objectKey;

    /**
     * Date variable parsing timezone (e.g. Asia/Shanghai), default JVM timezone
     */
    private String timezone;

    /**
     * Timeout in milliseconds
     */
    private String timeout;

    /**
     * Operation type
     * <p>
     * Supported operations:
     * - headObject: Get object metadata (file existence, size, modification time)
     * - listObjects: List objects (directory statistics, latest file)
     * - headBucket: Check bucket existence and accessibility
     * - getBucketStorageInfo: Get bucket storage info (size, object count)
     * </p>
     */
    private String operation;
}
