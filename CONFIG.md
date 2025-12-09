# GlazedTile_QQBot 配置说明文档

本文档提供项目配置的详细说明，帮助快速理解项目的配置架构和各个配置类的作用。

## 目录结构

```
src/main/java/LDS/Person/
├── config/                  # 配置类目录
│   ├── ConfigManager.java          # 配置文件管理器（单例模式）
│   ├── HttpClientFactory.java      # HTTP客户端工厂（单例模式）
│   ├── MybatisPlusConfig.java      # MyBatis-Plus配置
│   ├── NapCatTaskIsOpen.java       # NapCat任务开关配置
│   ├── RemoteOneBotClientConfig.java # 远程OneBot客户端配置
│   ├── RestTemplateConfig.java     # RestTemplate配置
│   ├── SecurityFilter.java         # 安全过滤器配置
│   ├── SwaggerConfig.java          # Swagger API文档配置
│   └── WebSocketConfig.java        # WebSocket配置
└── ...
```

## 核心配置类

### 1. ConfigManager.java

**作用**: 集中管理应用配置，避免重复加载 `config.properties` 文件

**设计模式**: 单例模式（双重检查锁定）

**主要功能**:
- 加载和管理 `config.properties` 配置文件
- 提供线程安全的配置读取（使用读写锁）
- 支持字符串、整数、布尔类型配置的获取
- 提供常用配置的便捷方法

**常用配置项**:
```properties
# NapCat API 配置
NapCatApiBase=http://127.0.0.1:3000
NapCatAuthToken=your_token_here
NapcatQQID=your_qq_id

# WebSocket 配置
WS_URL_LOCAL=ws://localhost:8090/onebot
WS_URL_REMOTE=ws://127.0.0.1:3001
WS_TOKEN=your_ws_token
WS_URL_SELECT=LOCAL

# 代理配置
proxy.is.open=false
proxy.host=127.0.0.1
proxy.port=33210
```

**使用示例**:
```java
// 获取单例实例
ConfigManager config = ConfigManager.getInstance();

// 获取配置值
String apiBase = config.getNapCatApiBase();
boolean proxyOpen = config.isProxyOpen();
int proxyPort = config.getProxyPort();

// 重新加载配置
config.reload();
```

**线程安全**: 使用 `ReadWriteLock` 保证多线程环境下的安全性，读操作不互斥，写操作独占

---

### 2. HttpClientFactory.java

**作用**: 提供配置好的 `HttpClient` 实例，避免重复创建，提高性能

**设计模式**: 单例模式（双重检查锁定）

**主要功能**:
- 创建和管理共享的 `HttpClient` 实例
- 配置 HTTP/2 协议支持
- 设置连接超时时间（默认20秒）
- 提供自定义超时时间的 `HttpClient` 创建方法

**使用示例**:
```java
// 获取共享的HttpClient实例（推荐）
HttpClient client = HttpClientFactory.getInstance();

// 获取自定义超时时间的HttpClient（创建新实例）
HttpClient customClient = HttpClientFactory.getInstanceWithTimeout(30);
```

**性能优化**: 
- `HttpClient` 是线程安全的，可以在多个线程间共享
- 复用 `HttpClient` 可以减少资源消耗和提高性能
- 使用 HTTP/2 协议提升网络通信效率

---

### 3. 其他配置类

#### MybatisPlusConfig.java
- **作用**: 配置 MyBatis-Plus 的分页插件和其他功能
- **功能**: 数据库分页、SQL性能分析等

#### NapCatTaskIsOpen.java
- **作用**: 管理各类定时任务的开关状态
- **功能**: 从数据库读取任务开关配置，控制任务是否执行

#### RemoteOneBotClientConfig.java
- **作用**: 配置远程 OneBot 客户端连接
- **功能**: 连接到 NapCat WebSocket 服务器

#### RestTemplateConfig.java
- **作用**: 配置 Spring 的 RestTemplate
- **功能**: HTTP 请求客户端配置

#### SecurityFilter.java
- **作用**: 安全过滤器配置
- **功能**: 请求过滤、安全验证等

#### SwaggerConfig.java
- **作用**: 配置 Swagger API 文档
- **功能**: API 文档自动生成和展示

#### WebSocketConfig.java
- **作用**: 配置 WebSocket 服务器
- **功能**: WebSocket 端点注册、消息处理器配置

---

## 配置文件

### application.yml
Spring Boot 主配置文件，包含：
- 数据源配置（MySQL连接）
- JPA/MyBatis配置
- 日志级别配置
- 服务器端口配置（默认8090）
- 其他Spring Boot相关配置

**位置**: `src/main/resources/application.yml`

**示例模板**: `src/main/resources/application.yml.example`

### config.properties
NapCat/OneBot 相关配置文件，包含：
- NapCat API 基础地址和认证令牌
- WebSocket 连接地址（本地/远程）
- 代理配置
- QQ机器人配置

**位置**: `src/main/resources/config.properties`

**示例模板**: `src/main/resources/config.properties.example`

---

## 配置优先级和加载顺序

1. **Spring Boot 配置**: `application.yml` 在应用启动时由 Spring Boot 自动加载
2. **自定义配置**: `config.properties` 由 `ConfigManager` 单例在首次使用时加载
3. **环境变量**: `DEEPSEEK_API_KEY` 等环境变量直接从系统读取

---

## 配置最佳实践

### 1. 使用 ConfigManager 管理自定义配置
```java
// 推荐：使用单例模式，避免重复加载
private static final ConfigManager configManager = ConfigManager.getInstance();
String apiBase = configManager.getNapCatApiBase();
```

### 2. 复用 HttpClient 实例
```java
// 推荐：使用共享实例
HttpClient client = HttpClientFactory.getInstance();

// 不推荐：频繁创建新实例（除非有特殊超时需求）
HttpClient newClient = HttpClientFactory.getInstanceWithTimeout(60);
```

### 3. 配置文件管理
- 敏感信息（如API密钥）应使用环境变量
- `.example` 文件提供配置模板，实际配置文件不提交到版本控制
- 修改配置后，使用 `ConfigManager.reload()` 重新加载

### 4. 线程安全
- `ConfigManager` 和 `HttpClientFactory` 都是线程安全的
- 可以在多线程环境中安全使用

---

## 常见问题

### Q: 如何修改配置而不重启应用？
A: 修改 `config.properties` 后，调用 `ConfigManager.getInstance().reload()` 即可重新加载配置。

### Q: 为什么使用单例模式？
A: 单例模式避免重复创建对象，减少资源消耗。`ConfigManager` 避免重复读取配置文件，`HttpClientFactory` 复用连接池提高性能。

### Q: 如何添加新的配置项？
A: 
1. 在 `config.properties` 中添加配置项
2. 在 `ConfigManager` 中添加对应的 getter 方法
3. 在代码中通过 `ConfigManager.getInstance()` 获取配置值

### Q: HTTP/2 有什么好处？
A: HTTP/2 支持多路复用、头部压缩、服务器推送等特性，可以显著提高网络性能。

---

## 配置迁移说明

### 从 util 包迁移到 config 包

**迁移的类**:
- `ConfigManager.java`: `LDS.Person.util` → `LDS.Person.config`
- `HttpClientFactory.java`: `LDS.Person.util` → `LDS.Person.config`

**原因**:
- 更符合包结构的语义（配置类应该放在 config 包）
- 便于维护和理解项目结构
- 与其他配置类保持一致

**影响的文件**:
- 所有引用这两个类的 import 语句需要更新
- 7个文件引用 `ConfigManager`
- 1个文件引用 `HttpClientFactory`

---

## 参考资料

- [Spring Boot 配置文档](https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html)
- [NapCatQQ 文档](https://github.com/NapNeko/NapCatQQ)
- [OneBot 协议文档](https://github.com/botuniverse/onebot)
- [GlazedTile_QQBot 在线文档](https://zh-company.top/QQBot_Docs)
