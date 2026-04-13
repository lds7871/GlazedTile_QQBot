# GlazedTile QQBot

## 概述

`GlazedTile_QQBot` 是一个基于 Spring Boot 的 QQ 机器人项目，通过使用OneBot协议依靠NapCat框架来对Napcat本地HTTP接口二次封装和WS消息接受并格式化，提供消息收发、群组任务、WebSocket 通信和定时任务自动化。


## WebSocket 与 NapCat

- WebSocket 客户端位于 `websocket/OneBotWebSocketClient.java`，连接 NapCat 提供的 WebSocket API。
- `config.properties` 中 `WS_URL_REMOTE` 控制连接 NapCat 实例。
- 该项目需要NapcatQQ开启WS服务器和HTTP服务器，建议配置token都为同一个值，项目所有token默认0000，ip为0.0.0.0，如果你配置为0000则不必额外配置。

## 特性

- NapCat/OneBot WebSocket 通信,API 二次封装
- QQ 群组定时任务
- QQ 群组监听任务
- 代理支持

## 提醒

- 目前简化重写项目还处于初期阶段，暂不编写详细文档。此分支目的是移除数据库相关和增加代码复用性。




## 配置文件

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






