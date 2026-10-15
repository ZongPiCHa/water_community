# Water Community

水集是一个用于模拟社区用户行为的 Spring Boot 项目。它内置 10 个演示用户、40 套固定视觉素材，以及作品发布、点赞、评论、分享、关注和个人数据页。

## 技术栈

- Java 17
- Spring Boot 4.1
- Spring MVC + Spring Data JPA
- MySQL 8 + Flyway
- 原生 HTML、CSS、JavaScript

## 本地启动

1. 从 `.env.example` 创建 `.env.local`，填写本地数据库连接信息。
2. 确保 MySQL 中存在 `water` 数据库。
3. 启动应用：

```bash
./run-local.sh
```

Flyway 会自动创建表结构并写入演示数据。端口由 `.env.local` 中的 `SERVER_PORT` 决定。

## 测试与构建

```bash
./mvnw test
./mvnw package
```

自动测试使用 H2 的 MySQL 兼容模式，不会修改远程数据库。

## 模拟用户

前端通过浏览器 `localStorage` 保存当前模拟用户，写操作通过 `X-User-Id` 请求头传递身份。该方式仅用于演示和压测数据构造，不应作为生产鉴权方案。

所有图片都由固定提示词映射到统一图片服务；数据库只保存提示词和展示元数据。
