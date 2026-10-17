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

页面使用的 51 张图片均已提交到 Git，位于 `src/main/resources/static/generated/`：

- `avatars/optimized/`：10 张波普风二次元头像。
- `landscapes/optimized/`：40 张摄影风格的风景图。
- `hero-optimized.jpg`：首页背景图。

图片由应用通过同源 `/generated/` 路径提供，并随 JAR 打包。迁移时拉取仓库、配置 `.env.local` 后启动即可，不需要访问图片生成服务或单独复制图片。数据库保留提示词和展示元数据；未压缩的生成源图不参与 Git，页面不依赖它们。

静态资源使用 `Cache-Control: no-cache`，允许缓存但每次使用前向服务器校验。前端脚本和样式带版本参数，更新它们时应同步调整 `index.html` 中的版本。如果浏览器曾缓存旧首页，可强制刷新，或首次访问时在首页 URL 后添加 `?v=74d8ddb`。

## ID 规则

所有业务 ID（用户、图片素材、作品、点赞、评论、关注、分享）均为 16 位字符串：1 位类型前缀 + 15 位小写 base36。
前缀依次为 `u`、`a`、`p`、`l`、`c`、`f`、`s`。V4 迁移把历史数字 ID 补零保留，例如用户 1 变为 `u000000000000001`；新数据由后端随机生成。
头像和风景图片按 ID 命名，例如 `avatar-u000000000000001.jpg`、`landscape-a000000000000001.jpg`。

## water-box 埋点

后端在事务提交后异步上报 water-box 协议事件（`system=backend`、`theme=community`），失败最多重试 3 次并沿用同一 `log_id`，不会影响业务请求。

| action | properties |
| --- | --- |
| `like` / `unlike` | `project_id`、`author_user_id` |
| `comment` | `project_id`、`comment_id`、`author_user_id` |
| `delete_comment` | `project_id`、`comment_id`、`author_user_id` |
| `share` | `project_id`、`author_user_id` |
| `follow` | `project_id`、`target_user_id` |
| `view_page` | `project_id`、`author_user_id`（`system=front`，由 `POST /api/projects/{projectId}/views` 转发） |

上报 action 严格限定为 water-box 动作列表：`view_page`、`like`、`unlike`、`comment`、`delete_comment`、`share`、`follow`。取消关注和发布作品不上报。
关注接口为 `POST /api/users/{targetUserId}/follow?projectId=...`，作品必须属于被关注用户。所有 properties 值均为非空字符串。
评论可由评论者本人或作品作者删除（`DELETE /api/projects/{projectId}/comments/{commentId}`）。
`session_id` 来自前端 `X-Session-Id` 请求头（闲置 30 分钟后轮换），`pre_span_id` 来自前端 `X-Span-Id`，每个请求生成新的 `span_id` 并通过响应头回传。
配置项：`WATER_BOX_ENABLED`、`WATER_BOX_ENDPOINT`（默认 `http://127.0.0.1:8080/api/v1/events`）、`WATER_BOX_ENV`（`test` 或 `prod`）。

## 模拟规模

V5 迁移把社区扩容到 10,000 个用户和 20,000 个作品。新增用户 `selectable=false`，使用默认头像 `generated/avatars/default-avatar.svg`，名字唯一，不出现在「选取用户」中；新增作品循环使用已有风景图，名字唯一，会出现在社区信息流。
最新作品分页加载（`GET /api/projects?sort=latest&page=0&size=24`，`size` 最大 50），滚动到底部自动加载下一页。
`GET /api/simulation/catalog` 返回全部用户 ID 与昵称、作品与作者、已有点赞和关注关系，供 water-box 的 `tools/simulate_traffic.py` 生成符合真实数据的埋点。

## 热度排序

社区提供「热度排序」和「最新排序」。热度排序展示小时、天、三天、周四个窗口各自的 Top 5，默认小时榜；切换窗口使用已获取的数据。在热度页面每 30 秒调用一次 `GET /api/project-rankings`，一次返回四榜。

接口使用现有数据源，数据库账号需要对 `water_analytics.heat_score_rule` 和 `water_analytics.project_engagement_stats` 有 SELECT 权限。分析表由分析系统维护，不纳入社区 Flyway 迁移。

每次请求查询一次 `status=1` 的规则版本，再分别查询四个窗口，共 5 条 SQL。排名使用 `idx_project_rank_lookup` 索引，选择该版本下最新有数据的 `window_start`，按 `heat_score DESC, project_id ASC LIMIT 5` 排列；无 `NOW()` 限制，不要求窗口已结束，三天榜为固定三天窗口。规则不存在时返回四个空榜，分析库错误返回 503，均不回退为点赞数排名。

Top 5 选出后在同一条 SQL 中关联社区作品名、作者昵称及图片；无法关联的历史 ID 仍保留原排名并显示 ID。接口以精确小数将数据库整数热度除以 1000（例如 17300 → 17.3），同时返回五种互动计数及窗口起止时间。页面只读取 Flink 已计算的结果，不重算、不写回、不触发规则发布流程。原 `sort=popular` 接口返回 400，请改用 `/api/project-rankings`。
