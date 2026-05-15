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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import org.apache.hertzbeat.common.entity.job.Metrics;
import org.apache.hertzbeat.common.entity.job.protocol.OssProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * OssCollectImpl unit test class
 */
@ExtendWith(MockitoExtension.class)
class OssCollectImplTest {

    @InjectMocks
    private OssCollectImpl ossCollectImpl;

    private Metrics metrics;
    private OssProtocol ossProtocol;

    @BeforeEach
    void setUp() {
        ossProtocol = OssProtocol.builder()
                .endpoint("https://oss-cn-beijing.aliyuncs.com")
                .region("cn-beijing")
                .accessKey("test-access-key")
                .secretKey("test-secret-key")
                .bucket("test-bucket")
                .objectKey("开发项目计划书.docx")
                .timeout("30000")
                .build();

        ArrayList<String> aliasFields = new ArrayList<>();
        aliasFields.add("objectKey");
        aliasFields.add("exists");
        aliasFields.add("fileSize");
        aliasFields.add("lastModified");
        aliasFields.add("responseTime");

        metrics = new Metrics();
        metrics.setName("oss_file");
        metrics.setOss(ossProtocol);
        metrics.setAliasFields(aliasFields);
    }

    @Test
    void testSupportProtocol() {
        assertEquals("oss", ossCollectImpl.supportProtocol());
    }

    @Test
    void testPreCheckSuccess() {
        ossCollectImpl.preCheck(metrics);
    }

    @Test
    void testPreCheckNullMetrics() {
        assertThrows(IllegalArgumentException.class, () -> ossCollectImpl.preCheck(null));
    }

    @Test
    void testPreCheckNullOss() {
        metrics.setOss(null);
        assertThrows(IllegalArgumentException.class, () -> ossCollectImpl.preCheck(metrics));
    }

    @Test
    void testPreCheckMissingEndpoint() {
        ossProtocol.setEndpoint("");
        assertThrows(IllegalArgumentException.class, () -> ossCollectImpl.preCheck(metrics));
    }

    @Test
    void testPreCheckMissingBucket() {
        ossProtocol.setBucket("");
        assertThrows(IllegalArgumentException.class, () -> ossCollectImpl.preCheck(metrics));
    }

    @Test
    void testPreCheckMissingAccessKey() {
        ossProtocol.setAccessKey("");
        assertThrows(IllegalArgumentException.class, () -> ossCollectImpl.preCheck(metrics));
    }

    @Test
    void testPreCheckMissingSecretKey() {
        ossProtocol.setSecretKey("");
        assertThrows(IllegalArgumentException.class, () -> ossCollectImpl.preCheck(metrics));
    }

    @Test
    void testPreCheckMissingObjectKeyForHeadObject() {
        ossProtocol.setOperation("headObject");
        ossProtocol.setObjectKey("");
        assertThrows(IllegalArgumentException.class, () -> ossCollectImpl.preCheck(metrics));
    }

    @Test
    void testPreCheckMissingObjectKeyForListObjects() {
        ossProtocol.setOperation("listObjects");
        ossProtocol.setObjectKey("");
        assertThrows(IllegalArgumentException.class, () -> ossCollectImpl.preCheck(metrics));
    }

    @Test
    void testPreCheckInvalidTimezone() {
        ossProtocol.setTimezone("Invalid/Timezone");
        assertThrows(IllegalArgumentException.class, () -> ossCollectImpl.preCheck(metrics));
    }

    @Test
    void testPreCheckMissingAliasFields() {
        metrics.setAliasFields(null);
        assertThrows(IllegalArgumentException.class, () -> ossCollectImpl.preCheck(metrics));
    }

    @Test
    void testResolveDateVariablesNoVars() {
        String key = "data/file.txt";
        assertEquals(key, ossCollectImpl.resolveDateVariables(key, null));
    }

    @Test
    void testResolveDateVariablesWithVars() {
        String template = "data/{yyyy}/{MM}/{dd}/report.csv";
        String result = ossCollectImpl.resolveDateVariables(template, null);
        assertTrue(result.matches("data/\\d{4}/\\d{2}/\\d{2}/report.csv"));
        assertTrue(!result.contains("{"));
    }

    @Test
    void testResolveDateVariablesWithTimezone() {
        String template = "data/{yyyy}/{MM}/{dd}/{HH}.csv";
        String result = ossCollectImpl.resolveDateVariables(template, "Asia/Shanghai");
        assertTrue(result.matches("data/\\d{4}/\\d{2}/\\d{2}/\\d{2}\\.csv"));
    }

    @Test
    void testResolveDateVariablesAllVars() {
        String template = "{yyyy}-{MM}-{dd}_{HH}-{mm}-{ss}";
        String result = ossCollectImpl.resolveDateVariables(template, "UTC");
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}"));
    }

    @Test
    void testResolveDateVariablesNullKey() {
        assertEquals(null, ossCollectImpl.resolveDateVariables(null, null));
    }

    @Test
    void testGetOperationExplicitHeadObject() {
        ossProtocol.setOperation("headObject");
        assertEquals("headObject", ossCollectImpl.getOperation(ossProtocol));
    }

    @Test
    void testGetOperationExplicitListObjects() {
        ossProtocol.setOperation("listObjects");
        assertEquals("listObjects", ossCollectImpl.getOperation(ossProtocol));
    }

    @Test
    void testGetOperationExplicitHeadBucket() {
        ossProtocol.setOperation("headBucket");
        assertEquals("headBucket", ossCollectImpl.getOperation(ossProtocol));
    }

    @Test
    void testGetOperationExplicitGetBucketStorageInfo() {
        ossProtocol.setOperation("getBucketStorageInfo");
        assertEquals("getBucketStorageInfo", ossCollectImpl.getOperation(ossProtocol));
    }

    @Test
    void testGetOperationDefaultHeadObject() {
        ossProtocol.setOperation(null);
        ossProtocol.setObjectKey("data/report.csv");
        assertEquals("headObject", ossCollectImpl.getOperation(ossProtocol));
    }

    @Test
    void testGetOperationDefaultListObjects() {
        ossProtocol.setOperation(null);
        ossProtocol.setObjectKey("data/logs/");
        assertEquals("listObjects", ossCollectImpl.getOperation(ossProtocol));
    }

    @Test
    void testParseTimeoutValid() {
        assertEquals(5000L, ossCollectImpl.parseTimeout("5000"));
    }

    @Test
    void testParseTimeoutInvalid() {
        long defaultTimeout = ossCollectImpl.getDefaultTimeoutMs();
        assertEquals(defaultTimeout, ossCollectImpl.parseTimeout("invalid"));
    }

    @Test
    void testParseTimeoutEmpty() {
        long defaultTimeout = ossCollectImpl.getDefaultTimeoutMs();
        assertEquals(defaultTimeout, ossCollectImpl.parseTimeout(""));
        assertEquals(defaultTimeout, ossCollectImpl.parseTimeout(null));
    }

    @Test
    void testGetMaxListKeys() {
        assertEquals(1000, ossCollectImpl.getMaxListKeys());
    }
}
