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
import org.apache.hertzbeat.common.constants.MetricDataConstants;
import org.apache.hertzbeat.common.entity.job.Metrics;
import org.apache.hertzbeat.common.entity.job.protocol.S3Protocol;
import org.apache.hertzbeat.common.entity.message.CollectRep;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
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
 * S3 兼容对象存储采集器
 * <p>
 * 支持 Amazon S3、MinIO、华为 OBS、阿里 OSS 等 S3 兼容对象存储的文件/目录监控。
 * 通过 HeadObject 检测文件存在性，通过 ListObjectsV2 检测目录状态。
 * 对象键支持原子级日期变量：{yyyy}, {yy}, {MM}, {dd}, {HH}, {mm}, {ss}。
 * </p>
 * <p>
 * responseTime 处理方式：与 HertzBeat 内置采集器（如 FTP）保持一致，
 * 在采集操作完成后将耗时直接放入数据 Map 中，构建 ValueRow 时一次性写入，
 * 避免对已构建的 MetricsData 进行二次修改（v1.7.2 基于 Apache Arrow，不支持 toBuilder）。
 * </p>
 */
@Slf4j
public class S3CollectImpl extends AbstractCollect {

    /** 日期变量正则模式：匹配 {yyyy}, {yy}, {MM}, {dd}, {HH}, {mm}, {ss} */
    private static final Pattern DATE_VAR_PATTERN =
            Pattern.compile("\\{(yyyy|yy|MM|dd|HH|mm|ss)\\}");

    /** 默认超时时间（毫秒） */
    private static final long DEFAULT_TIMEOUT_MS = 30000;

    /** 目录监控 ListObjectsV2 最大返回数量 */
    private static final int MAX_LIST_KEYS = 1000;

    @Override
    public void preCheck(Metrics metrics) throws IllegalArgumentException {
        if (metrics == null || metrics.getS3() == null) {
            throw new IllegalArgumentException("S3 collect must has s3 params");
        }
        S3Protocol s3 = metrics.getS3();
        Assert.hasText(s3.getEndpoint(), "S3 endpoint is required");
        Assert.hasText(s3.getBucket(), "S3 bucket name is required");
        Assert.hasText(s3.getObjectKey(), "S3 object key is required");
        Assert.hasText(s3.getAccessKey(), "S3 access key is required");
        Assert.hasText(s3.getSecretKey(), "S3 secret key is required");
        // 校验 aliasFields 不能为空，避免 appendValueRow 中 NPE
        if (CollectionUtils.isEmpty(metrics.getAliasFields())) {
            throw new IllegalArgumentException("S3 aliasFields is required");
        }
        // 校验 endpoint 的 URI 格式合法性
        try {
            URI.create(s3.getEndpoint());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("S3 endpoint format is invalid: " + s3.getEndpoint());
        }
        // 校验时区合法性（如果配置了）
        if (s3.getTimezone() != null && !s3.getTimezone().isBlank()) {
            try {
                ZoneId.of(s3.getTimezone());
            } catch (DateTimeException e) {
                throw new IllegalArgumentException("S3 timezone is invalid: " + s3.getTimezone());
            }
        }
    }

    @Override
    public void collect(CollectRep.MetricsData.Builder builder, Metrics metrics) {
        S3Protocol s3 = metrics.getS3();
        String resolvedKey = resolveDateVariables(s3.getObjectKey(), s3.getTimezone());

        // 设置 instance 为 bucket:objectKey 格式，便于在监控详情页面识别
        // hertzbeat 会自动生成 instance，但这是对有 host 的情况，如果没有 host 参数，则 instance 为 null，这里手动设置
        builder.addMetadata(MetricDataConstants.INSTANCE, s3.getBucket() + ":" + resolvedKey);

        // 判断监控模式：objectKey 以 / 结尾 → 目录监控，否则 → 文件监控
        boolean isDirectoryMode = resolvedKey.endsWith("/");

        S3Client s3Client = null;
        try {
            s3Client = buildS3Client(s3);

            // 先计时，再将 responseTime 与采集数据一起构建 ValueRow
            // 这与 HertzBeat 内置采集器（FTP/HTTP 等）的处理方式一致
            long startTime = System.currentTimeMillis();

            Map<String, String> data;
            if (isDirectoryMode) {
                data = collectDirectory(s3Client, s3.getBucket(), resolvedKey);
            } else {
                data = collectFile(s3Client, s3.getBucket(), resolvedKey);
            }

            long responseTime = System.currentTimeMillis() - startTime;
            data.put("responseTime", String.valueOf(responseTime));

            // 根据 aliasFields 顺序构建 ValueRow，一次性写入所有数据（含 responseTime）
            appendValueRow(builder, metrics, data);
        } catch (Exception e) {
            builder.setCode(CollectRep.Code.FAIL);
            builder.setMsg("S3 collect error: " + e.getMessage());
            log.error("S3 collect failed for bucket: {}, key: {}", s3.getBucket(), resolvedKey, e);
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
     * 文件存在性检测：通过 HeadObject API
     * <p>
     * 返回包含 objectKey、exists、fileSize、lastModified 的数据 Map。
     * 404（NoSuchKey）视为正常监控结果（文件不存在），不抛异常。
     * </p>
     *
     * @return 采集数据 Map，不包含 responseTime（由调用方统一填充）
     */
    private Map<String, String> collectFile(S3Client s3Client, String bucket, String objectKey) {
        Map<String, String> data = new HashMap<>(8);
        data.put("objectKey", objectKey);

        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build();

            HeadObjectResponse response = s3Client.headObject(request);
            data.put("exists", "true");
            // contentLength 可能为 null，需做空值保护
            Long contentLength = response.contentLength();
            data.put("fileSize", contentLength != null ? String.valueOf(contentLength) : "0");
            data.put("lastModified", response.lastModified() != null ? response.lastModified().toString() : CommonConstants.NULL_VALUE);
        } catch (S3Exception e) {
            // 404：文件不存在，这是正常监控结果，不是错误
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
     * 目录监控：通过 ListObjectsV2 API
     * <p>
     * 返回包含 prefix、fileCount、latestFile、latestModified、totalSize 的数据 Map。
     * 仅扫描前 1000 个对象（S3 单次 List 上限），适用于文件数量可控的目录场景。
     * fileCount 仅统计真实文件对象，排除目录标记对象（以 / 结尾且大小为 0）。
     * </p>
     *
     * @return 采集数据 Map，不包含 responseTime（由调用方统一填充）
     */
    private Map<String, String> collectDirectory(S3Client s3Client, String bucket, String prefix) {
        Map<String, String> data = new HashMap<>(8);
        data.put("prefix", prefix);

        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
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
                // 跳过目录标记对象（以 / 结尾且大小为 0）
                if (obj.size() == 0 && obj.key().endsWith("/")) {
                    continue;
                }
                actualFileCount++;
                // 某些非标准 S3 实现可能返回 lastModified 为 null，需做空值保护
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
     * 解析对象键中的原子级日期变量
     * <p>
     * 支持的变量：{yyyy}, {yy}, {MM}, {dd}, {HH}, {mm}, {ss}
     * 未识别的占位符保持原样。
     * </p>
     *
     * @param objectKey 包含日期变量的对象键模板
     * @param timezone  时区标识（如 Asia/Shanghai），为空时使用 JVM 默认时区
     * @return 解析后的对象键
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
                default -> matcher.group(0); // 未知变量保持原样
            };
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 构建 S3Client 实例
     */
    private S3Client buildS3Client(S3Protocol s3) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey());
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);

        long timeoutMs = parseTimeout(s3.getTimeout());

        S3Configuration.Builder configBuilder = S3Configuration.builder()
                // 路径风格访问：MinIO、华为 OBS 等需要设置为 true，AWS S3 使用 false（虚拟主机样式）
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

        // 区域设置：如果指定了 region 则使用，否则使用默认
        if (s3.getRegion() != null && !s3.getRegion().isBlank()) {
            clientBuilder.region(Region.of(s3.getRegion()));
        }

        return clientBuilder.build();
    }

    /**
     * 解析超时时间配置
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
     * 将采集数据构建为 ValueRow 并追加到 MetricsData
     * <p>
     * 按照 YAML 模板中 aliasFields 定义的顺序，从数据 Map 中逐列取值构建 ValueRow。
     * responseTime 已由调用方在 collect 方法中放入 data Map，此处一并处理。
     * 这种方式与 HertzBeat 内置采集器（FTP、HTTP 等）保持一致，
     * 在 ValueRow 构建时一次性写入所有数据，避免对 MetricsData 进行二次修改。
     * </p>
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
