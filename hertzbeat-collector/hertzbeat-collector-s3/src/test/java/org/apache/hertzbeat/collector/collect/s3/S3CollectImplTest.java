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
import software.amazon.awssdk.services.s3.model.GetBucketLocationResponse;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * S3CollectImpl unit test class
 * Test various operation types of S3 collector
 */
@ExtendWith(MockitoExtension.class)
class S3CollectImplTest {

    @InjectMocks
    private S3CollectImpl s3CollectImpl;

    private Metrics metrics;
    private S3Protocol s3Protocol;
    private List<String> aliasFields;

    @BeforeEach
    void setUp() {
        s3Protocol = S3Protocol.builder()
            .endpoint("https://s3.oss-cn-beijing.aliyuncs.com")
            .region("cn-beijing")
            .accessKey("LTAI5t5ruSh4bZYHQpsMzUq8")
            .secretKey("N38bRvQBFH2vA75gvOFdnyLtWyG28u")
            .bucket("bigdata-s34")
            .objectKey("project-plan.docx")
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
     * Test supportProtocol method returns correct protocol name "s3"
     */
    @Test
    void testSupportProtocol() {
        assertEquals("s3", s3CollectImpl.supportProtocol());
    }

    /**
     * Test preCheck method passes validation when parameters are complete
     */
    @Test
    void testPreCheckSuccess() {
        s3CollectImpl.preCheck(metrics);
    }

    /**
     * Test preCheck method throws IllegalArgumentException when metrics is null
     */
    @Test
    void testPreCheckNullMetrics() {
        assertThrows(IllegalArgumentException.class, () -> s3CollectImpl.preCheck(null));
    }

    /**
     * Test preCheck method throws IllegalArgumentException when S3 protocol is null
     */
    @Test
    void testPreCheckNullS3() {
        metrics.setS3(null);
        assertThrows(IllegalArgumentException.class, () -> s3CollectImpl.preCheck(metrics));
    }

    /**
     * Test preCheck method throws IllegalArgumentException when endpoint is empty
     */
    @Test
    void testPreCheckMissingEndpoint() {
        s3Protocol.setEndpoint("");
        assertThrows(IllegalArgumentException.class, () -> s3CollectImpl.preCheck(metrics));
    }

    /**
     * Test preCheck method throws IllegalArgumentException when bucket is empty
     */
    @Test
    void testPreCheckMissingBucket() {
        s3Protocol.setBucket("");
        assertThrows(IllegalArgumentException.class, () -> s3CollectImpl.preCheck(metrics));
    }

    /**
     * Test preCheck method throws IllegalArgumentException when objectKey is empty
     */
    @Test
    void testPreCheckMissingObjectKey() {
        s3Protocol.setObjectKey("");
        assertThrows(IllegalArgumentException.class, () -> s3CollectImpl.preCheck(metrics));
    }

    /**
     * Test preCheck method throws IllegalArgumentException when accessKey is empty
     */
    @Test
    void testPreCheckMissingAccessKey() {
        s3Protocol.setAccessKey("");
        assertThrows(IllegalArgumentException.class, () -> s3CollectImpl.preCheck(metrics));
    }

    /**
     * Test preCheck method throws IllegalArgumentException when secretKey is empty
     */
    @Test
    void testPreCheckMissingSecretKey() {
        s3Protocol.setSecretKey("");
        assertThrows(IllegalArgumentException.class, () -> s3CollectImpl.preCheck(metrics));
    }

    /**
     * Test resolveDateVariables method returns original key when no date variables
     */
    @Test
    void testResolveDateVariablesNoVars() {
        String key = "data/file.txt";
        assertEquals(key, s3CollectImpl.resolveDateVariables(key, null));
    }

    /**
     * Test resolveDateVariables method correctly parses date variables
     */
    @Test
    void testResolveDateVariablesWithVars() {
        String template = "data/{yyyy}/{MM}/{dd}/report.csv";
        String result = s3CollectImpl.resolveDateVariables(template, null);
        assertTrue(result.matches("data/\\d{4}/\\d{2}/\\d{2}/report.csv"));
        assertTrue(!result.contains("{"));
    }

    /**
     * Test resolveDateVariables method with specified timezone
     */
    @Test
    void testResolveDateVariablesWithTimezone() {
        String template = "data/{yyyy}/{MM}/{dd}/{HH}.csv";
        String result = s3CollectImpl.resolveDateVariables(template, "Asia/Shanghai");
        assertTrue(result.matches("data/\\d{4}/\\d{2}/\\d{2}/\\d{2}\\.csv"));
    }

    /**
     * Test headObject operation when file exists
     */
    @Test
    void testCollectHeadObjectFileExists() {
        CollectRep.MetricsData.Builder builder = CollectRep.MetricsData.newBuilder();
        s3Protocol.setOperation("headObject");

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
            assertEquals("project-plan.docx", row.getColumns(0));
            assertEquals("true", row.getColumns(1));
            assertEquals("1024", row.getColumns(2));
            assertNotNull(row.getColumns(3));
            assertNotNull(row.getColumns(4));
        }
    }

    /**
     * Test headObject operation when file does not exist (NoSuchKeyException)
     */
    @Test
    void testCollectHeadObjectFileNotExists() {
        CollectRep.MetricsData.Builder builder = CollectRep.MetricsData.newBuilder();
        s3Protocol.setOperation("headObject");

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
            assertEquals("false", row.getColumns(1));
            assertEquals("0", row.getColumns(2));
            assertEquals(CommonConstants.NULL_VALUE, row.getColumns(3));
            assertNotNull(row.getColumns(4));
        }
    }

    /**
     * Test listObjects operation with multiple files in directory
     */
    @Test
    void testCollectListObjectsWithFiles() {
        CollectRep.MetricsData.Builder builder = CollectRep.MetricsData.newBuilder();
        s3Protocol.setObjectKey("data/logs/");
        s3Protocol.setOperation("listObjects");

        aliasFields = new ArrayList<>();
        aliasFields.add("prefix");
        aliasFields.add("fileCount");
        aliasFields.add("totalSize");
        aliasFields.add("latestFile");
        aliasFields.add("latestModified");
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
            assertEquals("300", row.getColumns(2));
            assertEquals("data/logs/file2.log", row.getColumns(3));
            assertNotNull(row.getColumns(4));
            assertNotNull(row.getColumns(5));
        }
    }

    /**
     * Test listObjects operation with empty directory
     */
    @Test
    void testCollectListObjectsEmpty() {
        CollectRep.MetricsData.Builder builder = CollectRep.MetricsData.newBuilder();
        s3Protocol.setObjectKey("data/empty/");
        s3Protocol.setOperation("listObjects");

        aliasFields = new ArrayList<>();
        aliasFields.add("prefix");
        aliasFields.add("fileCount");
        aliasFields.add("totalSize");
        aliasFields.add("latestFile");
        aliasFields.add("latestModified");
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
            assertEquals("0", row.getColumns(2));
            assertEquals(CommonConstants.NULL_VALUE, row.getColumns(3));
            assertEquals(CommonConstants.NULL_VALUE, row.getColumns(4));
            assertNotNull(row.getColumns(5));
        }
    }

    /**
     * Test headBucket operation when bucket exists and is accessible
     */
    @Test
    void testCollectHeadBucketAccessible() {
        CollectRep.MetricsData.Builder builder = CollectRep.MetricsData.newBuilder();
        s3Protocol.setOperation("headBucket");
        s3Protocol.setObjectKey(null);

        aliasFields = new ArrayList<>();
        aliasFields.add("bucket");
        aliasFields.add("exists");
        aliasFields.add("accessible");
        aliasFields.add("responseTime");
        metrics.setAliasFields(aliasFields);

        S3Client mockClient = mock(S3Client.class);
        when(mockClient.headBucket(any(software.amazon.awssdk.services.s3.model.HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());

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
            assertEquals("bigdata-s34", row.getColumns(0));
            assertEquals("true", row.getColumns(1));
            assertEquals("true", row.getColumns(2));
            assertNotNull(row.getColumns(3));
        }
    }

    /**
     * Test headBucket operation when bucket does not exist
     */
    @Test
    void testCollectHeadBucketNotExists() {
        CollectRep.MetricsData.Builder builder = CollectRep.MetricsData.newBuilder();
        s3Protocol.setOperation("headBucket");
        s3Protocol.setObjectKey(null);

        aliasFields = new ArrayList<>();
        aliasFields.add("bucket");
        aliasFields.add("exists");
        aliasFields.add("accessible");
        aliasFields.add("responseTime");
        metrics.setAliasFields(aliasFields);

        NoSuchKeyException notFoundException = NoSuchKeyException.builder()
                .message("NoSuchBucket")
                .statusCode(404)
                .build();

        S3Client mockClient = mock(S3Client.class);
        when(mockClient.headBucket(any(software.amazon.awssdk.services.s3.model.HeadBucketRequest.class)))
                .thenThrow(notFoundException);

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
            assertEquals("false", row.getColumns(2));
        }
    }

    /**
     * Test getBucketLocation operation
     */
    @Test
    void testCollectGetBucketLocation() {
        CollectRep.MetricsData.Builder builder = CollectRep.MetricsData.newBuilder();
        s3Protocol.setOperation("getBucketLocation");
        s3Protocol.setObjectKey(null);

        aliasFields = new ArrayList<>();
        aliasFields.add("bucket");
        aliasFields.add("region");
        aliasFields.add("responseTime");
        metrics.setAliasFields(aliasFields);

        GetBucketLocationResponse locationResponse = GetBucketLocationResponse.builder()
                .locationConstraint(software.amazon.awssdk.services.s3.model.BucketLocationConstraint.CN_NORTH_1)
                .build();

        S3Client mockClient = mock(S3Client.class);
        when(mockClient.getBucketLocation(any(software.amazon.awssdk.services.s3.model.GetBucketLocationRequest.class)))
                .thenReturn(locationResponse);

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
            assertEquals("bigdata-s34", row.getColumns(0));
            assertEquals("cn-north-1", row.getColumns(1));
            assertNotNull(row.getColumns(2));
        }
    }

    /**
     * Test getBucketVersioning operation
     */
    @Test
    void testCollectGetBucketVersioning() {
        CollectRep.MetricsData.Builder builder = CollectRep.MetricsData.newBuilder();
        s3Protocol.setOperation("getBucketVersioning");
        s3Protocol.setObjectKey(null);

        aliasFields = new ArrayList<>();
        aliasFields.add("bucket");
        aliasFields.add("versioning");
        aliasFields.add("responseTime");
        metrics.setAliasFields(aliasFields);

        GetBucketVersioningResponse versioningResponse = GetBucketVersioningResponse.builder()
                .status(software.amazon.awssdk.services.s3.model.BucketVersioningStatus.ENABLED)
                .build();

        S3Client mockClient = mock(S3Client.class);
        when(mockClient.getBucketVersioning(any(software.amazon.awssdk.services.s3.model.GetBucketVersioningRequest.class)))
                .thenReturn(versioningResponse);

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
            assertEquals("bigdata-s34", row.getColumns(0));
            assertEquals("Enabled", row.getColumns(1));
            assertNotNull(row.getColumns(2));
        }
    }

    /**
     * Test default mode: headObject when objectKey does not end with /
     */
    @Test
    void testCollectDefaultHeadObject() {
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
            assertEquals("true", builder.getValuesList().get(0).getColumns(1));
        }
    }

    /**
     * Test default mode: listObjects when objectKey ends with /
     */
    @Test
    void testCollectDefaultListObjects() {
        CollectRep.MetricsData.Builder builder = CollectRep.MetricsData.newBuilder();
        s3Protocol.setObjectKey("data/logs/");

        aliasFields = new ArrayList<>();
        aliasFields.add("prefix");
        aliasFields.add("fileCount");
        aliasFields.add("responseTime");
        metrics.setAliasFields(aliasFields);

        S3Object obj = S3Object.builder()
                .key("data/logs/file1.log")
                .size(100L)
                .lastModified(Instant.now())
                .build();

        ListObjectsV2Response listResponse = ListObjectsV2Response.builder()
                .contents(obj)
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
            assertEquals("1", builder.getValuesList().get(0).getColumns(1));
        }
    }
}
