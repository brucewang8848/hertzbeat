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

import java.util.ArrayList;
import java.util.List;

import org.apache.hertzbeat.common.entity.job.Metrics;
import org.apache.hertzbeat.common.entity.job.protocol.S3Protocol;
import org.apache.hertzbeat.common.entity.message.CollectRep;

/**
 * WARNING: This is a temporary manual test file containing real credentials.
 * DO NOT commit this file to Git. Delete it immediately after verification.
 */
public class S3CollectImplManualTest {

    public static void main(String[] args) {
        S3CollectImpl collect = new S3CollectImpl();

        // WARNING: Real credentials below - DO NOT commit this file
        S3Protocol s3Protocol = S3Protocol.builder()
                .endpoint("https://s3.oss-cn-beijing.aliyuncs.com")
                .region("cn-beijing")
                .accessKey("LTAI5t5ruSh4bZYHQpsMzUq8")
                .secretKey("N38bRvQBFH2vA75gvOFdnyLtWyG28u")
                .bucket("bigdata-s34")
                .objectKey("开发项目计划书.docx")
                .pathStyle("false")
                .timeout("30000")
                .build();

        List<String> aliasFields = new ArrayList<>();
        aliasFields.add("objectKey");
        aliasFields.add("exists");
        aliasFields.add("fileSize");
        aliasFields.add("lastModified");
        aliasFields.add("responseTime");

        Metrics metrics = new Metrics();
        metrics.setName("s3_file");
        metrics.setS3(s3Protocol);
        metrics.setAliasFields(aliasFields);

        try {
            collect.preCheck(metrics);
            System.out.println("preCheck passed");
        } catch (IllegalArgumentException e) {
            System.err.println("preCheck failed: " + e.getMessage());
            return;
        }

        CollectRep.MetricsData.Builder builder = CollectRep.MetricsData.newBuilder();
        collect.collect(builder, metrics);

        System.out.println("Code: " + builder.getCode());
        System.out.println("Msg: " + builder.getMsg());
        System.out.println("Values count: " + builder.getValuesCount());

        if (builder.getValuesCount() > 0) {
            CollectRep.ValueRow row = builder.getValuesList().get(0);
            System.out.println("objectKey:    " + row.getColumns(0));
            System.out.println("exists:       " + row.getColumns(1));
            System.out.println("fileSize:     " + row.getColumns(2));
            System.out.println("lastModified: " + row.getColumns(3));
            System.out.println("responseTime: " + row.getColumns(4) + " ms");
        }
    }
}
