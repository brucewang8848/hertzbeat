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
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * S3 protocol
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class S3Protocol implements Protocol {

    /**
     * 服务端点（如 https://s3.cn-north-1.amazonaws.com.cn）
     */
    private String endpoint;

    /**
     * 区域（如 cn-north-1）
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
     * Bucket 名称
     */
    private String bucket;

    /**
     * 对象键（支持原子级日期变量：{yyyy}, {yy}, {MM}, {dd}, {HH}, {mm}, {ss}）
     * <p>
     * 文件监控示例：data/{yyyy}/{MM}/{dd}/report.csv
     * 目录监控示例：data/{yyyy}/{MM}/{dd}/
     * </p>
     */
    private String objectKey;

    /**
     * 路径风格访问（true/false），MinIO/OBS 等需要设置为 true
     */
    private String pathStyle;

    /**
     * 日期变量解析时区（如 Asia/Shanghai），默认 JVM 时区
     */
    private String timezone;

    /**
     * 超时时间（毫秒），同时用于连接超时和读取超时
     */
    private String timeout;
}
