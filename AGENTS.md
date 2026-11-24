# Repository Guidelines

> 本文档依据 2026-11-24 对源码的通读整理。代码与文档冲突时以代码为准，并顺手更新本文档。

## 项目概览
校园“点亮金牌”打卡系统（英文名 / GitHub 仓库名：`campus-gold-checkin`）：学校晨读/晚读到位打卡。每个班级教室的一体机/大屏打开一个**免登录班级链接**，学生点击自己的圆牌即完成打卡（变为金牌）；教职工通过后台查看战力榜、导出报表、修正数据，管理员负责导入花名册和系统配置。

技术栈：**Spring Boot 2.7.18 + Java 8 + MyBatis-Plus 3.5.5 + MySQL + EasyExcel 3.3.4 + jjwt 0.9.1 + Lombok**，前端为 `static/` 下的原生 HTML/CSS/JS（无构建工具、无框架）。

## 目录结构（哪些是“真”代码）
- `src/main/java/com/school/checkin/`：后端源码，**唯一的真实来源**。
  - `controller/`：`PublicApiController`（`/api/public`，免登录）、`AdminController`（`/api/admin`）、`DataQueryController`（`/api/data`）。
  - `service/impl/`：`CheckInServiceImpl`（打卡页数据、打卡/撤销、班内榜）、`AdminServiceImpl`（登录、导入、配置、账号、序号绑定、链接导出）、`DataQueryServiceImpl`（后台榜单、报表、补卡）。
  - `utils/`：`CheckInTimeUtil`（时间窗口、质量分）、`EveningCheckinSettingUtil`（分年级时段与返校日规则序列化）、`RankRuleUtil`（段位规则）、`ClassSortUtil`（年级/班级自然排序）、`JwtUtil`、`PasswordUtil`（MD5）、`NetworkUtil`（取本机局域网 IP）、`UniqueCodeGenerator`。
  - `interceptor/JwtAuthInterceptor` + `security/UserContext`（ThreadLocal 当前用户）。
  - `entity/` 5 张表；`dto/` 请求体；`vo/` 返回体与 Excel 行模型；`excel/ExcelStyleFactory` 报表样式。
- `src/main/resources/static/checkin.html`：**真正的班级打卡页**（约 825 行，调用后端 API）。
- `src/main/resources/static/admin/`：后台 `login.html`、`index.html`、`app.js`（约 1700 行）、`styles.css`。
- `src/main/resources/sql/init.sql`：建库建表 + 默认配置 + 默认 admin 账号（CRLF 换行）。
- `src/main/resources/application.yml`：端口 `8099` 等公共配置；数据库与 JWT 密钥只放占位值（`${DB_PASSWORD:change-me}` 等）。
- `src/main/resources/application-local.yml`：**真实**数据库账号密码和 JWT 密钥，由 `spring.config.import` 自动加载并覆盖 `application.yml`，已被 `.gitignore` 忽略，**不要提交**；模板见 `application-local.yml.example`。也可用环境变量 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET`。
- Git 仓库：https://github.com/tuyaosheng/campus-gold-checkin （`.gitignore` 已排除历史补丁包、zip、真实数据导出等）。

以下是**非源码/历史产物**，修改功能时不要去改它们：
- 根目录 `checkin.html`：最早的纯静态原型（硬编码 `studentNames`、长按 3 秒恢复），已不再使用。
- `补丁/*/`、`段位修复改动文件/`、各种 `*.zip`：历次交付给正式机的“改动文件包”，内容是按相同相对路径覆盖的文件副本 + `说明.txt`。`src/` 已包含其中最新的改动（最新一次为 `补丁/周榜月榜总榜切换改动文件`），较早的包与 `src/` 有差异属正常。
- `db.sql`：较早的 SQLyog 结构导出（无 `display_order_no` 列）；`段位修复改动文件/.../0331db.sql`：2026-03-31 正式库导出，**含真实学生姓名**。
- `班级免登录打卡链接快捷方式/`：后台“下载快捷方式 zip”的产物（`.url` 文件，指向 `http://<局域网IP>:8099/checkin.html?code=<特征码>`）。
- `数据库清理语句.txt`（换学期清空数据用）、`段位规则说明.txt`、`last_message.txt`（无关）。
- 没有 `src/test`。

## 构建与运行
```powershell
# 1. 初始化数据库（会 DROP 重建全部表！只用于全新环境）
mysql -uroot -p < src/main/resources/sql/init.sql
# 2. 准备私有配置（首次）
copy src\main\resources\application-local.yml.example src\main\resources\application-local.yml
# 3. 启动（正式机上实际是在 IDEA 中直接运行 CheckInApplication）
mvn spring-boot:run
# 或打包
mvn -DskipTests package
```
- 打卡页：`http://localhost:8099/checkin.html?code=<班级特征码>`（也接受 `?uniqueCode=`）。
- 后台：`http://localhost:8099/admin/login.html`，默认账号 `admin`，首次登录强制改密。
- 静态资源统一 `Cache-Control: no-store`（`WebMvcConfig`），改完前端刷新即可生效，但后端 Java 改动需重启。

## 已知坑（改代码前务必知道）
- **`init.sql` 缺少 `student_info.display_order_no` 列**，但实体 `StudentInfo` 和 `StudentInfoMapper` 都在用它，全新环境直接跑 `init.sql` 后查询学生会报 Unknown column。正式库已有该列，需补：
  ```sql
  ALTER TABLE student_info
      ADD COLUMN display_order_no INT DEFAULT NULL COMMENT '打卡页面显示序号' AFTER student_name,
      ADD KEY idx_student_class_order (class_id, display_order_no);
  ```
- **`application.yml` 中 `checkin.time-windows.*` 实际不生效**：真正的时间窗口来自 `sys_setting.evening_checkin_settings`（后台“晚间打卡与返校日规则”页配置）。`morning-start/end` 虽被注入 `CheckInTimeUtil` 但未被使用，`evening-*` 完全未读取。
- **配置缓存 10 秒**：`CheckInTimeUtil`、`CheckInServiceImpl`、`DataQueryServiceImpl` 各自缓存 `sys_setting`，后台改配置后最多 10 秒才对打卡页生效。
- **段位规则前端有硬编码兜底**：`checkin.html` 和 `admin/app.js` 中的 `resolveRankTier()` 写死了默认次数阈值，只在后端未返回 `rankTitle` 时使用；修改默认段位时要同步这两处 + `RankRuleUtil.defaultSettings()` + `init.sql`。
- **错误返回 HTTP 200**：`GlobalExceptionHandler` 把 `BusinessException` 包成 `{code, message, data}` 并以 200 返回，前端靠 `json.code !== 200` 判错；只有拦截器“首次登录须改密”返回真正的 HTTP 403。
- **Java 8**：不能用 `var`、`List.of`、`String.isBlank()`、文本块等新语法，用 `commons-lang3` 的 `StringUtils`。
- 路径含中文和全角引号（`校园“点亮金牌”打卡系统`），Shell 中注意加引号。

## 核心业务规则
### 角色与数据范围（`RoleCode`）
| 角色 | 说明 | 数据范围 |
|---|---|---|
| `ADMIN` | 管理员 | 全校 + 全部配置；后台 `.admin-only` 菜单仅其可见；`AdminServiceImpl.requireAdmin()` 校验 |
| `STAFF` | 行政 | 全校查看/导出/修正，无配置权限 |
| `GRADE_LEADER` | 级长 | 绑定 `grade_name`，仅本年级 |
| `TEACHER` | 班主任 | 绑定 `class_id`，仅本班 |
- 范围过滤在 `DataQueryServiceImpl`（`resolveScopedClassId` / `resolveScopedGradeName` / `assertClassPermission`）。
- **学生质量分榜**（`/api/data/leaderboard/student-quality`）仅 ADMIN 可查。
- JWT 放 `Authorization: Bearer`，有效期 12 小时；前端存于 `localStorage["checkin_admin_auth"]`。
- 密码为无盐 MD5；导入或重置的默认密码为 `dlzx`（`AdminServiceImpl.DEFAULT_PASSWORD`），新账号 `must_change_password=1`。

### 打卡时段（`CheckInTimeUtil`）
- 每个年级一条规则：早上窗口、平时晚上窗口、返校日晚上窗口。存储格式：`rule.N=年级|早始|早止|晚始|晚止|返校始|返校止`（兼容旧的 5 段格式，早上默认 06:00-07:10）。
- 默认：七/八/九年级早上 06:00-07:10，平时晚上 21:30-21:50，返校日晚上分别为 19:00-19:20 / 19:20-19:40 / 19:40-20:00。
- 返校日 = 本周的 `defaultReturnWeekday`（默认周日）；若设置了 `overrideReturnDate` 且与今天同一周，则以它为准。
- 不在窗口内：打卡页仍可浏览（显示最近的时段），但点击会提示且后端拒绝（403 业务码）。
- **调试开关** `checkin_debug_enabled=1`：任何时间都允许打卡，时段取当前“显示时段”。正式使用时记得关闭。
- 补卡（后台修正）未填时间时，默认取该时段窗口的开始时间。

### 打卡页交互（`static/checkin.html`）
- **单击**未点亮的圆牌 → `POST /api/public/check-in` → 变金牌并显示 `HH:mm`。
- **双击**已点亮的金牌 → `POST /api/public/check-in/revoke` 撤销（同样受时间窗口限制）。（不是长按，长按 3 秒是旧原型的逻辑。）
- 每 5 秒静默轮询 `/api/public/roster` 刷新；右侧“班内战力榜”可切换总榜/周榜/月榜并自动滚动，可折叠。
- 顶部显示每日金句（早/晚两套，按起始序号+起始日期每日轮换）、本年级榜首（姓名打码，如 `张*三`）。
- `autoAdjustZoom()`：可视高度 < 950px 时用 `body.style.zoom` 缩放，适配一体机系统缩放 150%/200%。
- 同一学生同日同时段唯一（表上有唯一键 `uk_student_date_slot`）。

### 圆牌序号
`displayNo` = `student_info.display_order_no`；为空时取学号数字的**末两位**（只有 1 位数字就取该位）。排序同理。后台“学生打卡序号绑定”页可修改，会校验班内序号不重复。

### 榜单与质量分
- 班内榜/后台学生榜排序：累计打卡次数降序 → 质量分降序 → 学号 → ID。
- 质量分（每次 0-10 分，`QUALITY_SCORE_SCALE=10`，按在窗口内的位置线性计算）：
  - 早上、返校日晚上：**越早越高**；
  - 平时晚上：**越晚越高**（代码中 `earlierBetter=false`，属有意设计，修改前先确认）。
- 总榜从 `semester_start_date` 起算；周榜从本周一起（不早于开学日）；月榜从本月 1 日起。周榜/月榜的段位徽章沿用总榜段位。
- 班级出勤榜（后台）：出勤率降序 → 已打卡人数降序 → 年级班级。
- 段位（`RankRuleUtil`）两种模式：`COUNT`（按累计次数阈值，青铜Ⅲ 0 次 … 最强王者 153、无双王者 159、荣耀王者 165，之后每多 1 次加 1 星，见 `段位规则说明.txt`）和 `PERCENT`（按榜内名次百分比分档）。
- 当前教学周 = `(今天 - 开学日) / 7 + 1`；按周导出也以开学日为第 1 周起点。

### 导入与导出
- 花名册 Excel 第 1-3 行为标题，第 4 行为表头，数据从第 5 行开始（`headRowNumber(4)`），修改模板时必须保持一致。
  - 学生：`年级 | 班号 | 学号 | 学生姓名`。班号自动补“班”字；不存在的班级会自动创建并生成 12 位大写十六进制特征码。学生按（班级+学号）匹配，无学号时按（班级+姓名）匹配。
  - 教职工：`姓名 | 登录账号 | 角色 | 年级 | 班号 | 初始密码`。角色支持 `班主任/级长/年级主任/行政` 或英文编码，不允许导入 ADMIN。导入班主任会回写 `class_info.teacher_name`。已存在账号再次导入会**重置密码并强制改密**。
  - 均支持先“预览”（不落库）再“上传”，错误行可导出 Excel。
- 报表导出 `scope`：`daily`（单 sheet 当日明细）、`weekly`（`weekIndex`）、`monthly`（`yyyy-MM`）、`semester`；非日报为“累计统计”sheet + 按日/按周分 sheet 的明细。明细会补齐未打卡行（每个学生每天早晚两行）。
- 班级链接导出 Excel / 快捷方式 zip：`baseUrl` 为空或含 `localhost`/`127.0.0.1` 时自动替换为 `http://<本机局域网IPv4>:8099`。

### `sys_setting` 键一览
`semester_start_date`、`daily_quotes_morning(_start_index/_start_date)`、`daily_quotes_evening(...)`、`daily_quotes(...)`（旧版通用金句，打卡页已不读取）、`rank_rule_settings`（`key=value` 多行）、`evening_checkin_settings`、`checkin_debug_enabled`。

## API 速查
- 免登录 `/api/public`：`GET roster?uniqueCode=`、`GET leaderboard`、`POST check-in`、`POST check-in/revoke`。
- 需登录 `/api/data`：`leaderboard`、`overview`、`leaderboard/student-quality`、`classes`、`correction/records`、`correction/check-in`、`correction/revoke`、`export`。
- 需登录 `/api/admin`（除 `login`、`password/change`、`GET semester-start`、`GET access-base-url` 外均要求 ADMIN）：用户管理、花名册上传/预览/模板、开学日、金句、晚间规则、段位、调试开关、班级与链接导出、学生序号绑定。

## 代码风格
- 统一 4 空格缩进。Java 沿用现有分层：Controller 只做参数接收和响应，业务放 `service/impl`，校验失败抛 `BusinessException(ErrorCode.X, "中文提示")`。
- 实体用 Lombok `@Data`，数据库下划线 ↔ 驼峰自动映射；复杂查询写在 Mapper 接口的 `@Select` 注解里。
- JS 用 camelCase、`async/await`；后台请求统一走 `app.js` 的 `api()`，打卡页走 `request()`。CSS 类名用 kebab-case。
- 注释简短，只解释关键逻辑。

## 测试与验证
没有自动化测试，修改后手动验证：
- 打卡页：圆牌按序号正确渲染；时间窗口内单击点亮并显示时间、双击撤销；窗口外点击给出提示；切换总/周/月榜；一体机缩放下布局正常。
- 可用后台“打卡调试开关”在非打卡时间验证，验证完关闭。
- 后台：用 ADMIN、STAFF、GRADE_LEADER、TEACHER 各登录一次，确认菜单与数据范围；导出各类报表能正常打开。
- 涉及 SQL 的改动同时考虑 `init.sql`（新环境）和正式库的增量 SQL。

## 交付约定
正式机通过“改动文件包”更新：把本次改动的文件按相同相对路径放到 `补丁/<改动名>改动文件/` 下，并写 `说明.txt` 列出文件清单、是否需要执行 SQL、是否需要改 `application.yml`，然后在正式机 IDEA 中覆盖并重新编译运行。

## 隐私
花名册、`0331db.sql`、打卡记录含真实学生和教师姓名，不要上传到外部服务或写进示例/文档。
