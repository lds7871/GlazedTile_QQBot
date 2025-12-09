# AI架构文档 - GlazedTile_QQBot

## 系统架构概览

**框架**: Spring Boot 3.1.4 + Java 17 + Maven
**核心协议**: OneBot + NapCat WebSocket
**关键启用点**: `XAutoApplication.java`

```
NapCat WebSocket (ws://115.190.170.56:3001)
    ↓
RemoteOneBotClient (连接转发)
    ↓
Spring WebSocket Server (ws://localhost:8090/onebot)
    ↓
消息处理系统 (Listeners: AT/KeyWord/UserCmd/VipCmd)
    ↓
定时任务系统 (Tasks: Greeting/Human/OldGame/Scheduling)
```

---

## 依赖关系映射

**配置加载顺序**:
1. XAutoApplication.main() → SpringApplication.run()
2. RemoteOneBotClientConfig → @ContextRefreshedEvent → RemoteOneBotClient.start()
3. 任务注入: MsgLisATTask | MsgLisKeyWordTask | MsgLisVipCmdTask | MsgLisUserCmdTask
4. 消息监听触发业务逻辑

**数据源链路**:
- MySQL: jdbc:mysql://115.190.170.56:3306/ldsperson
- 用户: LDS7871 | 密码: 375279901
- 驱动: com.mysql.cj.jdbc.Driver

---

## 核心模块说明

### 1. 监听任务 (tasks/MsgLisLogic/)

| 类名 | 触发条件 | 业务流程 |
|------|--------|--------|
| **MsgLisATTask** | @机器人 | → ATBotReplyLogic.handleATMessage() → QQ API发送 |
| **MsgLisKeyWordTask** | 关键词匹配 | → KeywordTriggerLogic.processKeyword() → 对应分支业务 |
| **MsgLisUserCmdTask** | 用户指令(steam→/wiki→/gal→/怀旧) | → SteamSearchLogic/WikiSearchLogic/GalgameSearchLogic/OldGameGetLogic |
| **MsgLisVipCmdTask** | VIP/白名单指令 | → VIPGroupTaskCreateLogic/VIPGroupTaskGetLogic/VIPGroupTaskUpLogic/VIPScreenshotLogic/VIPSingleDefenseLogic |

**处理细节**:
- ATBotReplyLogic: 简单文本回复 + DeepSeek API 调用
- KeywordTriggerLogic: 条件判断 + 业务分派
- SteamSearchLogic: HTTP请求Steam API → 解析 → 格式化响应
- WikiSearchLogic: Wikipedia数据爬取 → HTML生成 → 发送
- GalgameSearchLogic: TouchGal API调用 → HTML生成 → 浏览器截图
- OldGameGetLogic: myabandonware爬取 → 数据库持久化
- BilibiliShareLogic: B站视频信息解析 → 分享卡片
- VIP类业务: 数据库查询 → 权限验证 → 专属处理

### 2. 定时任务 (tasks/)

| 类名 | Cron表达式 | 功能 | 控制开关 |
|------|----------|------|---------|
| **MsgSchGreetingTask** | 0 0 8 * * ? | 每日08:00发送早安 | NapCatTaskIsOpen.isMsgSchTask |
| MsgSchHumanTask | (多个时段) | 降低人机检测概率 | NapCatTaskIsOpen.isMsgSchTask |
| MsgSchHumanTask (Evening) | 0 0 20 * * ? | 每日20:00发送晚安 | NapCatTaskIsOpen.isMsgSchTask |
| OldGameGetTask | 0 0 */3 * * ? | 每3小时同步怀旧游戏 | 数据库task_open_map |
| GithubWeeklyTask | 0 0 0 * * 1 | 每周一发送周报 | 任务开关检查 |

**定时任务基础类**: BaseWebSocketClient + RemoteOneBotClient 通过WebSocket发送QQ消息

### 3. 消息处理生命周期

```
消息到达 (OneBotWebSocketController.handleMessage)
    ↓
解析OneBot格式 → 判断类型 (群消息/私聊/meta)
    ↓
执行四层检查:
  1. NapCatTaskIsOpen开关检查
  2. 数据库task_open_map开关检查
  3. 权限/VIP白名单检查
  4. 业务逻辑执行权限检查
    ↓
分发到对应业务逻辑类
    ↓
调用NapCat API → HTTP/WebSocket → 消息回复
```

### 4. API端点映射

| 路由前缀 | Controller | 端点功能 |
|---------|-----------|--------|
| /api/serverinfo | ServerInfoController | JVM监控 + 内存状态 |
| /api/onebot | OneBotWebSocketController | 任务触发 + VIP刷新 + 状态查询 |
| /api/grouptask | GroupTaskController | 群组任务管理 |
| /api/ncat/get | NCatGetController | NapCat数据查询 |
| /api/ncat/send | NCatSendMessageController | NapCat消息发送 |
| ws://localhost:8090/onebot | OneBotWebSocketController | WebSocket通道 |

---

## 关键业务流程

### 流程1: Galgame搜索 (steam→/wiki→/gal→指令)

```
MsgLisUserCmdTask.processMessage("gal->XXX")
  ↓ 关键词匹配: startsWith("gal->")
  ↓ GalgameSearchLogic.search(gameName)
    ├─ GalgameProcessor.process()
    │  ├─ fetchGalgamesJson() [TouchGal API]
    │  ├─ parseGalgamesWithGson() [JSON解析]
    │  ├─ generateHtmlFile() [HTML生成]
    │  ├─ openHtmlFile() [浏览器打开]
    │  ├─ captureScreenshot() [截图]
    │  └─ closeBrowser() [关闭]
  ├─ ImgToUri.convertToBase64() [图片编码]
  ├─ RemoteOneBotClient.sendGroupMessage(groupId, imageUrl)
  └─ 返回成功/失败信息
```

### 流程2: Steam游戏搜索 (steam->XXX指令)

```
MsgLisUserCmdTask.processMessage("steam->XXX")
  ↓ 关键词匹配
  ↓ SteamSearchLogic.search(gameName)
    ├─ HTTP GET: https://store.steampowered.com/api/appsearch/?term=XXX&limit=10
    ├─ 解析JSON → 提取游戏ID/名称/价格
    ├─ 格式化为QQ消息
    └─ RemoteOneBotClient.sendGroupMessage()
```

### 流程3: VIP特权处理 (VIP指令)

```
MsgLisVipCmdTask.processMessage(message)
  ↓ 检查sender_id是否在VIP白名单
  ↓ 数据库查询: VIPUser.findById(userId)
  ↓ 根据指令类型:
    ├─ "vip-task-create" → VIPGroupTaskCreateLogic → 任务数据库持久化
    ├─ "vip-task-get" → VIPGroupTaskGetLogic → 返回任务列表
    ├─ "vip-task-update" → VIPGroupTaskUpLogic → 更新任务
    ├─ "vip-screenshot" → VIPScreenshotLogic → 指定链接截图
    └─ "vip-defense" → VIPSingleDefenseLogic → 群防御配置
```

---

## 静态配置注入点

**RemoteOneBotClientConfig.java行28-50**: 
- 读取config.properties
- 初始化: NCAT_API_BASE, NCAT_AUTH_TOKEN, NapCatIsOpen, ProxyUrl

**MsgLisUserCmdTask.java行33-55**:
- 同样方式读取配置
- 初始化: NCAT_API_BASE, NCAT_AUTH_TOKEN

**Config.properties必填项**:
```
NapCatApiBase=http://115.190.170.56:3000
NapCatAuthToken=YOUR_TOKEN
NapCatIsOpen=true
ProxyUrl=http://proxy:port (可选)
WsUrl=ws://115.190.170.56:3001
```

---

## 业务逻辑类详查表

### tasks/MsgLisLogic/ 消息监听分支

| 类 | 方法 | 输入 | 输出 | 依赖 |
|----|-----|------|------|-----|
| ATBotReplyLogic | handleATMessage() | Message | QQ_Response | DeepSeekAPI |
| KeywordTriggerLogic | processKeyword() | text,keyword | boolean+action | RegexMatcher |
| SteamSearchLogic | search() | gameName | List<SteamGame> | HttpClient |
| WikiSearchLogic | search() | query | WikiPage_HTML | Selenium |
| GalgameSearchLogic | search() | title | Screenshot_URI | GalgameProcessor |
| OldGameGetLogic | fetch() | - | void | MySQLRepository |
| BilibiliShareLogic | parseVideo() | url | BiliCard_JSON | HttpClient |
| VIPGroupTaskCreateLogic | create() | Task_DTO | Task_Id | DB_Repository |
| VIPGroupTaskGetLogic | getAll() | userId | List<Task> | DB_Repository |
| VIPGroupTaskUpLogic | update() | Task_DTO | boolean | DB_Repository |
| VIPScreenshotLogic | screenshot() | url | Image_URI | Selenium |
| VIPSingleDefenseLogic | configure() | config | void | DB_Repository |

### tasks/ 定时任务主类

| 类 | 触发 | 逻辑类 | 操作 |
|----|-----|--------|------|
| MsgSchGreetingTask | 08:00 | MorningGreetingLogic | QQ_MessageSend |
| MsgSchHumanTask | 多时段 | RandomChatLogic | QQ_MessageSend |
| MsgSchHumanTask | 20:00 | EveningGreetingLogic | QQ_MessageSend |
| OldGameGetTask | 3h周期 | OldGameGetLogic | DB_Insert |

---

## WebSocket架构

**RemoteOneBotClient (websocket/)**:
```
@OnOpen: 连接建立 → 记录sessionId
@OnMessage: 消息接收 → JSON解析 → RemoteWebSocketClientHandler分发
@OnClose: 连接关闭 → 重试逻辑
@OnError: 错误处理 → 日志输出

消息转发路由:
  message_type=message → OneBotWebSocketController.handleMessage()
  message_type=meta_event → 忽略或处理心跳
```

**Spring WebSocket (ws://localhost:8090/onebot)**:
```
客户端连接 → OneBotWebSocketController
  ├─ WebSocketHandler: OneBotWebSocketController
  │  ├─ @SendTo /onebot
  │  ├─ @MessageMapping /receive
  │  └─ @PostMapping /send
```

---

## 服务监控接口

**ServerInfoController** (/api/serverinfo):
```
GET /startup → 返回:
{
  "启动时间": "2024-12-09T...",
  "运行时长(秒)": 3600,
  "堆内存": {"堆_已用_MB": 512, "堆_最大_MB": 2048},
  "非堆内存": {...},
  "运行时信息": {"JVM总内存_MB": 1024}
}

GET /JVMoverview → JVM详细信息 (内存+线程+GC)
```

---

## 性能关键点

1. **单例模式复用**: ConfigManager / HttpClientFactory / RemoteOneBotClient
2. **线程池**: ThreadPool在RemoteOneBotClient处理消息分发
3. **WebSocket长连接**: 避免重复建立HTTP连接
4. **数据库连接池**: Hikari (Spring Boot默认)
5. **HTTP/2支持**: HttpClientFactory配置
6. **读写锁**: ConfigManager使用ReadWriteLock保证并发安全

---

## 重要常量和配置

| 常量 | 值 | 说明 |
|-----|-----|------|
| Spring Port | 8090 | 服务监听端口 |
| Session Timeout | 1800s | 会话30分钟超时 |
| MySQL Connection | 115.190.170.56:3306 | 远程数据库 |
| NapCat WebSocket | ws://115.190.170.56:3001 | 消息服务器 |
| Log Level (LDS.Person) | DEBUG | 项目日志级别 |
| Log Level (Spring) | WARN | 框架日志级别 |
| HttpClient Timeout | 20s | 连接超时 |

---

## 扩展点和关键类

**新增业务逻辑**: 创建tasks/MsgLisLogic/XXXLogic.java → 注入MsgLisUserCmdTask → 添加关键词匹配分支

**新增定时任务**: 创建tasks/XXXTask.java → @Component + @Scheduled(cron="") → 自动扫描执行

**新增API端点**: 创建controller/XXXController.java → @RestController + @RequestMapping("/api/xxx")

**新增数据模型**: entity/XXX.java + repository/XXXRepository.java → @Entity + JpaRepository/MyBatisPlus

---

## 启动和调试

**启动脚本** (A启动.cmd):
```batch
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dfile.encoding=GBK"
```

**关键环境变量**:
- DEEPSEEK_API_KEY: 必填 (ATBotReplyLogic AI回复)
- HTTP_PROXY / HTTPS_PROXY: 可选 (代理设置)
- file.encoding: GBK (中文编码支持)

**常用调试端点**:
- http://localhost:8090/swagger-ui.html → API文档
- http://localhost:8090/api/serverinfo/startup → 服务状态
- http://localhost:8090/api/onebot/status → OneBot连接状态

---

## 代码检查清单 (AI理解用)

- [x] 三层架构: Controller → Service/Logic → Repository
- [x] 单例模式应用: 配置/客户端/工厂类
- [x] 任务开关双检查: NapCatTaskIsOpen + 数据库task_open_map
- [x] WebSocket双向通信: RemoteOneBotClient ↔ Spring Server
- [x] 消息异步处理: ThreadPool分发任务
- [x] 数据库驱动: MySQL 8.0 + JPA + MyBatis-Plus
- [x] API标准化: Swagger文档 + 统一响应格式
- [x] 日志分级: 项目DEBUG + 框架WARN
- [x] 错误处理: Try-Catch + 日志记录 + 用户提示
- [x] 中文支持: GBK编码 + UTF-8配置

