# campus-gold-checkin · 校园“点亮金牌”打卡系统

面向中学晨读 / 晚读的**班级大屏打卡系统**。每个班级教室的一体机打开一个免登录的班级链接，学生点击自己的圆牌即完成打卡，圆牌“点亮”为金牌；教职工在后台查看出勤与战力榜、导出 Excel 报表、修正打卡数据，管理员负责导入花名册和配置规则。

- 后端：Spring Boot 2.7.18 · Java 8 · MyBatis-Plus 3.5.5 · MySQL · EasyExcel 3.3.4 · JWT (jjwt 0.9.1) · Lombok
- 前端：原生 HTML / CSS / JavaScript（无框架、无需构建），由 Spring Boot 以静态资源形式直接提供

---

## 目录

- [功能特性](#功能特性)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [使用流程](#使用流程)
- [业务规则](#业务规则)
- [角色与权限](#角色与权限)
- [API 概览](#api-概览)
- [数据库](#数据库)
- [项目结构](#项目结构)
- [部署与更新](#部署与更新)
- [常见问题](#常见问题)
- [开发约定](#开发约定)

---

## 功能特性

### 班级打卡页（大屏 / 一体机）
- 免登录访问：`/checkin.html?code=<班级特征码>`，每个班级一个链接
- **单击**圆牌打卡，变为金牌并显示打卡时间；**双击**金牌撤销本时段打卡
- 只在本年级的打卡时间段内允许操作，其余时间只能浏览，点击会给出提示
- 右侧“班内战力榜”可切换**总榜 / 周榜 / 月榜**，显示段位徽章，自动滚动，可折叠
- 顶部显示当前教学周、早 / 晚两套“每日金句”（按天轮换），以及本年级榜首（姓名打码）
- 每 5 秒自动刷新，多台设备同时操作也能同步
- 按屏幕可视高度自动缩放，适配系统缩放为 150% / 200% 的一体机

### 管理后台（`/admin/login.html`）
- **概览**：当前账号可见的班级数、学生数
- **战力榜查询**：班级出勤率榜（按日期 + 早 / 晚），学生质量分榜（分页，可下钻到班级）
- **报表导出**：日报 / 周报 / 月报 / 学期总报表（Excel，带累计统计和分日 / 分周明细，未打卡的也列出）
- **打卡数据修正**：按日期、时段、班级补卡或撤销
- **花名册上传**：学生花名册、教职工账号，支持先预览再导入，错误行可导出
- **账号管理**：修改角色和绑定关系，重置密码
- **学生打卡序号绑定**：自定义圆牌上显示的序号
- **系统设置**：开学日期、分年级打卡时间段与返校日、打卡调试开关、早 / 晚金句、段位规则
- **班级免登录链接**：一键导出全部班级链接（Excel），或下载 Windows `.url` 快捷方式压缩包

---

## 快速开始

### 1. 环境要求

| 组件 | 版本 |
|---|---|
| JDK | 1.8（代码按 Java 8 编写） |
| Maven | 3.6+ |
| MySQL | 5.6+ / 8.x（字符集 utf8） |
| 浏览器 | Chrome / Edge 等现代浏览器（打卡页依赖 Pointer Events） |

### 2. 初始化数据库

> ⚠️ `init.sql` 会 **DROP 并重建**全部表，只能在全新环境中执行。

```bash
mysql -uroot -p < src/main/resources/sql/init.sql
```

然后**补充学生序号列**（`init.sql` 目前还没有这一列，但代码会用到）：

```sql
USE checkin_system;
ALTER TABLE student_info
    ADD COLUMN display_order_no INT DEFAULT NULL COMMENT '打卡页面显示序号' AFTER student_name,
    ADD KEY idx_student_class_order (class_id, display_order_no);
```

### 3. 配置数据库账号与密钥

```bash
# Windows
copy src\main\resources\application-local.yml.example src\main\resources\application-local.yml
# macOS / Linux
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

编辑 `application-local.yml`，填入真实的数据库地址、用户名、密码和一个足够长的随机 JWT 密钥。该文件已被 `.gitignore` 忽略，不会提交到仓库。详见[配置说明](#配置说明)。

### 4. 启动

```bash
mvn spring-boot:run
```

或者打包后运行：

```bash
mvn -DskipTests package
java -jar target/checkin-system-1.0.0.jar
```

也可以在 IDEA 中直接运行 `com.school.checkin.CheckInApplication`。

### 5. 访问

| 页面 | 地址 |
|---|---|
| 管理后台 | http://localhost:8099/admin/login.html |
| 班级打卡页 | http://localhost:8099/checkin.html?code=班级特征码 |

默认管理员账号为 `admin`，**首次登录必须修改密码**。初始密码以 MD5 摘要形式存放在 `init.sql` 中，如果不知道原密码，可以直接在数据库里重置：

```sql
-- 将 admin 密码重置为 newpass（MD5），登录后会要求再次修改
UPDATE sys_user SET password = MD5('newpass'), must_change_password = 1 WHERE username = 'admin';
```

---

## 配置说明

配置分成两层：

| 文件 | 是否提交 | 内容 |
|---|---|---|
| `src/main/resources/application.yml` | ✅ 提交 | 端口、上传大小、MyBatis、日志等公共配置；敏感项只放占位值 |
| `src/main/resources/application-local.yml` | ❌ 不提交 | 真实的数据库账号密码、JWT 密钥 |
| `src/main/resources/application-local.yml.example` | ✅ 提交 | `application-local.yml` 的模板 |

`application.yml` 通过 `spring.config.import: optional:classpath:application-local.yml` 自动加载本地文件，本地文件中的值会覆盖同名配置。也可以不写本地文件，改用环境变量：

| 环境变量 | 对应配置 | 默认值（仅占位） |
|---|---|---|
| `DB_URL` | `spring.datasource.url` | `jdbc:mysql://127.0.0.1:3306/checkin_system?...` |
| `DB_USERNAME` | `spring.datasource.username` | `root` |
| `DB_PASSWORD` | `spring.datasource.password` | `change-me` |
| `JWT_SECRET` | `jwt.secret` | `change-me-to-a-long-random-string` |

其他配置项：

| 配置 | 默认值 | 说明 |
|---|---|---|
| `server.port` | `8099` | 服务端口，班级链接里也会使用这个端口 |
| `jwt.expire-hours` | `12` | 后台登录有效期（小时） |
| `spring.servlet.multipart.max-file-size` | `20MB` | 花名册上传大小上限 |
| `checkin.time-windows.*` | — | **已不再生效**，打卡时间段改为在后台配置（存于 `sys_setting` 表） |

> 修改 JWT 密钥会让所有已登录的后台会话失效，需要重新登录。

---

## 使用流程

一个新学期的典型操作顺序：

1. **管理员登录后台**，修改初始密码
2. **系统设置 → 开学日期**：设置本学期第一天，教学周和学期总榜都从这天算起
3. **系统设置 → 晚间打卡与返校日规则**：按年级设置早上、平时晚上、返校日晚上的打卡窗口
4. **花名册上传 → 学生花名册**：下载模板 → 填写 → 预览 → 上传。不存在的班级会自动创建，并生成班级特征码
5. **花名册上传 → 教职工账号**：导入班主任、级长、行政账号（默认密码 `dlzx`，首次登录需修改）
6. **学生打卡序号绑定**（可选）：调整圆牌上显示的序号
7. **班级免登录链接**：导出链接或下载快捷方式压缩包，把对应的 `.url` 放到各班一体机桌面
8. 日常使用：学生在大屏上打卡，班主任 / 级长 / 行政在后台查看与导出

换学期需要清空数据时，可以参考仓库外保存的清理 SQL：按 `check_in_record → student_info → sys_user（保留 admin）→ class_info` 的顺序删除（注意外键）。

---

## 业务规则

### 打卡时间段
每个年级一条规则，包含三个窗口（默认值）：

| 年级 | 早上 | 平时晚上 | 返校日晚上 |
|---|---|---|---|
| 七年级 | 06:00-07:10 | 21:30-21:50 | 19:00-19:20 |
| 八年级 | 06:00-07:10 | 21:30-21:50 | 19:20-19:40 |
| 九年级 | 06:00-07:10 | 21:30-21:50 | 19:40-20:00 |

- **返校日**默认为每周日，可以为某一周单独指定日期（例如调休）
- 每位学生每天每个时段（早 / 晚）只能打卡一次
- **打卡调试开关**：开启后任何时间都允许打卡，只用于测试，用完务必关闭
- 后台补卡如果不填时间，默认记为该时段窗口的开始时间
- 后台修改配置后，最多约 **10 秒**生效（有配置缓存）

### 圆牌序号
优先使用后台绑定的“打卡序号”；没有绑定时，取学号中数字部分的**末两位**（例如学号 `20230107` 显示 `7`）。

### 战力榜排序
1. 累计打卡次数（降序）
2. 质量分（降序）
3. 学号、学生 ID

统计范围：**总榜**从开学日开始，**周榜**从本周一开始，**月榜**从本月 1 日开始（都不早于开学日）。周榜和月榜的段位徽章沿用总榜段位。

### 质量分
每次打卡按它在时间窗口中的位置线性计算 0-10 分：

- 早上、返校日晚上：**越早打卡分越高**
- 平时晚上：**越晚打卡分越高**

### 段位
后台“段位规则”支持两种模式：

- **按次数（COUNT，默认）**：按学期累计打卡次数划分，从“倔强青铜Ⅲ（0 次）”到“荣耀王者（165 次）”，王者段位之后每多打卡 1 次加 1 星
- **按百分比（PERCENT）**：按榜内名次的百分比分档（默认荣耀 5%、无双 5%、王者 10%、星耀 10%、钻石 15%、铂金 15%、黄金 15%、白银 15%、青铜 10%）

默认次数阈值：

| 段位 | 起始次数 |
|---|---|
| 倔强青铜 Ⅲ / Ⅱ / Ⅰ | 0 / 5 / 10 |
| 秩序白银 Ⅲ / Ⅱ / Ⅰ | 15 / 20 / 25 |
| 荣耀黄金 Ⅳ / Ⅲ / Ⅱ / Ⅰ | 32 / 39 / 46 / 53 |
| 尊贵铂金 Ⅳ / Ⅲ / Ⅱ / Ⅰ | 61 / 69 / 77 / 85 |
| 永恒钻石 Ⅴ / Ⅳ / Ⅲ / Ⅱ / Ⅰ | 94 / 103 / 112 / 121 / 130 |
| 至尊星耀 Ⅴ / Ⅳ / Ⅲ / Ⅱ / Ⅰ | 136 / 141 / 146 / 150 / 152 |
| 最强王者（每多 1 次 +1 星） | 153 |
| 无双王者（每多 1 次 +1 星） | 159 |
| 荣耀王者（每多 1 次 +1 星） | 165 |

### 花名册模板
Excel 第 1-3 行为标题说明，**第 4 行为表头，第 5 行起为数据**。请直接使用后台下载的模板。

| 模板 | 列 |
|---|---|
| 学生花名册 | 年级 · 班号 · 学号 · 学生姓名 |
| 教职工账号 | 姓名 · 登录账号 · 角色 · 年级 · 班号 · 初始密码 |

- 班号可以写 `1` 或 `1班`，系统会统一补成 `1班`
- 学生按“班级 + 学号”匹配已有记录，没有学号时按“班级 + 姓名”匹配
- 角色可填 `班主任` / `级长`（或 `年级主任`）/ `行政`，也可填 `TEACHER` / `GRADE_LEADER` / `STAFF`；不能导入管理员
- 重复导入已有账号会**重置其密码**并要求重新修改

### 报表导出
| 类型 | 参数 | 内容 |
|---|---|---|
| 日报 | 日期 | 当日明细（一个 sheet） |
| 周报 | 第几教学周 | 累计统计 + 每日明细 |
| 月报 | `yyyy-MM` | 累计统计 + 每周明细 |
| 学期总报表 | — | 从开学日到今天的累计统计 + 明细 |

明细中每位学生每天都有早、晚两行，未打卡的标为“未打卡”。导出范围跟随当前账号的数据权限。

---

## 角色与权限

| 角色 | 编码 | 数据范围 | 可用功能 |
|---|---|---|---|
| 管理员 | `ADMIN` | 全校 | 全部功能，包括导入、账号、系统设置、链接导出、学生质量分榜 |
| 行政 | `STAFF` | 全校 | 查看榜单、导出报表、修正数据、修改自己的密码 |
| 级长 | `GRADE_LEADER` | 绑定的年级 | 同上，范围限本年级 |
| 班主任 | `TEACHER` | 绑定的班级 | 同上，范围限本班 |

- 后台使用 JWT 鉴权（`Authorization: Bearer <token>`），默认 12 小时过期
- 新账号和被重置的账号首次登录必须先修改密码，否则接口会返回 403

---

## API 概览

所有接口返回统一结构 `{"code": 200, "message": "success", "data": ...}`。业务错误同样以 HTTP 200 返回，通过 `code` 区分（400 / 401 / 403 / 404 / 409 / 500）。

### 公开接口 `/api/public`（免登录）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/roster?uniqueCode=` | 班级打卡页数据：学生、当日状态、三种战力榜、金句、榜首等 |
| GET | `/leaderboard?date=&timeSlot=` | 全校班级出勤榜 |
| POST | `/check-in` | 打卡 `{uniqueCode, studentId}` |
| POST | `/check-in/revoke` | 撤销打卡 `{uniqueCode, studentId}` |

### 数据接口 `/api/data`（需登录，按角色限定范围）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/overview` | 概览统计 |
| GET | `/classes` | 可见班级列表 |
| GET | `/leaderboard?date=&timeSlot=` | 班级出勤率榜 |
| GET | `/leaderboard/student-quality?classId=` | 学生质量分榜（仅管理员） |
| GET | `/correction/records?date=&timeSlot=&classId=` | 修正页数据 |
| POST | `/correction/check-in` | 补卡 |
| POST | `/correction/revoke` | 撤销 |
| GET | `/export?scope=daily\|weekly\|monthly\|semester&date=&weekIndex=&month=` | 导出 Excel |

### 管理接口 `/api/admin`（需登录，除标注外仅管理员）
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/login` | 登录（免登录） |
| POST | `/password/change` | 修改自己的密码（所有角色） |
| GET | `/semester-start` | 读取开学日期（所有角色） |
| POST | `/semester-start` | 设置开学日期 |
| GET / POST | `/daily-quotes` | 早 / 晚金句 |
| GET / POST | `/evening-checkin-settings` | 分年级打卡时间段与返校日 |
| GET / POST | `/rank-settings` | 段位规则 |
| GET / POST | `/checkin-debug` | 打卡调试开关 |
| GET | `/users` · POST `/users/update` · POST `/users/reset-password` | 账号管理 |
| POST | `/roster/students/preview` · `/roster/students/upload` | 学生花名册预览 / 导入 |
| POST | `/roster/users/preview` · `/roster/users/upload` | 教职工账号预览 / 导入 |
| GET | `/roster/students/template` · `/roster/users/template` | 下载模板 |
| POST | `/roster/errors/export` | 导出导入错误行 |
| GET / POST | `/student-order-bindings` | 学生打卡序号绑定 |
| GET | `/classes` | 班级与特征码列表 |
| GET | `/access-base-url` | 推荐的访问地址（本机局域网 IP，所有角色） |
| GET | `/classes/export?baseUrl=` · `/classes/zip?baseUrl=` | 导出班级链接 Excel / 快捷方式 zip |

---

## 数据库

库名 `checkin_system`，共 5 张表：

| 表 | 说明 | 关键字段 |
|---|---|---|
| `class_info` | 班级 | `grade_name`、`class_name`、`unique_code`（12 位班级特征码）、`teacher_name`、`student_count` |
| `student_info` | 学生 | `class_id`、`student_no`、`student_name`、`display_order_no`（打卡序号） |
| `sys_user` | 教职工账号 | `username`、`password`（MD5）、`role_code`、`grade_name`、`class_id`、`must_change_password` |
| `check_in_record` | 打卡记录 | `student_id`、`checkin_date`、`time_slot`（MORNING/EVENING）、`checkin_time`；唯一键（学生, 日期, 时段） |
| `sys_setting` | 键值配置 | `setting_key`、`setting_value` |

`sys_setting` 中的主要配置键：

| 键 | 说明 |
|---|---|
| `semester_start_date` | 开学日期 |
| `daily_quotes_morning` / `daily_quotes_evening` | 早 / 晚金句（每行一句），配有 `_start_index`、`_start_date` 控制轮换 |
| `evening_checkin_settings` | 返校日与分年级打卡时间段（`rule.N=年级\|早始\|早止\|晚始\|晚止\|返校始\|返校止`） |
| `rank_rule_settings` | 段位规则（`key=value` 多行） |
| `checkin_debug_enabled` | 打卡调试开关，`1` 开 / `0` 关 |

---

## 项目结构

```
campus-gold-checkin
├── pom.xml
├── README.md
├── AGENTS.md / CLAUDE.md            # 给 AI 编码助手的项目说明
└── src/main
    ├── java/com/school/checkin
    │   ├── CheckInApplication.java  # 启动类
    │   ├── controller/              # PublicApi / Admin / DataQuery 三组接口
    │   ├── service/impl/            # 业务实现
    │   ├── mapper/                  # MyBatis-Plus Mapper（复杂 SQL 写在 @Select 中）
    │   ├── entity/                  # 5 张表对应的实体
    │   ├── dto/ · vo/               # 请求体 / 返回体与 Excel 行模型
    │   ├── utils/                   # 时间窗口、质量分、段位、排序、JWT 等工具
    │   ├── interceptor/ · security/ # JWT 拦截器与当前用户上下文
    │   ├── config/                  # Web MVC（拦截器、静态资源禁缓存）、MyBatis-Plus
    │   ├── excel/                   # 报表样式
    │   └── exception/ · common/ · enums/
    └── resources
        ├── application.yml
        ├── application-local.yml.example
        ├── sql/init.sql
        └── static
            ├── checkin.html         # 班级打卡页
            └── admin/               # 后台：login.html、index.html、app.js、styles.css
```

---

## 部署与更新

- 典型部署方式：学校内网的一台 Windows 机器运行本服务（IDEA 或 `java -jar`），各班一体机通过局域网地址 `http://<服务器IP>:8099/checkin.html?code=...` 访问
- 后台导出班级链接时，如果当前地址是 `localhost` / `127.0.0.1`，会自动替换为服务器的局域网 IPv4
- 服务器 IP 变化后，需要重新导出链接或快捷方式，并更新各班一体机
- 静态资源都设置了 `Cache-Control: no-store`，更新前端文件后刷新页面即可；Java 代码改动需要重新编译并重启
- 涉及表结构的更新，需要同时准备 `init.sql`（新环境）和增量 `ALTER` 语句（已有数据库）

---

## 常见问题

**打卡页提示“缺少班级特征码”**
链接必须带 `?code=<特征码>` 参数，请到后台“班级免登录链接”中复制完整链接。

**点击圆牌提示“当前不在打卡时间段”**
检查后台该年级的时间窗口和返校日设置；测试时可以临时打开“打卡调试开关”。

**改了后台配置，打卡页没有变化**
配置有约 10 秒缓存，稍等片刻；打卡页每 5 秒自动刷新一次。

**启动报 `Unknown column 'display_order_no'`**
没有执行[快速开始](#2-初始化数据库)中补充 `display_order_no` 列的 SQL。

**启动报数据库连接失败 / `Access denied`**
没有创建 `application-local.yml`，或其中的账号密码不对；也可以检查 `DB_*` 环境变量。

**一体机上页面显示不全**
页面会按可视高度自动缩放；如果仍有问题，请将浏览器全屏（F11），并确认系统缩放比例。

---

## 开发约定

- Java 8 语法（不要用 `var`、`List.of` 等），字符串判断用 `commons-lang3` 的 `StringUtils`
- 统一 4 空格缩进；Controller 只做参数接收与响应，业务逻辑放在 `service/impl`
- 业务校验失败抛出 `BusinessException(ErrorCode.X, "中文提示")`
- 前端不引入框架和构建工具；后台请求统一走 `app.js` 中的 `api()`
- 目前没有自动化测试，修改后请按 `AGENTS.md` 中的“测试与验证”清单手动验证
- **不要提交**真实学生数据、数据库导出和 `application-local.yml`
