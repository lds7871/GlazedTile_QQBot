# GlazedTile QQBot

## 概述

`GlazedTile_QQBot` 是一个基于 Spring Boot 的 QQ 机器人项目，通过使用OneBot协议依靠NapCat框架来对Napcat本地HTTP接口二次封装和WS消息接受并格式化，提供消息收发、群组任务、WebSocket 通信和定时任务自动化。

## 特性

- NapCat/OneBot WebSocket 通信,API 二次封装
- QQ 群组定时任务（定时保存数据库，定时执行任务，固定时段随机执行任务）
- QQ 群组监听任务（关键词、AT、用户指令、白名单指令）
- 代理支持、日志等级控制、数据源配置灵活

## 提醒

- 具体配置与说明文档请参考：
  - QQBot说明文档.html(项目内)
  - [QQBot说明文档网页](占位URL)
  - [API详细介绍](占位URL)
- 请先在主机配置：
  - NapCatQQ
  - 用户环境变量配置DEEPSEEK_API_KEY
  - 解你的VPN代理端口号(具体参考“QQBot说明文档”)
- 项目可在Windows Server良好运行，其他系统不敢保证

## 感谢项目中使用到的网站/应用/架构

| 鸣谢 | 用途 |
|------------|------|
| [NapCatQQ](https://github.com/NapNeko/NapCatQQ) | 提供框架，本地HTTP与WS服务器支持 |
| [OneBot](https://github.com/botuniverse/onebot) | 提供接口协议支持 |
| [TouchGal](https://www.touchgal.us/) | 指令“gal->”数据获取支持 |
| [维基百科](https://www.wikipedia.org/) | 指令“wiki->XXX”数据获取支持 |
| [myabandonware](https://www.myabandonware.com/) | 指令“今日怀旧->”数据获取支持 |
| [Steam](https://store.steampowered.com/about/) | 指令“steam->XXX”数据获取支持 |
| [DeepSeek](https://platform.deepseek.com/usage) | 多个指令与方法的数据支持 |
| [栗次元API](https://t.alcy.cc/) | 二次元随机图片API支持 |
| [LDS_Memes_Hub](https://github.com/lds7871/LDS_Memes_Hub) | 多个方法Memes调用支持 |


## 项目结构

```
src/main/java/LDS/Person/
├── controller/           # API 入口（消息、群组任务、WebSocket）
├── config/               # 通用配置（数据源、OneBot/NapCat 配置、Swagger、WebSocket）
├── dto/                  # 请求/响应模型（仅 QQ/群组相关）
├── repository/           # 目前仅留空（可添加数据库表的 Repository）
├── service/              # 服务逻辑（群组任务、单防、多业务）
│   └── impl/             # 实现类（目前只保留 QQ 服务）
├── tasks/                # 定时任务（消息监听、问候、GitHub 周报）
├── util/                 # 工具类（OneBot 格式化、图像处理、搜索等）
└── websocket/            # OneBot WebSocket 客户端与处理器
```

## 依赖

- Spring Boot 3.1.4
- MyBatis-Plus 3.5.3
- Lombok
- Springfox Swagger 3.0.0
- MySQL Connector/J 8.0.33
- Fastjson2、Jsoup、Gson 等常用工具库

## 1.数据库准备

1. 创建数据库（示例使用 `XXXX`,详见src/.../resource文件夹）：
   ```sql
   CREATE DATABASE XXXX CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
2. 执行 `src/main/resources/建表文件.sql`，该脚本包含：
   - `vip_id`：白名单 QQ 列表
   - `task_open`：控制各类任务开关
   - `old_game`：老游戏内容存储
   - `group_task`：群任务配置
   - `daliy_greeting`：每日问候信息

## 2.配置文件

项目使用两个配置(详见src/.../resource文件夹)：

| 文件 | 说明 |
| ---- | ---- |
| `application.yml` | Spring Boot 的数据源、JPA、日志、服务端口等配置。请复制 `application.yml.example` 并修改为实际数据库与端口。 |
| `config.properties` | NapCat/OneBot 相关配置（代理、认证、WebSocket 地址等）。请复制 `config.properties.example` 并填入 NapCat 令牌与 QQ ID。 |

两个 `.example` 文件提供模版与注释，适合直接复制后修改。
设置配置文件：
```bash
cp src/main/resources/application.yml.example src/main/resources/application.yml
cp src/main/resources/config.properties.example src/main/resources/config.properties
```
修改配置中的数据库连接、NapCat 令牌、WebSocket 地址等。

## 3.执行启动项目：

- Windows系统建议顺序：
  - B更新依赖
  - A编译
  - A启动
访问 `http://localhost:8090` 验证应用状态。

## 任务开关

`task_open` 表控制运行的周期任务(数据库建表默认全部开启)，包含：

| param_name | 描述 |
|------------|------|
| isMsgLisATTask | @ 消息监听任务 |
| isMsgLisKeyWordTask | 关键词监听任务 |
| isMsgLisUserCmdTask | 用户命令处理任务 |
| isMsgLisVipCmdTask | 白名单命令处理任务 |
| isMsgSchTask | 固定时段随机消息任务 |
| isDailyGreetingCreateTask | 自动创建问候任务 |
| isDailyGreetingMorningTask | 早安问候任务 |
| isDailyGreetingEveningTask | 晚安问候任务 |

可通过数据库操作（`INSERT`/`UPDATE`）修改 `state` 切换任务状态。

## WebSocket 与 NapCat

- WebSocket 客户端位于 `websocket/OneBotWebSocketClient.java`，连接 NapCat 提供的 WebSocket API。
- `config.properties` 中 `WS_URL_LOCAL` 与 `WS_URL_REMOTE` 控制连接哪个 NapCat 实例，`WS_URL_SELECT` 设定使用本地还是远程。
- 认证令牌 `WS_TOKEN` 需要与 NapCat 配置一致（但因为我WS没有配置Token，逻辑代码并没有使用此字段，详见说明文档）。

## 日志与调试

- 日志级别在 `application.yml` 中设置（默认 `INFO`，`LDS.Person` 保留 `DEBUG`）。
- 可通过设置 `logging.level.LDS.Person=DEBUG` 进一步排查问题。

## Swagger 文档

- Springfox Swagger UI 已配置，可在启动后访问 `http://localhost:8090/swagger-ui/` 查看 API。


