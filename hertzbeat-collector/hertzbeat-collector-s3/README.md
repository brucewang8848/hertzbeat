# HertzBeat S3 兼容对象存储采集器使用文档

## 一、功能概述

S3 采集器为 Apache HertzBeat 新增了 S3 兼容对象存储的监控能力，支持：

- **Amazon S3**
- **MinIO**
- **华为 OBS**
- **阿里云 OSS**
- **腾讯云 COS**
- 其他 S3 兼容的对象存储服务

### 监控模式

| 模式 | 判断条件 | 采集指标 |
|------|---------|---------|
| **文件监控** | objectKey 不以 `/` 结尾 | objectKey、exists、fileSize、lastModified、responseTime |
| **目录监控** | objectKey 以 `/` 结尾 | prefix、fileCount、latestFile、latestModified、totalSize、responseTime |

### 日期变量支持

objectKey 支持原子级日期变量动态解析：

| 变量 | 说明 | 示例值 |
|------|------|--------|
| `{yyyy}` | 四位年份 | 2026 |
| `{yy}` | 两位年份 | 26 |
| `{MM}` | 两位月份 | 05 |
| `{dd}` | 两位日期 | 11 |
| `{HH}` | 两位小时（24小时制） | 14 |
| `{mm}` | 两位分钟 | 30 |
| `{ss}` | 两位秒数 | 45 |

**示例**：`data/{yyyy}/{MM}/{dd}/report.csv` → `data/2026/05/11/report.csv`

---

## 二、变更文件清单

### 2.1 hertzbeat-collector-s3（新增模块）

| 文件路径 | 说明 |
|---------|------|
| `pom.xml` | Maven 模块配置，引入 AWS SDK S3 依赖 |
| `src/main/java/org/apache/hertzbeat/collector/collect/s3/S3CollectImpl.java` | 核心采集器实现（315 行） |
| `src/main/resources/META-INF/services/org.apache.hertzbeat.collector.collect.AbstractCollect` | SPI 服务注册文件（**必须**，否则启动时报 `ServiceConfigurationError`）|
| `src/test/java/org/apache/hertzbeat/collector/collect/s3/S3CollectImplTest.java` | 单元测试类 |

### 2.2 hertzbeat-collector（父模块）

| 文件路径 | 变更内容 |
|---------|---------|
| `pom.xml` | 新增 `<module>hertzbeat-collector-s3</module>` |

### 2.3 hertzbeat-collector-common（采集器公共模块）

| 文件路径 | 变更内容 |
|---------|---------|
| `src/main/java/org/apache/hertzbeat/collector/dispatch/DispatchConstants.java` | 新增 `PROTOCOL_S3 = "s3"` 常量 |

### 2.4 hertzbeat-common（公共模块）

| 文件路径 | 变更内容 |
|---------|---------|
| `src/main/java/org/apache/hertzbeat/common/entity/job/protocol/S3Protocol.java` | 新增 S3 协议配置实体类 |
| `src/main/java/org/apache/hertzbeat/common/entity/job/Metrics.java` | 新增 `S3Protocol s3` 字段 |

---

## 三、配置参数说明

### 3.1 S3Protocol 配置项

| 参数 | 必填 | 说明 | 示例 |
|------|------|------|------|
| `endpoint` | ✅ | S3 服务端点 URL | `https://s3.cn-north-1.amazonaws.com.cn` |
| `accessKey` | ✅ | Access Key ID | `AKIAIOSFODNN7EXAMPLE` |
| `secretKey` | ✅ | Secret Access Key | `wJalrXUtnFEMI/K7MDENG/...` |
| `bucket` | ✅ | Bucket 名称 | `my-bucket` |
| `objectKey` | ✅ | 对象键（支持日期变量） | `data/{yyyy}/{MM}/{dd}/report.csv` |
| `region` | ❌ | 区域标识 | `cn-north-1` |
| `pathStyle` | ❌ | 是否使用路径风格访问 | `true` / `false`（默认 false） |
| `timezone` | ❌ | 日期变量解析时区 | `Asia/Shanghai`（默认 JVM 时区） |
| `timeout` | ❌ | 超时时间（毫秒） | `30000`（默认 30000） |

### 3.2 pathStyle 参数说明

| 值 | 访问风格 | URL 示例 | 适用场景 |
|---|---------|---------|---------|
| `false`（默认） | 虚拟主机风格 | `https://bucket.s3.region.amazonaws.com/key` | Amazon S3 |
| `true` | 路径风格 | `https://s3.region.amazonaws.com/bucket/key` | MinIO、华为 OBS、阿里 OSS |

---

## 四、监控模板配置示例

### 4.1 文件监控模板

监控单个文件是否存在，并获取文件大小和最后修改时间：

```yaml
# 文件监控 - S3 协议
name: s3-file
protocol: s3
priority: 1

metrics:
  - name: file_status
    priority: 0
    fields:
      - field: objectKey
        type: string
      - field: exists
        type: boolean
      - field: fileSize
        type: long
      - field: lastModified
        type: string
      - field: responseTime
        type: long
    aliasFields:
      - objectKey
      - exists
      - fileSize
      - lastModified
      - responseTime
    s3:
      endpoint: https://s3.cn-north-1.amazonaws.com.cn
      region: cn-north-1
      accessKey: ${ACCESS_KEY}
      secretKey: ${SECRET_KEY}
      bucket: my-bucket
      objectKey: data/reports/daily.csv
      timeout: "30000"
```

### 4.2 目录监控模板

监控目录下的文件数量、总大小、最新文件等：

```yaml
# 目录监控 - S3 协议
name: s3-directory
protocol: s3
priority: 1

metrics:
  - name: directory_status
    priority: 0
    fields:
      - field: prefix
        type: string
      - field: fileCount
        type: integer
      - field: latestFile
        type: string
      - field: latestModified
        type: string
      - field: totalSize
        type: long
      - field: responseTime
        type: long
    aliasFields:
      - prefix
      - fileCount
      - latestFile
      - latestModified
      - totalSize
      - responseTime
    s3:
      endpoint: https://s3.cn-north-1.amazonaws.com.cn
      region: cn-north-1
      accessKey: ${ACCESS_KEY}
      secretKey: ${SECRET_KEY}
      bucket: my-bucket
      objectKey: data/logs/{yyyy}/{MM}/{dd}/
      timezone: Asia/Shanghai
      timeout: "30000"
```

### 4.3 日期变量监控模板

监控按日期归档的文件：

```yaml
# 日期变量文件监控
name: s3-daily-report
protocol: s3
priority: 1

metrics:
  - name: daily_report
    priority: 0
    fields:
      - field: objectKey
        type: string
      - field: exists
        type: boolean
      - field: fileSize
        type: long
      - field: lastModified
        type: string
      - field: responseTime
        type: long
    aliasFields:
      - objectKey
      - exists
      - fileSize
      - lastModified
      - responseTime
    s3:
      endpoint: https://s3.cn-north-1.amazonaws.com.cn
      region: cn-north-1
      accessKey: ${ACCESS_KEY}
      secretKey: ${SECRET_KEY}
      bucket: reports-bucket
      objectKey: daily/{yyyy}/{MM}/{dd}/report.csv
      timezone: Asia/Shanghai
      timeout: "30000"
```

---

## 五、各对象存储配置示例

### 5.1 Amazon S3（中国区）

```yaml
s3:
  endpoint: https://s3.cn-north-1.amazonaws.com.cn
  region: cn-north-1
  accessKey: AKIAIOSFODNN7EXAMPLE
  secretKey: wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
  bucket: my-china-bucket
  objectKey: data/file.txt
  pathStyle: "false"
  timeout: "30000"
```

### 5.2 Amazon S3（国际区）

```yaml
s3:
  endpoint: https://s3.us-west-2.amazonaws.com
  region: us-west-2
  accessKey: AKIAIOSFODNN7EXAMPLE
  secretKey: wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
  bucket: my-global-bucket
  objectKey: data/file.txt
  pathStyle: "false"
  timeout: "30000"
```

### 5.3 MinIO

```yaml
s3:
  endpoint: http://localhost:9000
  region: us-east-1
  accessKey: minioadmin
  secretKey: minioadmin
  bucket: my-minio-bucket
  objectKey: data/file.txt
  pathStyle: "true"
  timeout: "30000"
```

### 5.4 华为 OBS

```yaml
s3:
  endpoint: https://obs.cn-north-4.myhuaweicloud.com
  region: cn-north-4
  accessKey: YOUR_HUAWEI_AK
  secretKey: YOUR_HUAWEI_SK
  bucket: my-obs-bucket
  objectKey: data/file.txt
  pathStyle: "true"
  timeout: "30000"
```

### 5.5 阿里云 OSS

```yaml
s3:
  endpoint: https://oss-cn-hangzhou.aliyuncs.com
  region: cn-hangzhou
  accessKey: YOUR_ALIYUN_ACCESS_KEY_ID
  secretKey: YOUR_ALIYUN_ACCESS_KEY_SECRET
  bucket: my-oss-bucket
  objectKey: data/file.txt
  pathStyle: "true"
  timeout: "30000"
```

### 5.6 腾讯云 COS

```yaml
s3:
  endpoint: https://cos.ap-guangzhou.myqcloud.com
  region: ap-guangzhou
  accessKey: YOUR_TENCENT_SECRET_ID
  secretKey: YOUR_TENCENT_SECRET_KEY
  bucket: my-cos-bucket-1234567890
  objectKey: data/file.txt
  pathStyle: "true"
  timeout: "30000"
```

---

## 六、告警规则示例

### 6.1 文件不存在告警

```yaml
alert:
  name: s3-file-missing
  expr: s3_file_exists == 0
  for: 1m
  severity: critical
  message: "S3 文件 {{ $labels.objectKey }} 不存在"
```

### 6.2 目录文件数量异常告警

```yaml
alert:
  name: s3-directory-empty
  expr: s3_directory_fileCount == 0
  for: 5m
  severity: warning
  message: "S3 目录 {{ $labels.prefix }} 为空，可能数据生成异常"
```

### 6.3 响应时间告警

```yaml
alert:
  name: s3-response-slow
  expr: s3_responseTime > 5000
  for: 3m
  severity: warning
  message: "S3 访问响应时间过长: {{ $value }}ms"
```

---

## 七、注意事项

1. **权限要求**：AccessKey 需要具备目标 Bucket 的 `s3:HeadObject`（文件监控）或 `s3:ListBucket`（目录监控）权限。

2. **目录监控限制**：单次最多返回 1000 个对象，适用于文件数量可控的场景。如需监控大量文件，建议使用更细粒度的前缀。

3. **时区设置**：使用日期变量时，建议明确指定 `timezone` 参数，避免因 JVM 时区不一致导致解析错误。

4. **网络连通性**：确保 HertzBeat 服务能够访问 S3 端点，必要时配置代理或防火墙规则。

5. **凭证安全**：建议使用 `${ENV_VAR}` 形式引用环境变量，避免在配置文件中硬编码 AccessKey 和 SecretKey。

---

## 八、故障排查

| 问题 | 可能原因 | 解决方案 |
|------|---------|---------|
| 连接超时 | 网络不通或 endpoint 错误 | 检查网络连通性，验证 endpoint URL |
| 认证失败 | AccessKey/SecretKey 错误 | 验证凭证是否正确，检查是否过期 |
| 404 Not Found | Bucket 或 Object 不存在 | 确认 Bucket 名称和 ObjectKey 是否正确 |
| 日期变量未解析 | 变量格式错误 | 确保使用 `{yyyy}` 格式，区分大小写 |
| MinIO 连接失败 | pathStyle 未设置 | 设置 `pathStyle: "true"` |
| `ServiceConfigurationError: Provider S3CollectImpl not found` | SPI 配置文件缺失 | 确保 `hertzbeat-collector-s3.jar` 包含 `META-INF/services/org.apache.hertzbeat.collector.collect.AbstractCollect` 文件 |

---

## 九、版本信息

- **HertzBeat 版本**：2.0-SNAPSHOT
- **AWS SDK S3 版本**：2.44.4
- **最低 Java 版本**：17
