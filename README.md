# 高校一卡通挂失补卡服务

基于 Ktor 框架的高校校园一卡通挂失补卡后端服务，覆盖挂失申请、余额冻结、补卡制卡、解挂、消费争议处理等完整业务流程。

## 原始需求

> 高校需要一卡通挂失补卡服务，Ktor 接口承接挂失申请、余额冻结、补卡制卡、解挂和消费争议。业务内容包括学号、卡号、卡状态、余额、最近消费、挂失渠道、补卡费用、制卡批次、领取地点和异常终端。学生发现丢卡后提交挂失，服务冻结后续消费；卡务中心核验身份并制新卡；食堂终端同步黑名单；财务处理补卡费和异常消费退款。服务要把挂失、冻结、制卡、领取、旧卡作废和余额迁移处理清楚，不能把未同步终端消费、误挂失、找回旧卡和补卡失败混为一类。

## 技术栈

- **框架**: Ktor 2.3.7 (Netty)
- **语言**: Kotlin 1.9.22
- **数据库**: H2 (默认) / PostgreSQL (可选)
- **ORM**: Exposed 0.46.0
- **序列化**: Jackson
- **日志**: Logback + kotlin-logging
- **JDK**: 17+

## 核心业务模块

| 模块 | 说明 |
|------|------|
| 卡片管理 | 开卡、查询、状态流转、操作日志、黑名单 |
| 挂失管理 | 多渠道挂失申请、误挂失取消、找回旧卡解挂 |
| 补卡制卡 | 身份核验、补卡缴费、制卡批次、新卡领取、旧卡作废、余额迁移 |
| 消费记录 | 正常消费、未同步终端消费、黑名单消费、同步处理 |
| 消费争议 | 争议提交、审核、退款、异常终端区分 |

### 卡片状态流转

```
ACTIVE(正常) ──挂失──► LOST(已挂失) ──补卡──► REISSUED(已补卡)
     │                      │
     │                      ├─误挂失──► ACTIVE (CANCELLED_MISREPORT)
     │                      └─找回旧卡──► ACTIVE (CANCELLED_FOUND)
     │
     └─补卡直接挂失──► LOST ──制卡完成──► PICKED_UP
                                       旧卡 CANCELLED(作废)
                                       新卡 ACTIVE(激活)
```

## 启动方式

### 前置要求

- JDK 17 或更高版本
- Gradle 8.x（项目已内置 Gradle Wrapper）
- Docker / Docker Compose（可选，用于容器化启动）

### 方式一：Docker 一键启动（推荐）

#### 1. 启动服务

```bash
docker compose up --build
```

后台运行：

```bash
docker compose up --build -d
```

#### 2. 查看服务状态

```bash
docker compose ps
```

查看日志：

```bash
docker compose logs -f
```

#### 3. 停止和清理

```bash
docker compose down
```

如需同时删除数据卷：

```bash
docker compose down -v
```

**访问地址**: http://localhost:8080

健康检查: http://localhost:8080/api/health

### 方式二：本地源码启动

#### 1. 安装依赖

```bash
./gradlew dependencies
```

（Windows 环境使用 `gradlew.bat`）

#### 2. 启动服务

```bash
./gradlew run
```

或先构建再运行：

```bash
./gradlew buildFatJar
java -jar build/libs/campus-card-service-1.0.0-fat.jar
```

**访问地址**: http://localhost:8080

#### 环境变量配置（可选）

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| PORT | 服务端口 | 8080 |
| DATABASE_URL | JDBC 数据库连接 URL | jdbc:h2:file:./data/campus_card |
| DATABASE_DRIVER | 数据库驱动类 | org.h2.Driver |
| DATABASE_USER | 数据库用户名 | sa |
| DATABASE_PASSWORD | 数据库密码 | (空) |

使用 PostgreSQL 示例：

```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/campus_card
export DATABASE_DRIVER=org.postgresql.Driver
export DATABASE_USER=postgres
export DATABASE_PASSWORD=your_password
./gradlew run
```

## API 接口列表

### 健康检查

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/health` | 服务健康检查 |

### 卡片管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/cards` | 开卡 |
| GET | `/api/cards/{cardNo}` | 查询卡片信息 |
| GET | `/api/cards?status=&studentId=` | 列表查询 |
| GET | `/api/cards/student/{studentId}` | 按学号查卡 |
| GET | `/api/cards/{cardNo}/operations` | 卡片操作日志 |
| GET | `/api/cards/{cardNo}/recent-consumptions` | 最近消费记录 |
| GET | `/api/blacklist` | 获取终端黑名单 |

### 挂失管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/lost-reports` | 提交挂失申请 |
| GET | `/api/lost-reports/{id}` | 查询挂失单 |
| GET | `/api/lost-reports?status=&studentId=&cardNo=` | 列表查询 |
| POST | `/api/lost-reports/{id}/cancel` | 取消挂失（误挂失/找回旧卡） |

### 补卡制卡

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/reissues` | 申请补卡 |
| GET | `/api/reissues/{id}` | 查询补卡单 |
| GET | `/api/reissues?status=&studentId=&batchId=` | 列表查询 |
| POST | `/api/reissues/{id}/verify-identity` | 身份核验 |
| POST | `/api/reissues/{id}/pay-fee` | 缴纳补卡费 |
| POST | `/api/reissues/{id}/assign-batch` | 分配制卡批次 |
| POST | `/api/reissues/{id}/mark-ready` | 标记制卡完成 |
| POST | `/api/reissues/{id}/pickup` | 领取新卡（激活+余额迁移+旧卡作废） |
| POST | `/api/reissues/{id}/fail` | 标记补卡失败 |
| POST | `/api/batches` | 创建制卡批次 |
| GET | `/api/batches` | 查询制卡批次列表 |
| GET | `/api/batches/{id}` | 查询制卡批次 |

### 消费记录

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/consumptions` | 记录消费 |
| GET | `/api/consumptions/{id}` | 查询消费记录 |
| GET | `/api/consumptions?cardNo=&studentId=&isUnsynced=&status=&terminalId=` | 列表查询 |
| POST | `/api/consumptions/sync` | 批量同步未同步消费 |
| GET | `/api/consumptions/card/{cardNo}/recent` | 查询卡片最近消费 |

### 消费争议

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/disputes` | 提交消费争议 |
| GET | `/api/disputes/{id}` | 查询争议单 |
| GET | `/api/disputes?status=&studentId=&disputeType=&cardNo=` | 列表查询 |
| POST | `/api/disputes/{id}/review` | 审核争议（批准退款/拒绝） |

## 接口使用示例

### 1. 开卡

```bash
curl -X POST http://localhost:8080/api/cards \
  -H "Content-Type: application/json" \
  -d '{
    "cardNo": "2024001001",
    "studentId": "2024001",
    "studentName": "张三",
    "initialBalance": 500.00
  }'
```

### 2. 提交挂失

```bash
curl -X POST http://localhost:8080/api/lost-reports \
  -H "Content-Type: application/json" \
  -d '{
    "cardNo": "2024001001",
    "studentId": "2024001",
    "channel": "MOBILE_APP",
    "reporterContact": "13800138000",
    "remark": "食堂就餐时丢失"
  }'
```

### 3. 找回旧卡解挂

```bash
curl -X POST http://localhost:8080/api/lost-reports/1/cancel \
  -H "Content-Type: application/json" \
  -d '{
    "cancelReason": "在书包夹层找到旧卡",
    "isMisreport": false,
    "operatorId": "admin001"
  }'
```

### 4. 申请补卡（完整流程）

#### 4.1 申请补卡

```bash
curl -X POST http://localhost:8080/api/reissues \
  -H "Content-Type: application/json" \
  -d '{
    "lostReportId": 1,
    "oldCardNo": "2024001001",
    "studentId": "2024001",
    "pickupLocation": "卡务中心1号窗口"
  }'
```

#### 4.2 身份核验

```bash
curl -X POST http://localhost:8080/api/reissues/1/verify-identity \
  -H "Content-Type: application/json" \
  -d '{
    "verifierId": "staff001",
    "verified": true,
    "note": "已核验学生证和身份证"
  }'
```

#### 4.3 缴费

```bash
curl -X POST http://localhost:8080/api/reissues/1/pay-fee \
  -H "Content-Type: application/json" \
  -d '{ "paidAmount": 20.00 }'
```

#### 4.4 创建制卡批次并分配

```bash
curl -X POST http://localhost:8080/api/batches \
  -H "Content-Type: application/json" \
  -d '{ "batchNo": "BATCH20240601", "operatorId": "staff001" }'

curl -X POST http://localhost:8080/api/reissues/1/assign-batch \
  -H "Content-Type: application/json" \
  -d '{ "batchId": 1 }'
```

#### 4.5 标记制卡完成

```bash
curl -X POST http://localhost:8080/api/reissues/1/mark-ready \
  -H "Content-Type: application/json" \
  -d '{ "newCardNo": "2024001002" }'
```

#### 4.6 领取新卡（自动完成余额迁移+旧卡作废）

```bash
curl -X POST http://localhost:8080/api/reissues/1/pickup \
  -H "Content-Type: application/json" \
  -d '{ "receiverId": "staff001" }'
```

### 5. 提交消费争议（未同步终端消费/异常退款）

```bash
curl -X POST http://localhost:8080/api/disputes \
  -H "Content-Type: application/json" \
  -d '{
    "cardNo": "2024001001",
    "studentId": "2024001",
    "disputeType": "UNSYNCED_TERMINAL_CONSUMPTION",
    "disputedAmount": 25.50,
    "description": "挂失后食堂终端未同步黑名单，被盗刷25.5元",
    "terminalId": "CANTEEN_02_TERM_05",
    "evidence": "挂失时间截图和消费时间对比"
  }'
```

### 6. 获取终端黑名单（食堂终端同步用）

```bash
curl http://localhost:8080/api/blacklist
```

## 目录结构

```
wmy-29/
├── src/
│   └── main/
│       ├── kotlin/com/campus/card/
│       │   ├── Application.kt              # 应用入口
│       │   ├── config/
│       │   │   └── DatabaseFactory.kt      # 数据库初始化
│       │   ├── dto/
│       │   │   ├── Requests.kt             # 请求 DTO
│       │   │   └── Responses.kt            # 响应 DTO
│       │   ├── exception/
│       │   │   └── ServiceException.kt     # 业务异常
│       │   ├── model/
│       │   │   ├── Enums.kt                # 枚举定义
│       │   │   └── Tables.kt               # 数据库表定义
│       │   ├── routing/
│       │   │   ├── CardRoutes.kt           # 卡片路由
│       │   │   ├── ConsumptionRoutes.kt    # 消费路由
│       │   │   ├── DisputeRoutes.kt        # 争议路由
│       │   │   ├── LostReportRoutes.kt     # 挂失路由
│       │   │   └── ReissueRoutes.kt        # 补卡路由
│       │   └── service/
│       │       ├── CardService.kt          # 卡片服务
│       │       ├── ConsumptionService.kt   # 消费服务
│       │       ├── DisputeService.kt       # 争议服务
│       │       ├── LostReportService.kt    # 挂失服务
│       │       └── ReissueService.kt       # 补卡服务
│       └── resources/
│           ├── application.conf            # 应用配置
│           └── logback.xml                 # 日志配置
├── Dockerfile
├── docker-compose.yml
├── .dockerignore
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

## 注意事项

1. 补卡领取时系统自动完成三件事：旧卡作废、新卡激活、余额迁移
2. 挂失取消区分 `CANCELLED_MISREPORT`（误挂失）和 `CANCELLED_FOUND`（找回旧卡），不可混为一谈
3. 未同步终端消费（`isUnsynced=true`）在同步时若检测到卡片已挂失，会标记为 `PENDING_BLACKLIST` → `BLOCKED`
4. 消费争议类型严格区分：`UNSYNCED_TERMINAL_CONSUMPTION`、`FRAUDULENT_CHARGE`、`SYSTEM_ERROR`、`DOUBLE_CHARGE`、`WRONG_AMOUNT`
5. 补卡失败有独立状态 `FAILED`，与正常完成状态分开处理
