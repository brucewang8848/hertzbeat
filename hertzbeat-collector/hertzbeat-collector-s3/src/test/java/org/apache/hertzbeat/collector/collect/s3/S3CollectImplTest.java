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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.hertzbeat.common.constants.CommonConstants;
import org.apache.hertzbeat.common.entity.job.Metrics;
import org.apache.hertzbeat.common.entity.job.protocol.S3Protocol;
import org.apache.hertzbeat.common.entity.message.CollectRep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * S3CollectImpl 单元测试类
 */
@ExtendWith(MockitoExtension.class)
class S3CollectImplTest {

    @InjectMocks
    private S3CollectImpl s3CollectImpl;

    private Metrics metrics;
    private S3Protocol s3Protocol;
    private List<String> aliasFields;

    private final String existObjectKey = "开发项目计划书.docx";

    @BeforeEach
    void setUp() {
        s3Protocol = S3Protocol.builder()
            .endpoint("https://s3.oss-cn-beijing.aliyuncs.com")
            .region("cn-beijing")
            .accessKey("LTAI5t5ruSh4bZYHQpsMzUq8")
            .secretKey("N38bRvQBFH2vA75gvOFdnyLtWyG28u")
            .bucket("bigdata-s34")
            .objectKey(existObjectKey)
            .pathStyle("false")
            .timeout("30000")
            .build();

        aliasFields = new ArrayList<>();
        aliasFields.add("objectKey");
        aliasFields.add("exists");
        aliasFields.add("fileSize");
        aliasFields.add("lastModified");
        aliasFields.add("responseTime");

        metrics = new Metrics();
        metrics.setName("s3_file");
        metrics.setS3(s3Protocol);
        metrics.setAliasFields(aliasFields);
    }

    /**
     * 测试 supportProtocol 方法返回正确的协议名称 "s3"
     */
    @Test
    void testSupportProtocol() {
        assertEquals("s3", s3CollectImpl.supportProtocol());
    }

    /**
     * 测试 preCheck 方法在参数完整时正常通过校验，不抛出异常
     */
    @Test
    void testPreCheckSuccess() {
        s3CollectImpl.preCheck(metrics);
    }

    /**
     * 测试 preCheck 方法在 metrics 为 null 时抛出 IllegalArgumentException
     */
    @Test
    void testPreCheckNullMetrics() {
        assertThrows(IllegalArgumentException.class, () -> s3CollectImpl.preCheck(null));
    }

    /**
     * 测试 preCheck 方法在 S3 协议配置为 null 时抛出 IllegalArgumentException
     */
    @Test
    void testPreCheckNullS3() {
        metrics.setS3(null);
        assertThrows(IllegalArgumentException.class, () -> s3CollectImpl.preCheck(metrics));
    }

    /**
     * 测试 preCheck 方法在 endpoint 为空时抛出 IllegalArgumentException
     */
    @Test
    void testPreCheckMissingEndpoint() {
        s3Protocol.setEndpoint("");
        assertThrows(IllegalArgumentException.class, () -> s3CollectImpl.preCheck(metrics));
    }

    /**
     * 测试 preCheck 方法在 bucket 为空时抛出 IllegalArgumentException
     */
    @Test
    void testPreCheckMissingBucket() {
        s3Protocol.setBucket("");
        assertThrows(IllegalArgumentException.class, () -> s3CollectImpl.preCheck(metrics));
    }

    /**
     * 测试 preCheck 方法在 objectKey 为空时抛出 IllegalArgumentException
     */
    @Test
    void testPreCheckMissingObjectKey() {
        s3Protocol.setObjectKey("");
        assertThrows(IllegalArgumentException.class, () -> s3CollectImpl.preCheck(metrics));
    }

    /**
     * 测试 preCheck 方法在 accessKey 为空时抛出 IllegalArgumentException
     */
    @Test
    void testPreCheckMissingAccessKey() {
        s3Protocol.setAccessKey("");
        assertThrows(IllegalArgumentException.class, () -> s3CollectImpl.preCheck(metrics));
    }

    /**
     * 测试 preCheck 方法在 secretKey 为空时抛出 IllegalArgumentException
     */
    @Test
    void testPreCheckMissingSecretKey() {
        s3Protocol.setSecretKey("");
        assertThrows(IllegalArgumentException.class, () -> s3CollectImpl.preCheck(metrics));
    }

    /**
     * 测试 resolveDateVariables 方法在对象键不包含日期变量时原样返回
     */
    @Test
    void testResolveDateVariablesNoVars() {
        String key = "data/file.txt";
        assertEquals(key, s3CollectImpl.resolveDateVariables(key, null));
    }

    /**
     * 测试 resolveDateVariables 方法正确解析日期变量（{yyyy}, {MM}, {dd} 等）
     */
    @Test
    void testResolveDateVariablesWithVars() {
        String template = "data/{yyyy}/{MM}/{dd}/report.csv";
        String result = s3CollectImpl.resolveDateVariables(template, null);
        assertTrue(result.matches("data/\\d{4}/\\d{2}/\\d{2}/report.csv"));
        assertTrue(!result.contains("{"));
    }

    /**
     * 测试 resolveDateVariables 方法使用指定时区解析日期变量
     */
    @Test
    void testResolveDateVariablesWithTimezone() {
        String template = "data/{yyyy}/{MM}/{dd}/{HH}.csv";
        String result = s3CollectImpl.resolveDateVariables(template, "Asia/Shanghai");
        assertTrue(result.matches("data/\\d{4}/\\d{2}/\\d{2}/\\d{2}\\.csv"));
    }

    /**
     * 测试文件存在时的采集结果：
     * - exists 为 true
     * - fileSize 为实际大小
     * - lastModified 有值
     * - responseTime 有值
     */
    @Test
    void testCollectFileExists() {
        CollectRep.MetricsData.Builder builder = CollectRep.MetricsData.newBuilder();

        HeadObjectResponse headResponse = HeadObjectResponse.builder()
                .contentLength(1024L)
                .lastModified(Instant.now())
                .build();

        S3Client mockClient = mock(S3Client.class);
        when(mockClient.headObject(any(HeadObjectRequest.class))).thenReturn(headResponse);

        try (var s3ClientStatic = mockStatic(S3Client.class)) {
            S3ClientBuilder mockS3ClientBuilder = mock(S3ClientBuilder.class);
            s3ClientStatic.when(S3Client::builder).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.endpointOverride(any(URI.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.credentialsProvider(any())).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.serviceConfiguration(any(S3Configuration.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.overrideConfiguration(any(ClientOverrideConfiguration.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.region(any(Region.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.build()).thenReturn(mockClient);

            s3CollectImpl.collect(builder, metrics);

            assertEquals(1, builder.getValuesCount());
            CollectRep.ValueRow row = builder.getValuesList().get(0);
            assertEquals(existObjectKey, row.getColumns(0));
            assertEquals("true", row.getColumns(1));
            assertEquals("1024", row.getColumns(2));
            assertNotNull(row.getColumns(3));
            assertNotNull(row.getColumns(4));
        }
    }

    /**
     * 测试文件不存在（抛出 NoSuchKeyException）时的采集结果：
     * - exists 为 false
     * - fileSize 为 0
     * - lastModified 为 NULL_VALUE
     * - responseTime 有值
     */
    @Test
    void testCollectFileNotExists() {
        CollectRep.MetricsData.Builder builder = CollectRep.MetricsData.newBuilder();

        NoSuchKeyException noSuchKeyException = NoSuchKeyException.builder()
                .message("Not found")
                .statusCode(404)
                .build();

        S3Client mockClient = mock(S3Client.class);
        when(mockClient.headObject(any(HeadObjectRequest.class))).thenThrow(noSuchKeyException);

        try (var s3ClientStatic = mockStatic(S3Client.class)) {
            S3ClientBuilder mockS3ClientBuilder = mock(S3ClientBuilder.class);
            s3ClientStatic.when(S3Client::builder).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.endpointOverride(any(URI.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.credentialsProvider(any())).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.serviceConfiguration(any(S3Configuration.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.overrideConfiguration(any(ClientOverrideConfiguration.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.region(any(Region.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.build()).thenReturn(mockClient);

            s3CollectImpl.collect(builder, metrics);

            assertEquals(1, builder.getValuesCount());
            CollectRep.ValueRow row = builder.getValuesList().get(0);
            assertEquals(existObjectKey, row.getColumns(0));
            assertEquals("false", row.getColumns(1));
            assertEquals("0", row.getColumns(2));
            assertEquals(CommonConstants.NULL_VALUE, row.getColumns(3));
            assertNotNull(row.getColumns(4));
        }
    }

    /**
     * 测试文件不存在（抛出 S3Exception 且 statusCode 为 404）时的采集结果：
     * - exists 为 false
     * - fileSize 为 0
     * - lastModified 为 NULL_VALUE
     */
    @Test
    void testCollectFileNotExistsS3Exception404() {
        CollectRep.MetricsData.Builder builder = CollectRep.MetricsData.newBuilder();

        S3Exception s3Exception = mock(S3Exception.class);
        when(s3Exception.statusCode()).thenReturn(404);

        S3Client mockClient = mock(S3Client.class);
        when(mockClient.headObject(any(HeadObjectRequest.class))).thenThrow(s3Exception);

        try (var s3ClientStatic = mockStatic(S3Client.class)) {
            S3ClientBuilder mockS3ClientBuilder = mock(S3ClientBuilder.class);
            s3ClientStatic.when(S3Client::builder).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.endpointOverride(any(URI.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.credentialsProvider(any())).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.serviceConfiguration(any(S3Configuration.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.overrideConfiguration(any(ClientOverrideConfiguration.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.region(any(Region.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.build()).thenReturn(mockClient);

            s3CollectImpl.collect(builder, metrics);

            assertEquals(1, builder.getValuesCount());
            CollectRep.ValueRow row = builder.getValuesList().get(0);
            assertEquals("false", row.getColumns(1));
            assertEquals("0", row.getColumns(2));
            assertEquals(CommonConstants.NULL_VALUE, row.getColumns(3));
        }
    }

    /**
     * 测试 S3 服务端返回非 404 错误（如 500）时的采集结果：
     * - 采集状态为 FAIL
     * - 错误信息包含 "S3 collect error"
     */
    @Test
    void testCollectFileS3Exception() {
        CollectRep.MetricsData.Builder builder = CollectRep.MetricsData.newBuilder();

        S3Exception s3Exception = mock(S3Exception.class);
        when(s3Exception.statusCode()).thenReturn(500);
        when(s3Exception.getMessage()).thenReturn("Internal Error");

        S3Client mockClient = mock(S3Client.class);
        when(mockClient.headObject(any(HeadObjectRequest.class))).thenThrow(s3Exception);

        try (var s3ClientStatic = mockStatic(S3Client.class)) {
            S3ClientBuilder mockS3ClientBuilder = mock(S3ClientBuilder.class);
            s3ClientStatic.when(S3Client::builder).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.endpointOverride(any(URI.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.credentialsProvider(any())).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.serviceConfiguration(any(S3Configuration.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.overrideConfiguration(any(ClientOverrideConfiguration.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.region(any(Region.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.build()).thenReturn(mockClient);

            s3CollectImpl.collect(builder, metrics);

            assertEquals(CollectRep.Code.FAIL, builder.getCode());
            assertTrue(builder.getMsg().contains("S3 collect error"));
        }
    }

    /**
     * 测试目录下有多个文件时的采集结果：
     * - fileCount 为实际文件数量
     * - latestFile 为最新修改的文件
     * - totalSize 为所有文件大小之和
     */
    @Test
    void testCollectDirectoryWithFiles() {
        CollectRep.MetricsData.Builder builder = CollectRep.MetricsData.newBuilder();
        s3Protocol.setObjectKey("data/logs/");

        aliasFields = new ArrayList<>();
        aliasFields.add("prefix");
        aliasFields.add("fileCount");
        aliasFields.add("latestFile");
        aliasFields.add("latestModified");
        aliasFields.add("totalSize");
        aliasFields.add("responseTime");
        metrics.setAliasFields(aliasFields);

        Instant now = Instant.now();
        S3Object obj1 = S3Object.builder()
                .key("data/logs/file1.log")
                .size(100L)
                .lastModified(now.minusSeconds(3600))
                .build();
        S3Object obj2 = S3Object.builder()
                .key("data/logs/file2.log")
                .size(200L)
                .lastModified(now)
                .build();

        ListObjectsV2Response listResponse = ListObjectsV2Response.builder()
                .contents(obj1, obj2)
                .build();

        S3Client mockClient = mock(S3Client.class);
        when(mockClient.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResponse);

        try (var s3ClientStatic = mockStatic(S3Client.class)) {
            S3ClientBuilder mockS3ClientBuilder = mock(S3ClientBuilder.class);
            s3ClientStatic.when(S3Client::builder).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.endpointOverride(any(URI.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.credentialsProvider(any())).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.serviceConfiguration(any(S3Configuration.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.overrideConfiguration(any(ClientOverrideConfiguration.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.region(any(Region.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.build()).thenReturn(mockClient);

            s3CollectImpl.collect(builder, metrics);

            assertEquals(1, builder.getValuesCount());
            CollectRep.ValueRow row = builder.getValuesList().get(0);
            assertEquals("data/logs/", row.getColumns(0));
            assertEquals("2", row.getColumns(1));
            assertEquals("data/logs/file2.log", row.getColumns(2));
            assertNotNull(row.getColumns(3));
            assertEquals("300", row.getColumns(4));
            assertNotNull(row.getColumns(5));
        }
    }

    /**
     * 测试空目录时的采集结果：
     * - fileCount 为 0
     * - latestFile 为 NULL_VALUE
     * - latestModified 为 NULL_VALUE
     * - totalSize 为 0
     */
    @Test
    void testCollectDirectoryEmpty() {
        CollectRep.MetricsData.Builder builder = CollectRep.MetricsData.newBuilder();
        s3Protocol.setObjectKey("data/empty/");

        aliasFields = new ArrayList<>();
        aliasFields.add("prefix");
        aliasFields.add("fileCount");
        aliasFields.add("latestFile");
        aliasFields.add("latestModified");
        aliasFields.add("totalSize");
        aliasFields.add("responseTime");
        metrics.setAliasFields(aliasFields);

        ListObjectsV2Response listResponse = ListObjectsV2Response.builder()
                .contents(Collections.emptyList())
                .build();

        S3Client mockClient = mock(S3Client.class);
        when(mockClient.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResponse);

        try (var s3ClientStatic = mockStatic(S3Client.class)) {
            S3ClientBuilder mockS3ClientBuilder = mock(S3ClientBuilder.class);
            s3ClientStatic.when(S3Client::builder).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.endpointOverride(any(URI.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.credentialsProvider(any())).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.serviceConfiguration(any(S3Configuration.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.overrideConfiguration(any(ClientOverrideConfiguration.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.region(any(Region.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.build()).thenReturn(mockClient);

            s3CollectImpl.collect(builder, metrics);

            assertEquals(1, builder.getValuesCount());
            CollectRep.ValueRow row = builder.getValuesList().get(0);
            assertEquals("data/empty/", row.getColumns(0));
            assertEquals("0", row.getColumns(1));
            assertEquals(CommonConstants.NULL_VALUE, row.getColumns(2));
            assertEquals(CommonConstants.NULL_VALUE, row.getColumns(3));
            assertEquals("0", row.getColumns(4));
            assertNotNull(row.getColumns(5));
        }
    }

    /**
     * 测试目录包含目录标记对象（以 / 结尾且大小为 0）时的采集结果：
     * - 目录标记对象不计入 fileCount
     * - 目录标记对象不作为 latestFile 的候选
     * - totalSize 不包含目录标记对象的大小
     */
    @Test
    void testCollectDirectoryWithDirectoryMarkers() {
        CollectRep.MetricsData.Builder builder = CollectRep.MetricsData.newBuilder();
        s3Protocol.setObjectKey("data/logs/");

        aliasFields = new ArrayList<>();
        aliasFields.add("prefix");
        aliasFields.add("fileCount");
        aliasFields.add("latestFile");
        aliasFields.add("latestModified");
        aliasFields.add("totalSize");
        aliasFields.add("responseTime");
        metrics.setAliasFields(aliasFields);

        Instant now = Instant.now();
        S3Object dirMarker = S3Object.builder()
                .key("data/logs/")
                .size(0L)
                .lastModified(now)
                .build();
        S3Object file = S3Object.builder()
                .key("data/logs/file.log")
                .size(100L)
                .lastModified(now.minusSeconds(60))
                .build();

        ListObjectsV2Response listResponse = ListObjectsV2Response.builder()
                .contents(dirMarker, file)
                .build();

        S3Client mockClient = mock(S3Client.class);
        when(mockClient.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResponse);

        try (var s3ClientStatic = mockStatic(S3Client.class)) {
            S3ClientBuilder mockS3ClientBuilder = mock(S3ClientBuilder.class);
            s3ClientStatic.when(S3Client::builder).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.endpointOverride(any(URI.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.credentialsProvider(any())).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.serviceConfiguration(any(S3Configuration.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.overrideConfiguration(any(ClientOverrideConfiguration.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.region(any(Region.class))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.build()).thenReturn(mockClient);

            s3CollectImpl.collect(builder, metrics);

            assertEquals(1, builder.getValuesCount());
            CollectRep.ValueRow row = builder.getValuesList().get(0);
            // 目录标记对象在计算 latestFile 时被跳过
            assertEquals("data/logs/file.log", row.getColumns(2));
            assertEquals("100", row.getColumns(4));
        }
    }
}
