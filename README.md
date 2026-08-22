# Sigmob 数据资产看板（第一期）

第一期用于接收 OSS/Hive 元数据全量快照，保存每次采集结果，并展示容量、表数量、分区数量、Bucket 分布、Top 表、负责人分布和容量增长趋势。

## 当前数据口径

最新 NDJSON 每行是一条资产记录，字段如下：

```json
{
  "bucket": "sigbakup-osshdfs",
  "db": "dsp_log",
  "table": "ad_material_log_compress",
  "partition": "ds=2024-09-01",
  "size_bytes": 5343868187775,
  "mod_time": "2024-09-02 00:20:00",
  "access_time": "",
  "owner": "flink",
  "scan_type": "table",
  "collect_host": "master-1-1.c-43285eced70fb121",
  "collect_time": "2026-08-22T17:47:43+08:00"
}
```

分区统计规则：

- `partition` 存在且非空：该行表示一个分区。
- `partition` 缺失、为 `null` 或空字符串：该行表示一张非分区表。
- 表的唯一标识是 `bucket + db + table`，分区的唯一标识在此基础上增加 `partition`。
- 一张分区表的当前容量是本次全量快照中该表所有分区 `size_bytes` 之和。
- 表数量按不同的 `bucket + db + table` 计数，不会把分区数量误当成表数量。
- 趋势以每次成功发布的全量快照为一个时间点；不能用同一快照中的分区日期替代历史容量趋势。

`access_time` 允许为空。当前支持的 `scan_type` 为 `table`、`user`、`tmp`、`trash`；表规模相关指标只统计 `table`。

## 技术结构

- 后端：Java 21、Spring Boot、Spring JDBC、Flyway、Kafka Consumer
- 数据库：PostgreSQL 17
- 前端：Vue 3、TypeScript、Element Plus、ECharts
- 登录：飞书 OAuth 2.0 Authorization Code + PKCE、服务端 Session
- 部署：Docker Compose + Nginx

## 一键启动

需要安装 Docker Desktop。在项目根目录执行：

```bash
docker compose up --build -d
```

打开 [http://localhost:8088](http://localhost:8088)。后端健康检查地址为 [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)。

默认不连接 Kafka，先通过页面右上角“导入快照”上传 `.ndjson` 文件即可。

停止服务：

```bash
docker compose down
```

PostgreSQL 数据保存在 Docker volume 中；`docker compose down` 不会删除已有快照。

## 飞书登录配置

系统已经接入飞书 OAuth v3 登录。默认 `FEISHU_AUTH_ENABLED=false`，便于本地开发；启用后，除 `/api/v1/auth/**` 外的数据接口都必须具有有效登录 Session。

1. 在[飞书开发者后台](https://open.feishu.cn/app)创建或选择一个企业自建应用。
2. 在 **开发配置 -> 安全设置 -> 重定向 URL** 中添加实际回调地址。
3. 发布应用，并在应用可用范围中加入允许访问平台的公司成员。
4. 复制环境变量模板并填写真实凭证：

```bash
cp .env.example .env
```

Docker 本地部署的回调地址为：

```text
http://localhost:8088/api/v1/auth/callback
```

本地 Vite 开发时应改为：

```text
http://localhost:5173/api/v1/auth/callback
```

启动并启用登录：

```bash
docker compose up --build -d
```

当前只读取登录所必需的基础身份信息（姓名、头像、`open_id`、`tenant_key`），不申请聊天、文档等业务权限，也不保存飞书 `user_access_token`。App Secret 只允许通过后端环境变量提供，不能放入前端代码或提交到仓库。

安全处理包括：随机 `state` 防止登录 CSRF、PKCE S256、登录成功后更换 Session ID、`HttpOnly` + `SameSite=Lax` Cookie。生产环境使用 HTTPS 时必须设置：

```bash
SESSION_COOKIE_SECURE=true
FEISHU_REDIRECT_URI=https://data.example.com/api/v1/auth/callback
FRONTEND_URI=https://data.example.com/
```

第一期 Session 保存在单个后端实例内存中，后端重启后用户需要重新登录。如果后续部署多个后端实例，应接入 Spring Session Redis。

## 本地开发

先启动数据库：

```bash
docker compose up -d postgres
```

启动后端：

```bash
cd backend
mvn spring-boot:run
```

启动前端：

```bash
cd frontend
npm install
npm run dev
```

开发页面为 [http://localhost:5173](http://localhost:5173)。Vite 会将 `/api` 请求代理到 `http://localhost:8080`。

也可以在后端启动时自动导入一个文件：

```bash
APP_IMPORT_FILE=/absolute/path/ossdata.ndjson KAFKA_ENABLED=false mvn spring-boot:run
```

相同文件按 SHA-256 校验和幂等处理，重复上传不会生成重复快照。

## Kafka 接入

公司内网环境中可使用以下配置启动：

```bash
KAFKA_ENABLED=true \
KAFKA_BOOTSTRAP_SERVERS=10.26.18.72:9092,10.26.18.73:9092,10.26.18.74:9092 \
KAFKA_TOPIC=oss_data \
KAFKA_GROUP_ID=data-platform-metadata \
KAFKA_SECURITY_PROTOCOL=PLAINTEXT \
docker compose up --build -d
```

当前消费者按“一条 Kafka 消息对应一条 JSON 记录”解析。由于上游没有快照开始、结束或删除事件，系统把连续消息写入同一批次；默认连续 30 秒没有新消息后，将该批次发布为一个完整快照。可通过 `KAFKA_BATCH_QUIET_PERIOD` 调整，例如 `PT2M`。

上线前需要和上游确认两点：

1. 一条 Kafka 消息是否确实只包含一条 JSON 记录。
2. 两次全量推送之间的间隔是否一定大于静默窗口。

如果上游能增加 `snapshot_id` 和结束标记，应优先改用显式批次边界，可靠性会高于静默窗口推断。

## 文件导入 API

```bash
curl -F "file=@/absolute/path/ossdata.ndjson" \
  http://localhost:8080/api/v1/imports/ndjson
```

常用查询接口：

- `GET /api/v1/auth/me`：当前登录状态和用户信息
- `GET /api/v1/auth/login`：跳转飞书授权页
- `GET /api/v1/auth/callback`：飞书 OAuth 回调
- `POST /api/v1/auth/logout`：退出本平台登录
- `GET /api/v1/dashboard/summary`：总览指标
- `GET /api/v1/dashboard/trend`：快照容量趋势
- `GET /api/v1/dashboard/buckets`：Bucket 分布
- `GET /api/v1/dashboard/top-tables`：大表排行
- `GET /api/v1/assets`：按表聚合的资产清单
- `GET /api/v1/partitions`：指定表的分区明细
- `GET /api/v1/snapshots`：全量快照历史

## 验证

```bash
cd backend && mvn test
cd frontend && npm run build
docker compose config
```

后端测试覆盖分区表聚合、非分区表识别以及重复文件幂等导入。
