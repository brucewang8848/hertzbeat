# 华为云 OBS 监控配置指南

本文档介绍如何在 HertzBeat 中配置华为云 OBS (Object Storage Service) 监控。

---

## 前置条件

1. 已安装并运行 HertzBeat
2. 拥有华为云账号，并创建了 OBS Bucket
3. 获取了有效的 Access Key 和 Secret Key

---

## 获取华为云 Access Key

1. 登录 [华为云控制台](https://console.huaweicloud.com/)
2. 进入 **我的凭证** → **访问密钥**
3. 点击 **新建访问密钥**，下载 Access Key 信息

---

## 创建 OBS 监控

### 1. 添加监控

在 HertzBeat Web UI 中：
1. 点击 **新增监控**
2. 选择监控类型：**华为云 OBS 监控**

### 2. 配置监控参数

| 参数名称 | 是否必填 | 说明 | 示例 |
|:---|:---:|:---|:---|
| 服务端点 | 是 | OBS 服务端点地址 | `https://obs.cn-north-4.myhuaweicloud.com` |
| Access Key | 是 | 访问密钥 ID | - |
| Secret Key | 是 | 访问密钥密码 | - |
| Bucket名称 | 是 | 要监控的存储桶名称 | `my-bucket` |
| 对象键 | 否 | 要检测的文件路径，支持日期变量 | `data/{yyyy}/{MM}/{dd}/report.csv` |
| 时区 | 否 | 日期变量解析时区，默认为 JVM 时区 | `Asia/Shanghai` |
| 超时时间(ms) | 是 | 请求超时时间，默认 30000ms | `30000` |

### 3. 服务端点参考

常用华为云 OBS 服务端点：

| 区域 | 服务端点 |
|:---|:---|
| 华北-北京一 | `https://obs.cn-north-1.myhuaweicloud.com` |
| 华北-北京四 | `https://obs.cn-north-4.myhuaweicloud.com` |
| 华东-上海一 | `https://obs.cn-east-3.myhuaweicloud.com` |
| 华东-广州 | `https://obs.cn-south-1.myhuaweicloud.com` |
| 亚太-香港 | `https://obs.ap-southeast-1.myhuaweicloud.com` |

---

## 监控指标说明

HertzBeat 提供以下 4 个监控指标组：

### 1. 文件检测 (fileCheck)

检测指定对象键对应的文件是否存在，并获取文件元信息。

| 指标 | 类型 | 说明 |
|:---|:---:|:---|
| objectKey | 字符串 | 对象键（文件路径） |
| exists | 字符串 | 文件是否存在：`true` / `false` |
| fileSize | 数值 | 文件大小（单位：字节） |
| lastModified | 字符串 | 最后修改时间 |
| responseTime | 数值 | 响应时间（单位：毫秒） |

### 2. 目录统计 (dirStats)

统计指定前缀下的所有文件数量、总大小和最新文件信息。

**注意**：对象键必须以 `/` 结尾才会触发目录统计模式。

| 指标 | 类型 | 说明 |
|:---|:---:|:---|
| prefix | 字符串 | 统计前缀（目录路径） |
| fileCount | 数值 | 文件数量 |
| totalSize | 数值 | 总大小（单位：字节） |
| latestFile | 字符串 | 最新文件的路径 |
| latestModified | 字符串 | 最新文件的修改时间 |
| responseTime | 数值 | 响应时间（单位：毫秒） |

### 3. Bucket健康 (bucketHealth)

检查 Bucket 是否存在且可访问。

| 指标 | 类型 | 说明 |
|:---|:---:|:---|
| bucket | 字符串 | Bucket 名称 |
| exists | 字符串 | Bucket 是否存在：`true` / `false` |
| accessible | 字符串 | Bucket 是否可访问：`true` / `false` |
| responseTime | 数值 | 响应时间（单位：毫秒） |

### 4. Bucket存储信息 (bucketStorageInfo)

获取 Bucket 的存储量统计信息（OBS 特有功能）。

| 指标 | 类型 | 说明 |
|:---|:---:|:---|
| bucket | 字符串 | Bucket 名称 |
| bucketSize | 数值 | Bucket 总大小（单位：字节） |
| objectCount | 数值 | 对象数量 |
| responseTime | 数值 | 响应时间（单位：毫秒） |

---

## 日期变量使用

对象键支持原子级日期变量，可用于监控每日/每小时生成的文件：

| 变量 | 说明 | 示例值 |
|:---|:---|:---|
| `{yyyy}` | 4位年份 | `2026` |
| `{yy}` | 2位年份 | `26` |
| `{MM}` | 2位月份 | `05` |
| `{dd}` | 2位日期 | `15` |
| `{HH}` | 2位小时 | `14` |
| `{mm}` | 2位分钟 | `30` |
| `{ss}` | 2位秒数 | `45` |

### 使用示例

| 场景 | 对象键配置 |
|:---|:---|
| 每日报告文件 | `reports/{yyyy}/{MM}/{dd}/daily.csv` |
| 每小时日志文件 | `logs/{yyyy}/{MM}/{dd}/{HH}/access.log` |
| 固定文件（不推荐） | `data/latest.json` |

### 时区配置

如果日志生成时间与服务器时区不一致，可以通过 **时区** 参数指定：

- 设为 `Asia/Shanghai` 表示中国时区
- 设为 `UTC` 表示国际标准时区

---

## 告警规则配置

### 常用告警规则示例

#### 1. 文件不存在告警

```
指标: exists = false
告警级别: 严重
```

#### 2. 文件过大告警

```
指标: fileSize > 1073741824  (大于 1GB)
告警级别: 警告
```

#### 3. Bucket 不可访问告警

```
指标: accessible = false
告警级别: 严重
```

#### 4. 存储量超阈值告警

```
指标: bucketSize > 10737418240  (大于 10GB)
告警级别: 警告
```

---

## 通过模板配置监控

除了通过 Web UI 配置监控外，还可以通过 YAML 模板文件进行配置。模板文件通常位于 HertzBeat 安装目录的 `./define/app-obs.yml`。

### fileCheck 模板配置

用于检测指定文件是否存在及获取文件元信息：

```yaml
# 指标名称
- name: fileCheck
  # 优先级（数字越小优先级越高）
  priority: 0
  # 字段定义
  fields:
    - field: objectKey
      type: 1          # 1=字符串类型, 0=数值类型
      i18n:
        zh-CN: 对象键
        en-US: Object Key
    - field: exists
      type: 1
      i18n:
        zh-CN: 文件存在
        en-US: File Exists
    - field: fileSize
      type: 0
      unit: B          # 单位：字节
      i18n:
        zh-CN: 文件大小
        en-US: File Size
    - field: lastModified
      type: 1
      i18n:
        zh-CN: 最后修改时间
        en-US: Last Modified
    - field: responseTime
      type: 0
      unit: ms
      i18n:
        zh-CN: 响应时间
        en-US: Response Time
  # 别名字段（与上方 fields 对应）
  aliasFields:
    - objectKey
    - exists
    - fileSize
    - lastModified
    - responseTime
  # 协议类型
  protocol: obs
  obs:
    # 操作类型：headObject = 获取文件元信息
    operation: headObject
    # 具体配置参数
    endpoint: ^_^endpoint^_^
    accessKey: ^_^accessKey^_^
    secretKey: ^_^secretKey^_^
    bucket: ^_^bucket^_^
    objectKey: ^_^objectKey^_^
    timezone: ^_^timezone^_^
    timeout: ^_^timeout^_^
```

### dirStats 模板配置

用于统计目录下所有文件的数量、总大小等信息：

```yaml
# 指标名称
- name: dirStats
  priority: 1
  fields:
    - field: prefix
      type: 1
      i18n:
        zh-CN: 前缀
        en-US: Prefix
    - field: fileCount
      type: 0
      i18n:
        zh-CN: 文件数量
        en-US: File Count
    - field: totalSize
      type: 0
      unit: B
      i18n:
        zh-CN: 总大小
        en-US: Total Size
    - field: latestFile
      type: 1
      i18n:
        zh-CN: 最新文件
        en-US: Latest File
    - field: latestModified
      type: 1
      i18n:
        zh-CN: 最新修改时间
        en-US: Latest Modified
    - field: responseTime
      type: 0
      unit: ms
      i18n:
        zh-CN: 响应时间
        en-US: Response Time
  aliasFields:
    - prefix
    - fileCount
    - totalSize
    - latestFile
    - latestModified
    - responseTime
  protocol: obs
  obs:
    # 操作类型：listObjects = 列出目录下的对象
    operation: listObjects
    endpoint: ^_^endpoint^_^
    accessKey: ^_^accessKey^_^
    secretKey: ^_^secretKey^_^
    bucket: ^_^bucket^_^
    # 关键：目录统计要求 objectKey 以 / 结尾
    objectKey: ^_^objectKey^_^
    timezone: ^_^timezone^_^
    timeout: ^_^timeout^_^
```

### 完整模板示例

```yaml
# 监控类型标识
app: obs
# 监控类型名称
name:
  zh-CN: 华为云 OBS 监控
  en-US: Huawei OBS Monitor

# 监控参数定义
params:
  - field: endpoint
    name:
      zh-CN: 服务端点
      en-US: Endpoint
    type: text
    required: true
    placeholder: 'https://obs.cn-north-4.myhuaweicloud.com'
  - field: accessKey
    name:
      zh-CN: Access Key
      en-US: Access Key
    type: text
    required: true
  - field: secretKey
    name:
      zh-CN: Secret Key
      en-US: Secret Key
    type: password
    required: true
  - field: bucket
    name:
      zh-CN: Bucket名称
      en-US: Bucket Name
    type: text
    required: true
  - field: objectKey
    name:
      zh-CN: 对象键
      en-US: Object Key
    type: text
    required: false
    placeholder: 'logs/'
  - field: timezone
    name:
      zh-CN: 时区
      en-US: Timezone
    type: text
    required: false
    placeholder: 'Asia/Shanghai'
  - field: timeout
    name:
      zh-CN: 超时时间(ms)
      en-US: Timeout(ms)
    type: number
    required: true
    defaultValue: 30000

# 监控指标列表
metrics:
  # fileCheck 示例：检测每日报告文件
  - name: dailyReportCheck
    priority: 0
    fields:
      - field: objectKey
        type: 1
        i18n:
          zh-CN: 对象键
          en-US: Object Key
      - field: exists
        type: 1
        i18n:
          zh-CN: 文件存在
          en-US: File Exists
      - field: fileSize
        type: 0
        unit: B
        i18n:
          zh-CN: 文件大小
          en-US: File Size
      - field: lastModified
        type: 1
        i18n:
          zh-CN: 最后修改时间
          en-US: Last Modified
      - field: responseTime
        type: 0
        unit: ms
        i18n:
          zh-CN: 响应时间
          en-US: Response Time
    aliasFields:
      - objectKey
      - exists
      - fileSize
      - lastModified
      - responseTime
    protocol: obs
    obs:
      operation: headObject
      # 使用日期变量监控每日报告
      objectKey: reports/{yyyy}/{MM}/{dd}/daily.csv
      timezone: Asia/Shanghai
      # 以下参数会从监控配置的表单参数中自动替换
      endpoint: ^_^endpoint^_^
      accessKey: ^_^accessKey^_^
      secretKey: ^_^secretKey^_^
      bucket: ^_^bucket^_^
      timeout: ^_^timeout^_^

  # dirStats 示例：统计每日日志
  - name: dailyLogsStats
    priority: 1
    fields:
      - field: prefix
        type: 1
        i18n:
          zh-CN: 前缀
          en-US: Prefix
      - field: fileCount
        type: 0
        i18n:
          zh-CN: 文件数量
          en-US: File Count
      - field: totalSize
        type: 0
        unit: B
        i18n:
          zh-CN: 总大小
          en-US: Total Size
      - field: latestFile
        type: 1
        i18n:
          zh-CN: 最新文件
          en-US: Latest File
      - field: latestModified
        type: 1
        i18n:
          zh-CN: 最新修改时间
          en-US: Latest Modified
      - field: responseTime
        type: 0
        unit: ms
        i18n:
          zh-CN: 响应时间
          en-US: Response Time
    aliasFields:
      - prefix
      - fileCount
      - totalSize
      - latestFile
      - latestModified
      - responseTime
    protocol: obs
    obs:
      operation: listObjects
      # 注意：目录统计要求以 / 结尾
      objectKey: logs/{yyyy}/{MM}/{dd}/
      timezone: Asia/Shanghai
      endpoint: ^_^endpoint^_^
      accessKey: ^_^accessKey^_^
      secretKey: ^_^secretKey^_^
      bucket: ^_^bucket^_^
      timeout: ^_^timeout^_^
```

### 模板配置说明

| 配置项 | 说明 |
|:---|:---|
| `name` | 指标名称，用于区分不同的监控指标 |
| `priority` | 优先级，数字越小采集顺序越靠前 |
| `fields` | 定义返回的字段列表 |
| `field.type` | 字段类型：`1`=字符串，`0`=数值 |
| `field.unit` | 数值字段的单位（可选） |
| `aliasFields` | 别名字段列表，需与 fields 顺序对应 |
| `protocol` | 协议类型，此处固定为 `obs` |
| `obs.operation` | 操作类型：`headObject`=文件检测，`listObjects`=目录统计 |
| `^_^xxx^_^` | 变量占位符，会自动替换为监控配置表单中的值 |

---

## 常见问题

### Q1: 提示 "Access Key 无效"？

请确认 Access Key 和 Secret Key 是否正确，确保密钥未被禁用。

### Q2: 提示 "Bucket 不存在"？

请确认 Bucket 名称拼写正确，且与 Access Key 所属账号的 Bucket 名称一致。

### Q3: 对象键不包含日期变量，提示文件不存在？

这是正常的。如果指定的文件不存在，`exists` 会返回 `false`。如果需要监控动态生成的文件，请使用日期变量。

### Q4: 目录统计没有返回结果？

请确保对象键以 `/` 结尾，例如 `logs/`。不包含 `/` 会被当作单个文件处理。

---

## 相关资源

- [HertzBeat 官方文档](https://hertzbeat.apache.org/zh-cn/docs/)
- [华为云 OBS 官方文档](https://support.huaweicloud.com/obs/)
- [HertzBeat 官方帮助 - OBS](https://hertzbeat.apache.org/zh-cn/docs/help/obs)
