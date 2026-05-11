# AI架构文档 - GlazedTile_QQBot（2026年4月更新版）

## 系统架构概览

**框架**: Spring Boot 3.1.4 + Java 17 + Maven  
**核心协议**: OneBot 11 + NapCat WebSocket  
**启动入口**: XAutoApplication.java  
**架构模式**: 工厂模式 + 策略模式 + 单例模式

```
远程 NapCat 服务器 (ws://115.190.170.56:3001)
    ↓ [WebSocket 连接]
RemoteOneBotClient (远程消息客户端)
    ↓ [消息转发]
Spring 本地服务器 (ws://localhost:9200/onebot)
    ↓ [路由分发]
消息处理系统 (MsgLisATTask, KeywordTriggerLogic等)
    ↓ [异步处理]
业务逻辑层 (各类Logic类)
    ↓ [API调用]
NapCat API (send_group_msg 等)
```

---

## 依赖加载顺序

1. **应用启动**: XAutoApplication.main() → SpringApplication.run()
2. **配置初始化**:
   - ConfigManager.getInstance() 加载 config.properties (单例)
   - 读取: NapCatApiBase, NapCatAuthToken, NapcatQQID, WsUrlRemote等
3. **Spring Bean 注册**:
   - RestTemplateConfig 注入 RestTemplate
   - WebSocketConfig 配置 Spring WebSocket 端点
   - RemoteOneBotClientConfig 初始化远程连接
4. **远程 WebSocket 连接**: @EventListener(ContextRefreshedEvent.class) 触发
   - RemoteOneBotClient.start() 连接远程 NapCat
   - 连接成功后启动心跳任务
5. **任务开关加载**: NapCatTaskIsOpen 初始化静态变量
   - isMsgLisATTask: 消息监听任务开关
   - isMsgSchTask: 定时任务开关
6. **定时任务启动**: @EnableScheduling 启动 @Scheduled 任务
   - MsgSchHumanTask 定时执行

---

## 核心模块架构

### 1. 配置与工厂层 (config/)

#### ConfigManager (单例模式)
```java
作用: 集中管理应用配置，避免重复加载config.properties
特性:
  - 单例模式 (双重检查锁定)
  - 读写锁保证线程安全
  - 支持 getString(), getBoolean(), getInt() 等多种获取方法

关键配置项:
  NapCatApiBase: NapCat API基础URL (http://115.190.170.56:3000)
  NapCatAuthToken: API认证Token
  NapcatQQID: 机器人QQ号
  WsUrlRemote: 远程WebSocket URL (ws://115.190.170.56:3001?access_token=xxx)
  NapCatIsOpen: 是否启用NapCat连接 (boolean)
```

#### NapCatTaskIsOpen (静态开关)
```java
作用: 控制消息监听和定时任务的启用/禁用
字段:
  public static boolean isMsgLisATTask = true;   // 消息监听@机器人
  public static boolean isMsgSchTask = true;     // 定时任务
```

#### TaskFactory (工厂工具类)
```java
作用: 为所有Task提供通用能力 (静态方法)
核心能力:
  1. isListenerTaskEnabled() / isScheduledTaskEnabled() - 检查任务开关
  2. extractGroupMessage() - 从WebSocket消息提取群聊信息
  3. isAtBot() - 检查是否@机器人
  4. buildTextMessage() / buildImageMessage() - 构建消息请求体
  5. sendGroupMessage() / sendGroupMessageWithRetry() - 发送消息(支持重试)
  6. createAuthHeaders() - 创建认证请求头

特性: 无需创建实例，所有方法均为static，禁止继承(final class)
```

### 2. WebSocket 通信层 (websocket/)

#### BaseWebSocketClient (抽象基类)
```
作用: 提供WebSocket客户端的通用功能
功能:
  - start() / stop() - 连接管理
  - startHeartbeat() - 定期发送心跳
  - 异常重试和断线重连逻辑

内部线程池: ScheduledExecutorService (2个线程)
```

#### RemoteOneBotClient (extends BaseWebSocketClient)
```
作用: 连接远程NapCat服务器，转发消息到本地Spring服务器
流程:
  1. start() 连接 ws://115.190.170.56:3001?access_token=xxx
  2. @OnMessage 接收远程消息 (JSON格式)
  3. 屏蔽心跳和API响应消息
  4. 调用 MsgLisATTask.handleMessage() 处理消息
  5. 消息转发到本地 Spring WebSocket Handler

关键注入: MsgLisATTask (通过setter注入)
```

#### OneBotWebSocketHandler (@Component)
```
作用: Spring本地 WebSocket 服务器的消息处理器
监听地址: ws://localhost:9200/onebot

功能:
  - afterConnectionEstablished() - 客户端连接时记录 session
  - handleTextMessage() - 处理来自客户端的消息
  - broadcastMessage() - 向所有连接的客户端广播消息
  - getConnectionCount() - 获取当前连接数

支持的 action:
  - get_status: 返回机器人在线状态
  - get_version: 返回版本信息
  - get_login_info: 返回登录信息
```

### 3. 消息监听层 (tasks/)

#### MsgLisATTask (@Component)
```
作用: 监听所有群消息，检查是否@机器人，自动回复

处理流程:
  1. RemoteOneBotClient 转发消息到此 handleMessage()
  2. 检查 NapCatTaskIsOpen.isMsgLisATTask 开关
  3. TaskFactory.extractGroupMessage() 提取群聊信息
  4. TaskFactory.isAtBot() 检查是否@机器人
  5. 若是@机器人，调用 DeepSeekAPI 生成回复
  6. 通过 RestTemplate 发送回复消息

依赖注入:
  - RestTemplate: HTTP客户端 (来自RestTemplateConfig)
  - ConfigManager: 配置管理器 (单例)

重试机制:
  - 基础重试延迟: 1000ms
  - 服务器错误延迟: 2000ms
  - 支持自定义重试次数
```

#### KeywordTriggerLogic (@Component)
```
作用: 关键词匹配逻辑，触发关键字响应

关键字类别:
  - GAL系列: ["旮旯", "gal", "GAL", "Gal"] → 发送BIGHead图片
  - 魔裁系列: ["魔裁", "少女", "魔法", "审判"] → 发送MoCai图片
  - 随机触发: 1/110 概率随机发送图片

图片类型: BIGHead, MoCai, Memes

触发方式:
  - 关键词精准匹配优先
  - 未匹配时有概率随机触发
  - 通过 RestTemplate 调用 NapCat API 发送消息

方法:
  - triggerKeywordResponse(groupId, rawMessage) - 主流程
  - triggerRandomImage(groupId) - 公开的随机图片触发
```

### 4. 定时任务层 (tasks/)

#### MsgSchHumanTask (@Component @Scheduled)
```
作用: 定时任务，降低人机查封概率

Cron表达式: "0 * 9-21 * * *" (每小时的每分钟执行,仅9-21时段)

流程:
  1. 检查 NapCatTaskIsOpen.isMsgSchTask 开关
  2. 检查当前时间是否在 9:00-22:00 范围内
  3. 检查是否在 5% 概率内 (RANDOM.nextInt(100) < 5)
  4. 若满足条件，调用 randomChatLogic.generateAndSendRandomChat()

依赖注入:
  - RandomChatLogic: 随机聊天逻辑
  - KeywordTriggerLogic: 关键词触发逻辑

特性: 避免频繁触发，仅小概率执行
```

#### RandomChatLogic (@Component)
```
作用: 生成随机群聊信息，模拟人工活动

核心方法: generateAndSendRandomChat()

流程:
  1. getLastGroupId() 获取最后一次消息的群ID
  2. 构建Prompt: "生成一句无关紧要的科普对话，不要带引号"
  3. 调用 DeepSeekAPI 生成对话文本 (需要DEEPSEEK_API_KEY环境变量)
  4. 通过 NapCat API 发送到群聊

关键特性:
  - 记录每次消息的群ID (recordLastGroupId)
  - 支持用户昵称记录 (userNicknameMap)
  - 异常处理和日志记录

静态方法:
  - recordLastGroupId(groupId) - 记录群ID
  - getLastGroupId() - 获取群ID
```

### 5. 业务工具层 (util/)

#### DSchatNcatQQ
```
作用: DeepSeek API Java客户端，支持多用户对话历史管理

核心特性:
  - 使用 Java 11+ HttpClient (高性能非流式调用)
  - 线程安全的 ConcurrentHashMap 存储对话历史
  - 每个用户最多保存15条消息 (节省token)

方法:
  - Usedeepseek(prompt) - 生成AI回复
  - getAllHistoryAsJson() - 获取所有用户的对话完整历史(JSON)

环境变量: DEEPSEEK_API_KEY (必填)

性能优化: 共享的HttpClient实例 (通过HttpClientFactory获取)
```

#### OneBotMessageFormatter
```
作用: OneBot消息格式化工具
主要方法: 将消息格式化为OneBot协议要求的格式
```

### 6. 控制层 (controller/)

#### OneBotWebSocketController (@RestController)
```
路由前缀: /api/onebot

端点:
  GET /status
    - 获取WebSocket连接状态
    - 返回: connected_clients, websocket_url等

  GET /config
    - 获取WebSocket配置信息
    - 返回: endpoint, protocol, onebot_version等

  POST /broadcast
    - 向所有连接客户端广播消息
    - 请求体: JSONObject消息

  GET /conversation-history
    - 获取所有用户的AI对话历史
    - 返回: 所有用户的DeepSeek对话记录
```

#### ServerInfoController (@RestController)
```
路由前缀: /api/serverinfo

端点:
  GET /startup - 服务启动信息
  GET /JVMoverview - JVM详细信息
```

#### NCatGetController, NCatSendMessageController
```
路由前缀: /api/ncat/*
作用: NapCat API的HTTP代理
```

---

## 消息处理完整流程

### 流程：群消息到达 → 处理 → 回复

```
1. 远程消息到达
   NapCat 发送 WebSocket 消息 (JSON格式)

2. RemoteOneBotClient 接收
   @OnMessage 回调 → 解析JSON
   ├─ 屏蔽心跳消息 (meta_event_type=heartbeat)
   ├─ 屏蔽API响应 (包含status/retcode/echo)
   └─ 其他消息转发

3. 消息分发
   RemoteOneBotClient.onMessage()
   ├─ 调用 MsgLisATTask.handleMessage()
   └─ 并行消息处理

4. MsgLisATTask 处理
   ├─ 检查 NapCatTaskIsOpen.isMsgLisATTask 开关
   ├─ TaskFactory.extractGroupMessage() 提取信息
   │  └─ 提取: groupId, userId, rawMessage, displayName
   ├─ TaskFactory.isAtBot() 检查@机器人
   ├─ 若是@机器人:
   │  ├─ 获取用户昵称
   │  ├─ DSchatNcatQQ 生成AI回复
   │  ├─ TaskFactory.sendGroupMessageWithRetry() 发送
   │  └─ 重试机制 (最多3次)
   └─ 并发安全: 每个消息独立处理

5. 定时任务并行执行
   MsgSchHumanTask.scheduleRandomChat() (每分钟执行)
   ├─ 小概率条件检查
   ├─ RandomChatLogic.generateAndSendRandomChat()
   ├─ DSchatNcatQQ 生成随机对话
   └─ 发送到最后一次消息的群ID

6. 关键词触发 (并发执行)
   KeywordTriggerLogic.triggerKeywordResponse()
   ├─ 检查关键词匹配
   ├─ 未匹配时随机触发
   └─ 发送对应图片
```

---

## 新建 Task 完整流程

### 第1步：确定 Task 类型

**流程型任务(监听消息)**
```
触发条件: 接收到群消息时自动处理
创建位置: tasks/ 目录
示例: MsgLisATTask, 自定义的消息监听任务

实现方式:
  1. 创建 MyListenerTask.java
  2. @Component 注解
  3. @Autowired 注入需要的依赖
  4. 实现 handleMessage(JSONObject) 方法
  5. 在 RemoteOneBotClient 中调用
```

**定时型任务(Cron执行)**
```
触发条件: 按照 Cron 表达式定时执行
创建位置: tasks/ 目录
示例: MsgSchHumanTask

实现方式:
  1. 创建 MyScheduledTask.java
  2. @Component 注解
  3. @Scheduled(cron="...") 注解定时方法
  4. Spring 自动扫描并执行
```

### 第2步：文件创建模板

#### 示例1: 新建监听型Task

**文件**: tasks/MyListenerTask.java

```java
package LDS.Person.tasks;

import com.alibaba.fastjson2.JSONObject;
import LDS.Person.config.NapCatTaskIsOpen;
import LDS.Person.config.ConfigManager;
import LDS.Person.tasks.MsgLisLogic.MyCustomLogic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

/**
 * 自定义消息监听任务 - 处理特定类型消息
 */
@Component
@Slf4j
public class MyListenerTask {

    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private MyCustomLogic myCustomLogic;

    // 配置单例
    private static final ConfigManager CONFIG = ConfigManager.getInstance();

    /**
     * 处理接收到的 WebSocket 消息
     * 
     * @param message WebSocket 消息的 JSON 对象
     */
    public void handleMessage(JSONObject message) {
        // 1. 检查任务开关
        if (!NapCatTaskIsOpen.isMsgLisATTask) {
            return;
        }

        try {
            // 2. 使用 TaskFactory 提取群聊信息
            TaskFactory.GroupMessageInfo msgInfo = TaskFactory.extractGroupMessage(message);
            
            // 3. 检查是否为群消息
            if (msgInfo == null) {
                return;
            }

            // 4. 自定义业务逻辑
            if (shouldProcess(msgInfo.getRawMessage())) {
                myCustomLogic.process(msgInfo, restTemplate);
            }

        } catch (Exception e) {
            log.error("处理消息异常 - 群ID: {}", message.getLong("group_id"), e);
        }
    }

    private boolean shouldProcess(String rawMessage) {
        // 自定义判断逻辑
        return rawMessage != null && rawMessage.contains("your_keyword");
    }
}
```

#### 示例2: 新建定时型Task

**文件**: tasks/MyScheduledTask.java

```java
package LDS.Person.tasks;

import LDS.Person.config.NapCatTaskIsOpen;
import LDS.Person.tasks.MsgSchLogic.MyScheduledLogic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * 自定义定时任务 - 按 Cron 表达式执行
 */
@Component
@Slf4j
public class MyScheduledTask {

    @Autowired
    private MyScheduledLogic myScheduledLogic;

    /**
     * 每天 10:00 执行
     */
    @Scheduled(cron = "0 0 10 * * ?")
    public void executeAt10AM() {
        // 1. 检查任务开关
        if (!NapCatTaskIsOpen.isMsgSchTask) {
            log.warn("定时任务已禁用");
            return;
        }

        try {
            log.info("执行定时任务...");
            // 2. 调用业务逻辑
            myScheduledLogic.doWork();
        } catch (Exception e) {
            log.error("定时任务执行异常", e);
        }
    }

    /**
     * Cron 表达式示例:
     * "0 0 0 * * ?" - 每天 00:00
     * "0 0 */3 * * ?" - 每3小时
     * "0 * 9-21 * * ?" - 9-21点 每分钟
     * "0 0 0 ? * 1" - 每周一 00:00
     */
}
```

### 第3步：创建业务逻辑类

**文件**: tasks/MsgLisLogic/MyCustomLogic.java (或 MsgSchLogic/MyScheduledLogic.java)

```java
package LDS.Person.tasks.MsgLisLogic;

import LDS.Person.config.ConfigManager;
import LDS.Person.tasks.TaskFactory;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

/**
 * 自定义业务逻辑类 - 处理具体业务
 */
@Component
@Slf4j
public class MyCustomLogic {

    @Autowired
    private RestTemplate restTemplate;

    private static final ConfigManager CONFIG = ConfigManager.getInstance();
    private static final String NCAT_API_BASE = CONFIG.getNapCatApiBase();
    private static final String NCAT_AUTH_TOKEN = CONFIG.getNapCatAuthToken();

    /**
     * 执行自定义业务逻辑
     */
    public void process(TaskFactory.GroupMessageInfo msgInfo, RestTemplate template) {
        try {
            Long groupId = msgInfo.getGroupId();
            String displayName = msgInfo.getDisplayName();
            
            log.info("处理消息 - 群: {}, 用户: {}", groupId, displayName);

            // 步骤1: 处理消息
            String result = processMessage(msgInfo.getRawMessage());

            // 步骤2: 发送回复
            JSONObject request = TaskFactory.buildTextMessage(groupId, result);
            boolean success = TaskFactory.sendGroupMessageWithRetry(template, groupId, request, 3);

            if (success) {
                log.info("消息发送成功");
            } else {
                log.error("消息发送失败");
            }

        } catch (Exception e) {
            log.error("业务逻辑处理异常", e);
        }
    }

    private String processMessage(String rawMessage) {
        // 自定义消息处理逻辑
        return "处理结果: " + rawMessage;
    }
}
```

### 第4步：在 Task 中注入逻辑类

**修改**: RemoteOneBotClient.java (添加调用)

```java
@Override
public void onMessage(String message) {
    try {
        JSONObject json = JSONObject.parseObject(message);
        
        // ... 现有的屏蔽逻辑 ...

        if (!isHeartbeat && !isStatusResponse) {
            // 调用已有的 MsgLisATTask
            if (msgLisATTask != null) {
                msgLisATTask.handleMessage(json);
            }
            
            // [新增] 调用新的 Task
            if (myListenerTask != null) {
                myListenerTask.handleMessage(json);
            }
        }
    } catch (Exception e) {
        log.error("消息处理异常", e);
    }
}
```

### 第5步：配置文件更新 (如需要)

**文件**: resources/config.properties

```properties
# 如果 Task 需要新的配置
MyCustomParam=value
MyTaskEnabled=true
```

**读取配置**:
```java
private static final String MY_PARAM = CONFIG.getString("MyCustomParam", "default_value");
private static final boolean MY_TASK_ENABLED = CONFIG.getBoolean("MyTaskEnabled", true);
```

### 第6步：测试检查清单

```
[ ] 1. Task 类添加了 @Component 注解
[ ] 2. 业务逻辑类添加了 @Component 注解
[ ] 3. 依赖通过 @Autowired 正确注入
[ ] 4. 实现了任务开关检查 (NapCatTaskIsOpen)
[ ] 5. 使用 TaskFactory 发送消息 (推荐)
[ ] 6. 异常处理完善 (try-catch + 日志)
[ ] 7. 微调 log.info() 记录关键业务流
[ ] 8. 如果是定时任务，编制正确的 Cron 表达式
[ ] 9. 如果需要，在配置文件中添加新参数
[ ] 10. 在 RemoteOneBotClient 中注册调用 (若为监听型)
```

---

## 消息构建和发送常用方法

### 发送文本消息
```java
JSONObject request = TaskFactory.buildTextMessage(groupId, "你好");
boolean success = TaskFactory.sendGroupMessage(restTemplate, groupId, request);
```

### 发送图片消息
```java
JSONObject request = TaskFactory.buildImageMessage(groupId, "https://example.com/image.png");
boolean success = TaskFactory.sendGroupMessage(restTemplate, groupId, request);
```

### 带重试的发送 (推荐)
```java
JSONObject request = TaskFactory.buildTextMessage(groupId, "重要消息");
// 最多重试3次，失败后自动延迟重试
boolean success = TaskFactory.sendGroupMessageWithRetry(restTemplate, groupId, request, 3);
```

### 创建认证请求头 (高级用法)
```java
HttpHeaders headers = TaskFactory.createAuthHeaders();
// 用于自定义HTTP请求

HttpEntity<String> entity = new HttpEntity<>(body, headers);
ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
```

### 检查@机器人
```java
if (TaskFactory.isAtBot(rawMessage)) {
    // 机器人被@了
}
```

---

## 关键配置和常量

| 配置项 | 值 | 说明 |
|-------|-----|------|
| spring.port | 9200 | Spring 本地服务端口 |
| NapCat ApiURL | http://115.190.170.56:3000 | NapCat API基础地址 |
| NapCat WebSocket | ws://115.190.170.56:3001 | NapCat通信服务器 |
| 重试基础延迟 | 1000ms | 消息发送失败的重试等待 |
| 服务器错误延迟 | 2000ms | 5xx错误的加长重试等待 |
| 心跳间隔 | 30s | WebSocket心跳发送间隔 |
| 连接超时 | 10s | WebSocket连接建立超时 |
| Max消息历史 | 15条 | DeepSeek单用户保存的对话数 |
| 日志级别 | DEBUG | 项目(LDS.Person)日志级别 |

---

## 环境变量

| 变量名 | 说明 | 必填 |
|-------|------|-----|
| DEEPSEEK_API_KEY | DeepSeek API密钥 | 是(用于AI回复) |
| file.encoding | 文件编码 | 是(设置为GBK) |
| HTTP_PROXY | HTTP代理地址 | 否 |
| HTTPS_PROXY | HTTPS代理地址 | 否 |

---

## 常见错误和解决方案

### 错误1: Task 未被执行
**可能原因**:
- 类未添加 @Component 注解
- NapCatTaskIsOpen 的相应开关为 false
- 定时任务的 Cron 表达式不正确

**解决方案**:
```java
@Component  // 必须添加
@Slf4j
public class MyTask {
    // 检查开关
    if (!NapCatTaskIsOpen.isMsgSchTask) return;
    
    // 检查 Cron: https://cron.qqe2.com/
}
```

### 错误2: 消息发送失败
**可能原因**:
- groupId 无效
- NapCat API 异常
- 认证Token过期

**解决方案**:
```java
// 使用 TaskFactory.sendGroupMessageWithRetry() 自动重试
boolean success = TaskFactory.sendGroupMessageWithRetry(
    restTemplate, groupId, request, 3);  // 重试3次

if (!success) {
    log.error("发送失败，检查API或Token");
}
```

### 错误3: DeepSeek API 超时
**可能原因**:
- DEEPSEEK_API_KEY 未设置或无效
- 网络连接异常

**解决方案**:
```java
// 设置环境变量
export DEEPSEEK_API_KEY=your_api_key

// 在代码中检查
String apiKey = System.getenv("DEEPSEEK_API_KEY");
if (apiKey == null || apiKey.isEmpty()) {
    log.error("DEEPSEEK_API_KEY 未设置");
}
```

---

## 性能优化建议

1. **复用单例**: 使用 ConfigManager.getInstance(), HttpClientFactory.getInstance()
2. **避免频繁创建HttpHeaders**: 使用 TaskFactory.createAuthHeaders()
3. **异步处理**: RestTemplate 通过 Spring 的线程池处理
4. **合理设置重试次数**: 不超过3次，避免重复请求
5. **消息历史限制**: DeepSeek 单用户保存15条消息，节省token
6. **心跳优化**: 30秒一次心跳，避免连接被断开

---

## 启动和调试

**启动脚本** ([A启动.cmd](A启动.cmd)):
```batch
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dfile.encoding=GBK"
```

**常用调试端点**:
- 连接状态: http://localhost:9200/api/onebot/status
- 配置信息: http://localhost:9200/api/onebot/config
- 对话历史: http://localhost:9200/api/onebot/conversation-history
- 服务信息: http://localhost:9200/api/serverinfo/startup
- API文档: http://localhost:9200/swagger-ui.html

---

## 总结检查清单

- [x] 工厂模式应用: TaskFactory 提供通用工具
- [x] 单例模式: ConfigManager, HttpClientFactory
- [x] 策略模式: 多种业务逻辑类独立实现
- [x] 开关机制: 双层检查(NapCatTaskIsOpen + 业务逻辑)
- [x] 重试机制: 自动重试失败请求
- [x] 心跳机制: 保持 WebSocket 连接活跃
- [x] 线程安全: 读写锁, ConcurrentHashMap
- [x] 错误处理: Try-Catch + 详细日志 + 用户提示
- [x] 消息格式化: TaskFactory 构建标准 OneBot 消息
- [x] 基础映射: 所有 API 端点均已注册

