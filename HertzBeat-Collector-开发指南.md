# HertzBeat Collector 开发与产品发布指南

本文档介绍如何在 HertzBeat 项目中开发一个新的 Collector 模块，以及如何打包发布产品。

---

## 一、产品发布包构建

### 1.1 前置条件

确保已安装以下工具：
- JDK 17 或更高版本
- Maven 3.8+

### 1.2 构建步骤

#### 第一步：编译整个项目

在 `./hertzbeat` 项目根目录下执行：

```bash
mvn clean install -DskipTests
```

此命令会编译所有模块并将依赖安装到本地 Maven 仓库。

#### 第二步：打包产品

进入 `./hertzbeat/hertzbeat-startup` 目录，执行以下命令之一：

**方式一：发布版本（Release）**
```bash
cd hertzbeat-startup
mvn clean package -DskipTests -Prelease
```

**方式二：运行时版本（Runtime）**
```bash
cd hertzbeat-startup
mvn clean package -DskipTests -Pruntime
```

### 1.3 产物位置

构建完成后，产品包会生成在 `./hertzbeat/dist` 目录下：

```
hertzbeat/dist/
├── apache-hertzbeat-{version}.tar.gz    # Linux/Mac 压缩包
├── apache-hertzbeat-{version}.zip       # Windows 压缩包
└── ...
```

---

## 二、开发新的 Collector

### 2.1 创建子模块

新的 Collector 需要在 `hertzbeat-collector` 模块下创建子模块。

#### 目录结构示例

```
hertzbeat-collector/
├── hertzbeat-collector-common/          # 公共代码
├── hertzbeat-collector-basic/           # 基础采集器
├── hertzbeat-collector-objectstore/     # 对象存储采集器（示例）
└── ...
```

#### 创建步骤

1. **创建模块目录**

   在 `hertzbeat-collector` 下创建新目录，例如 `hertzbeat-collector-mycollector`。

2. **创建 pom.xml**

   参考现有模块创建 `pom.xml`，关键配置：

   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <project xmlns="http://maven.apache.org/POM/4.0.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                                https://maven.apache.org/xsd/maven-4.0.0.xsd">
       <modelVersion>4.0.0</modelVersion>
       <parent>
           <groupId>org.apache.hertzbeat</groupId>
           <artifactId>hertzbeat-collector</artifactId>
           <version>2.0-SNAPSHOT</version>
       </parent>

       <artifactId>hertzbeat-collector-mycollector</artifactId>
       <name>${project.artifactId}</name>

       <properties>
           <maven.compiler.source>17</maven.compiler.source>
           <maven.compiler.target>17</maven.compiler.target>
           <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
       </properties>

       <dependencies>
           <!-- 依赖 collector-common -->
           <dependency>
               <groupId>org.apache.hertzbeat</groupId>
               <artifactId>hertzbeat-collector-common</artifactId>
               <scope>provided</scope>
           </dependency>

           <!-- 第三方 SDK 依赖 -->
           <dependency>
               <groupId>com.example</groupId>
               <artifactId>some-sdk</artifactId>
               <version>1.0.0</version>
           </dependency>

           <!-- 测试依赖 -->
           <dependency>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-starter-test</artifactId>
               <scope>test</scope>
           </dependency>
       </dependencies>
   </project>
   ```

3. **在父 pom.xml 中添加模块**

   编辑 `hertzbeat-collector/pom.xml`，在 `<modules>` 中添加新模块：

   ```xml
   <modules>
       <module>hertzbeat-collector-common</module>
       <module>hertzbeat-collector-basic</module>
       <module>hertzbeat-collector-objectstore</module>
       <module>hertzbeat-collector-mycollector</module>  <!-- 新增 -->
   </modules>
   ```

### 2.2 实现 Collector

#### 核心接口

Collector 需要实现 `org.apache.hertzbeat.collector.collect.AbstractCollect` 抽象类：

```java
package org.apache.hertzbeat.collector.collect.mycollector;

import lombok.extern.slf4j.Slf4j;
import org.apache.hertzbeat.collector.collect.AbstractCollect;
import org.apache.hertzbeat.collector.dispatch.DispatchConstants;
import org.apache.hertzbeat.common.entity.job.Metrics;
import org.apache.hertzbeat.common.entity.message.CollectRep;

@Slf4j
public class MyCollectorImpl extends AbstractCollect {

    @Override
    public void preCheck(Metrics metrics) throws IllegalArgumentException {
        // 参数校验逻辑
    }

    @Override
    public void collect(CollectRep.MetricsData.Builder builder, Metrics metrics) {
        // 采集逻辑
    }

    @Override
    public String supportProtocol() {
        return DispatchConstants.PROTOCOL_MYPROTOCOL;
    }
}
```

#### 协议常量定义

建议在模块内创建本地的 `DispatchConstants`，避免修改公共模块：

```java
package org.apache.hertzbeat.collector.dispatch;

public final class DispatchConstants {
    private DispatchConstants() {}
    
    public static final String PROTOCOL_MYPROTOCOL = "myprotocol";
}
```

### 2.3 注册到类路径

**关键步骤**：HertzBeat 运行时的 classpath 由 `apache-hertzbeat-<version>.jar` 中的 `MANIFEST.MF` 的 `Class-Path` 设置。

要让新开发的 Collector 加入到类路径，需要在 `hertzbeat-collector-collector` 的 `pom.xml` 中添加对新模块的依赖：

编辑 `hertzbeat-collector/hertzbeat-collector-collector/pom.xml`：

```xml
<dependencies>
    <!-- 其他依赖 -->
    
    <!-- 添加对新 collector 的依赖 -->
    <dependency>
        <groupId>org.apache.hertzbeat</groupId>
        <artifactId>hertzbeat-collector-mycollector</artifactId>
        <version>${project.version}</version>
    </dependency>
</dependencies>
```

### 2.4 配置文件

**重要**：监控模板文件必须放在 `./hertzbeat-manager/src/main/resources/define` 目录下，否则打包时不会包含。

在 `./hertzbeat-manager/src/main/resources/define` 下创建监控模板文件，例如 `app-myprotocol.yml`：

```yaml
# 监控类型名称
app: myprotocol
# 资源分类
category: custom
# 监控描述
name:
  zh-CN: 自定义协议监控
  en-US: Custom Protocol Monitor
# 参数定义
params:
  - field: host
    name:
      zh-CN: 主机Host
      en-US: Host
    type: host
    required: true
# 指标采集定义
metrics:
  - name: status
    priority: 0
    fields:
      - field: responseTime
        type: 0
        unit: ms
```

---

## 三、开发示例：对象存储 Collector

以对象存储采集器（`hertzbeat-collector-objectstore`）为例，展示如何抽象公共逻辑：

### 3.1 公共协议类

```java
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectStorageProtocol {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private String objectKey;
    private String timezone;
    private String timeout;
    private String operation;
}
```

### 3.2 抽象 Collector 类

```java
@Slf4j
public abstract class AbstractObjectStorageCollectImpl<C> extends AbstractCollect {
    
    // 获取存储类型名称（从 supportProtocol() 转换）
    private String getStorageType() {
        return supportProtocol().toUpperCase();
    }
    
    @Override
    public void preCheck(Metrics metrics) throws IllegalArgumentException {
        // 公共校验逻辑
    }
    
    @Override
    public void collect(CollectRep.MetricsData.Builder builder, Metrics metrics) {
        // 模板方法模式：定义通用流程
        ObjectStorageProtocol protocol = getProtocol(metrics);
        C client = buildClient(protocol);
        try {
            // 执行具体采集操作
            Map<String, String> data = executeOperation(client, protocol);
            appendValueRow(builder, metrics, data);
        } finally {
            closeClient(client);
        }
    }
    
    // 子类需要实现的抽象方法
    protected abstract ObjectStorageProtocol getProtocol(Metrics metrics);
    protected abstract C buildClient(ObjectStorageProtocol protocol);
    protected abstract void closeClient(C client);
    protected abstract Map<String, String> doHeadObject(C client, ObjectStorageProtocol protocol);
    // ... 其他抽象方法
}
```

### 3.3 具体实现类

```java
@Slf4j
public class ObsCollectImpl extends AbstractObjectStorageCollectImpl<ObsClient> {

    @Override
    public String supportProtocol() {
        return DispatchConstants.PROTOCOL_OBS;
    }

    @Override
    protected ObjectStorageProtocol getProtocol(Metrics metrics) {
        return metrics.getObs();
    }

    @Override
    protected ObsClient buildClient(ObjectStorageProtocol protocol) {
        ObsConfiguration config = new ObsConfiguration();
        config.setEndPoint(protocol.getEndpoint());
        return new ObsClient(protocol.getAccessKey(), protocol.getSecretKey(), config);
    }

    @Override
    protected Map<String, String> doHeadObject(ObsClient client, ObjectStorageProtocol protocol) {
        // OBS 特定的实现
    }
    // ... 其他方法实现
}
```

---

## 四、测试

### 4.1 单元测试

使用 JUnit 5 编写测试：

```java
@ExtendWith(MockitoExtension.class)
class MyCollectorImplTest {

    @InjectMocks
    private MyCollectorImpl myCollector;

    @BeforeEach
    void setUp() {
        // 初始化测试数据
    }

    @Test
    void testPreCheck() {
        // 测试参数校验
    }

    @Test
    void testCollect() {
        // 测试采集逻辑
    }
}
```

### 4.2 运行测试

```bash
mvn test -pl hertzbeat-collector/hertzbeat-collector-mycollector
```

---

## 五、完整开发流程总结

```
1. 创建子模块
   └── hertzbeat-collector/hertzbeat-collector-mycollector/
       ├── pom.xml
       └── src/
           ├── main/java/.../MyCollectorImpl.java
           └── main/resources/app-myprotocol.yml

2. 注册到父模块
   └── 编辑 hertzbeat-collector/pom.xml，添加 <module>

3. 注册到类路径
   └── 编辑 hertzbeat-collector/hertzbeat-collector-collector/pom.xml，添加依赖

4. 编写单元测试
   └── src/test/java/.../MyCollectorImplTest.java

5. 构建验证
   └── mvn clean install -DskipTests

6. 打包发布
   └── cd hertzbeat-startup && mvn clean package -DskipTests -Prelease
```

---

## 六、注意事项

1. **协议常量**：建议在模块内定义 `DispatchConstants`，减少跨模块修改
2. **抽象复用**：多个相似 Collector 可提取公共抽象类
3. **依赖管理**：第三方 SDK 版本在模块 `pom.xml` 的 `<properties>` 中统一管理
4. **类路径注册**：务必在 `hertzbeat-collector-collector` 中添加依赖，否则运行时无法加载
5. **配置文件**：监控模板文件必须放在 `./hertzbeat-manager/src/main/resources/define` 下，否则打包时不会包含
