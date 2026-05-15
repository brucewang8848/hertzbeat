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
import org.apache.hertzbeat.common.entity.job.protocol.ObsProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ObsCollectImpl unit test class
 */
@ExtendWith(MockitoExtension.class)
class ObsCollectImplTest {

    @InjectMocks
    private ObsCollectImpl obsCollectImpl;

    private Metrics metrics;
    private ObsProtocol obsProtocol;
    private ArrayList<String> aliasFields;

    @BeforeEach
    void setUp() {
        obsProtocol = ObsProtocol.builder()
                .endpoint("https://obs.cn-north-4.myhuaweicloud.com")
                .accessKey("test-access-key")
                .secretKey("test-secret-key")
                .bucket("test-bucket")
                .objectKey("data/report.csv")
                .timeout("30000")
                .build();

        aliasFields = new ArrayList<>();
        aliasFields.add("objectKey");
        aliasFields.add("exists");
        aliasFields.add("fileSize");
        aliasFields.add("lastModified");
        aliasFields.add("responseTime");

        metrics = new Metrics();
        metrics.setName("obs_file");
        metrics.setObs(obsProtocol);
        metrics.setAliasFields(aliasFields);
    }

    @Test
    void testSupportProtocol() {
        assertEquals("obs", obsCollectImpl.supportProtocol());
    }

    @Test
    void testPreCheckSuccess() {
        obsCollectImpl.preCheck(metrics);
    }

    @Test
    void testPreCheckNullMetrics() {
        assertThrows(IllegalArgumentException.class, () -> obsCollectImpl.preCheck(null));
    }

    @Test
    void testPreCheckNullObs() {
        metrics.setObs(null);
        assertThrows(IllegalArgumentException.class, () -> obsCollectImpl.preCheck(metrics));
    }

    @Test
    void testPreCheckMissingEndpoint() {
        obsProtocol.setEndpoint("");
        assertThrows(IllegalArgumentException.class, () -> obsCollectImpl.preCheck(metrics));
    }

    @Test
    void testPreCheckMissingBucket() {
        obsProtocol.setBucket("");
        assertThrows(IllegalArgumentException.class, () -> obsCollectImpl.preCheck(metrics));
    }

    @Test
    void testPreCheckMissingAccessKey() {
        obsProtocol.setAccessKey("");
        assertThrows(IllegalArgumentException.class, () -> obsCollectImpl.preCheck(metrics));
    }

    @Test
    void testPreCheckMissingSecretKey() {
        obsProtocol.setSecretKey("");
        assertThrows(IllegalArgumentException.class, () -> obsCollectImpl.preCheck(metrics));
    }

    @Test
    void testPreCheckMissingObjectKeyForHeadObject() {
        obsProtocol.setOperation("headObject");
        obsProtocol.setObjectKey("");
        assertThrows(IllegalArgumentException.class, () -> obsCollectImpl.preCheck(metrics));
    }

    @Test
    void testPreCheckMissingObjectKeyForListObjects() {
        obsProtocol.setOperation("listObjects");
        obsProtocol.setObjectKey("");
        assertThrows(IllegalArgumentException.class, () -> obsCollectImpl.preCheck(metrics));
    }

    @Test
    void testPreCheckInvalidTimezone() {
        obsProtocol.setTimezone("Invalid/Timezone");
        assertThrows(IllegalArgumentException.class, () -> obsCollectImpl.preCheck(metrics));
    }

    @Test
    void testPreCheckMissingAliasFields() {
        metrics.setAliasFields(null);
        assertThrows(IllegalArgumentException.class, () -> obsCollectImpl.preCheck(metrics));
    }

    @Test
    void testResolveDateVariablesNoVars() {
        String key = "data/file.txt";
        assertEquals(key, obsCollectImpl.resolveDateVariables(key, null));
    }

    @Test
    void testResolveDateVariablesWithVars() {
        String template = "data/{yyyy}/{MM}/{dd}/report.csv";
        String result = obsCollectImpl.resolveDateVariables(template, null);
        assertTrue(result.matches("data/\\d{4}/\\d{2}/\\d{2}/report.csv"));
        assertTrue(!result.contains("{"));
    }

    @Test
    void testResolveDateVariablesWithTimezone() {
        String template = "data/{yyyy}/{MM}/{dd}/{HH}.csv";
        String result = obsCollectImpl.resolveDateVariables(template, "Asia/Shanghai");
        assertTrue(result.matches("data/\\d{4}/\\d{2}/\\d{2}/\\d{2}\\.csv"));
    }

    @Test
    void testResolveDateVariablesAllVars() {
        String template = "{yyyy}-{MM}-{dd}_{HH}-{mm}-{ss}";
        String result = obsCollectImpl.resolveDateVariables(template, "UTC");
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}"));
    }

    @Test
    void testResolveDateVariablesNullKey() {
        assertEquals(null, obsCollectImpl.resolveDateVariables(null, null));
    }

    @Test
    void testGetOperationExplicitHeadObject() {
        obsProtocol.setOperation("headObject");
        assertEquals("headObject", obsCollectImpl.getOperation(obsProtocol));
    }

    @Test
    void testGetOperationExplicitListObjects() {
        obsProtocol.setOperation("listObjects");
        assertEquals("listObjects", obsCollectImpl.getOperation(obsProtocol));
    }

    @Test
    void testGetOperationExplicitHeadBucket() {
        obsProtocol.setOperation("headBucket");
        assertEquals("headBucket", obsCollectImpl.getOperation(obsProtocol));
    }

    @Test
    void testGetOperationExplicitGetBucketStorageInfo() {
        obsProtocol.setOperation("getBucketStorageInfo");
        assertEquals("getBucketStorageInfo", obsCollectImpl.getOperation(obsProtocol));
    }

    @Test
    void testGetOperationDefaultHeadObject() {
        obsProtocol.setOperation(null);
        obsProtocol.setObjectKey("data/report.csv");
        assertEquals("headObject", obsCollectImpl.getOperation(obsProtocol));
    }

    @Test
    void testGetOperationDefaultListObjects() {
        obsProtocol.setOperation(null);
        obsProtocol.setObjectKey("data/logs/");
        assertEquals("listObjects", obsCollectImpl.getOperation(obsProtocol));
    }

    @Test
    void testParseTimeoutValid() {
        assertEquals(5000L, obsCollectImpl.parseTimeout("5000"));
    }

    @Test
    void testParseTimeoutInvalid() {
        long defaultTimeout = obsCollectImpl.getDefaultTimeoutMs();
        assertEquals(defaultTimeout, obsCollectImpl.parseTimeout("invalid"));
    }

    @Test
    void testParseTimeoutEmpty() {
        long defaultTimeout = obsCollectImpl.getDefaultTimeoutMs();
        assertEquals(defaultTimeout, obsCollectImpl.parseTimeout(""));
        assertEquals(defaultTimeout, obsCollectImpl.parseTimeout(null));
    }

    @Test
    void testGetMaxListKeys() {
        assertEquals(1000, obsCollectImpl.getMaxListKeys());
    }
}
