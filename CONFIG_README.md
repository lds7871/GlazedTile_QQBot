# 配置文件说明

## 概述

本项目使用两个主配置文件：
- `application.yml` - Spring Boot 应用配置
- `config.properties` - 应用程序自定义配置

## 快速开始

### 1. 从示例文件创建配置

```bash
# 复制示例文件
cp src/main/resources/application.yml.example src/main/resources/application.yml
cp src/main/resources/config.properties.example src/main/resources/config.properties
```

### 2. 修改配置文件

根据你的实际环境修改以下配置项：

#### application.yml

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `spring.datasource.url` | MySQL 数据库连接 URL | `jdbc:mysql://localhost:3306/ldsperson` |
| `spring.datasource.username` | 数据库用户名 | `root` |
| `spring.datasource.password` | 数据库密码 | `password123` |

#### config.properties

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `proxy.is.open` | 是否启用代理 | `true` 或 `false` |
| `proxy.host` | 代理服务器地址 | `127.0.0.1` |
| `proxy.port` | HTTP/HTTPS 代理端口 | `33210` |
| `proxy.port.socks` | SOCKS 代理端口 | `33211` |
| `NapCatIsOpen` | 是否启用 NapCat | `true` |
| `NapCatApiBase` | NapCat API 地址 | `http://localhost:3000` |
| `NapCatAuthToken` | NapCat 认证令牌 | 从 NapCat 配置获取 |
| `NapcatQQID` | QQ 机器人 ID | `123456789` |
| `WS_URL_LOCAL` | 本地 WebSocket 端点 | `ws://localhost:7090/onebot` |
| `WS_URL_REMOTE` | 远程 WebSocket 端点 | `ws://server.ip:3001` |
| `WS_URL_SELECT` | 选择连接的服务器 | `LOCAL` 或 `REMOTE` |
| `WS_TOKEN` | WebSocket 认证令牌 | 自定义令牌 |

## 配置详解

### NapCat 配置

NapCat 是一个 QQ 机器人框架，本项目通过 WebSocket 与其通信。

**获取 NapCatAuthToken：**
1. 启动 NapCat
2. 查看 NapCat 的配置或启动日志
3. 获取其中的认证令牌

**配置 WebSocket：**
- 如果 NapCat 运行在本地，使用 `WS_URL_LOCAL`
- 如果 NapCat 运行在远程服务器，使用 `WS_URL_REMOTE` 并设置 `WS_URL_SELECT=REMOTE`

### 代理配置

如果你的网络需要通过代理访问外部资源：
- 设置 `proxy.is.open=true`
- 配置正确的代理地址和端口

### 数据库配置

项目使用 MySQL 数据库，需要提前创建数据库并运行初始化脚本：

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE ldsperson CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 运行初始化脚本
mysql -u root -p ldsperson < src/main/resources/建表文件.sql
```

## 示例文件

### application.yml.example
包含 Spring Boot 的所有配置示例，包括：
- 数据源配置
- JPA/Hibernate 配置
- 日志级别配置
- 可选的 CORS 跨域配置

### config.properties.example
包含项目自定义配置示例，包括：
- 代理设置
- NapCat 连接配置
- WebSocket 连接配置

## 安全提示

⚠️ **重要提示：**
1. **不要提交真实配置文件**到版本控制系统
2. **示例文件已添加到 .gitignore**，防止意外提交
3. 实际配置文件（`.yml` 和 `.properties`）中包含敏感信息
4. 使用环境变量或密钥管理工具处理敏感配置

## 验证配置

启动应用后，检查以下信息确认配置正确：

```bash
# 查看应用启动日志
# 应该看到类似的输出：
# - Successfully connected to database
# - WebSocket connected to NapCat
# - Application started on port 8090
```

## 常见问题

### Q: 如何更换数据库连接？
A: 修改 `application.yml` 中的 `spring.datasource` 配置。

### Q: 如何连接到远程 NapCat？
A: 
1. 设置 `NapCatApiBase` 为远程服务器地址
2. 设置 `WS_URL_SELECT=REMOTE`
3. 确保网络可以访问远程服务器

### Q: 配置修改后需要重启应用吗？
A: 是的，配置文件修改后需要重启 Spring Boot 应用才能生效。

### Q: 如何调试配置问题？
A: 
1. 检查应用启动日志
2. 增加日志级别：设置 `logging.level.LDS.Person: DEBUG`
3. 验证配置文件语法正确性

## 环境变量配置（可选）

你也可以使用环境变量覆盖配置文件中的设置：

```bash
# 设置数据库连接
export SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/ldsperson
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=password

# 设置 NapCat 配置
export NAPCATAPIBASE=http://localhost:3000
export NAPCATAUTHTOKEN=your_token

# 启动应用
java -jar app.jar
```

## 更多帮助

如有问题，请查看：
- Spring Boot 文档：https://spring.io/projects/spring-boot
- NapCat 文档：https://napneko.github.io/
- 项目文档：查看项目根目录的 README.md
