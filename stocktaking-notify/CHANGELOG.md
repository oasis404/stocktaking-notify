# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 与 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.1.0] - 2026-09-28

与生产实现（ruoyi-crm）2026-09-24~09-28 的演进同步：测试模式整体移除、接收权限点闸门、文案定稿（免登录深链）。

### Removed

- **测试模式整体移除**（与生产 2026-09-24 同步）：`TestModeScope`、`TestModeConfigProvider`、`TEST-MODE` 闸门、
  Builder 的 `testModeConfigProvider` 与 `dispatch` 的 `testModeOn` 参数全部删除。
  "只发给个别人"的诉求由**接收权限点**承担——"权限点存在即启用"，在 `RespUserProvider` 实现的清单层过滤，
  无任何参数开关（生产实现已两次因参数开关踩坑：忘配就静默全量发送）。

### Added

- **`GateNode.RECEIVE_PERM`（RECEIVE-PERM）**：接收方资格节点标识——持有接收权限点的角色成员才进入收件人清单；
  过滤在清单提供方实现，引擎不做二次判定（闸门链：`GATE-STATUS → GATE-HOLIDAY(START豁免) → GATE-WINDOW →
  GATE-LIST(含接收权限过滤) → BENEFICIARY → GATE-ROLE(可选) → DAY-LIMIT`）。
- **文案定稿（2026-09-28，业务逐字确认）**：三种文案经生产工程基准测试逐字钉死——
  入口提示语改为**企微免登录深链**（OAuth 静默授权直达系统，免输账号密码，仅企微客户端内有效）；
  应用/网站/清单统一为「鼎勤信息管理」「个人盘点清单」命名；开始通知增加「本期」与「初次登录」提示
  （账号为登录人的工号/密码默认为 admin123，仅开始通知携带一次）。
- **截止日消息日期上移**：由"…进行盘点（2026-09-29）"改为"今天是本次盘点的最后一天**（2026-09-29）**，您还有…"，
  deadline 为空时优雅降级（无括号）。

### Changed

- `GroupDispatcher.dispatch` / `dispatchChunk` 移除 `testModeOn` 参数；`markSent`/`markFailed` 去除测试模式分支。
- `NotifyContext` 移除 `testModeOn`；`StocktakingNotifyService` 构造器与 Builder 移除测试模式装配。
- 文案基准（start/remind/lastday）以生产工程 `EamStocktakingNotifyContentTest` 为唯一事实源，本仓库测试逐字对齐。

### Tests

- 删除测试模式相关用例 8 条（解析回归 / 白名单过滤 / fail-closed / 跳过一天一条 / 配置写错等），
  新增三种文案的逐字断言；`ReviewAcceptanceTest` 验收项 12 → **10**（测试模式两项随功能移除）。
- 全量 **38 条**（GateTest 5 / NotifyServiceTest 22 / ReviewAcceptanceTest 10 / Walkthrough 1），全部通过。

## [1.0.2] - 2026-09-22

代码评审修复（第3、4批）：事件通知语义修正 + 正式角色过滤 + 发送留痕落库。

### Fixed

- **P1 开始通知节假日永久丢失**：`HolidayGate` 对 START 类型豁免——"计划已开始"由状态变更事件触发且只发生一次，被周末/节假日拦下就永远收不到；提醒类（REMIND）继续受节假日限制。
- **P1 占位后异常泄漏限流键**：逐人判定 catch 中回收已占用但未使用的当日限流键（此前只有群发阶段失败才释放）。
- **P2 空清单提示误报**：GATE-LIST 提示补充"全部资产已盘完"分支；`RespUserProvider` 契约明确"已盘完的人也要返回，由引擎判 SKIP，不要在 SQL 层过滤"。
- **P2 发送成功计数虚高**：按去重后的企微号统计成功人数，重复号（数据脏）单独 warn。
- **P2 双轨统计口径**：`GroupDispatcher.dispatch` 改为 void，发送结果唯一事实源是逐行 `NotifyResult` + `NotifySummary`。

### Added

- **「一天一条」明确为硬承诺（业务决策）**：开始通知撞当日已占键（无论占位者是提醒还是另一次开始通知）一律跳过，不做抢占——开始通知漏了由第二天起的每日提醒兜底。曾短暂实现过"START 抢占 REMIND 键"，经业务确认"一天一条是硬承诺"后撤除，回归严格一天一条。
- **GATE-ROLE 正式角色闸门** `RoleGate`：`Builder.notifyRoles(...)` 配置后只发给指定角色的成员；未配置 = 闸门关闭，默认行为不变。与测试模式的 roleKeys 匹配无关（那是临时联调开关）。
- **发送留痕落库** `NotifyAuditRepository`（port）：真实执行结束时把逐人结果交给接入方落库（演练不落、落库失败不影响发送），回答"这个人到底收到没有"。

### Tests

- 新增 5 条：START 节假日豁免、角色过滤命中/未配置、留痕落库（含演练不落 + 落库失败不影响主流程）、重复企微号去重。
- 新增 `ReviewAcceptanceTest`（12 项验收脚本）：把评审修复逐项跑一遍并打印可截图报告，末行输出 `12/12 项通过`。全量共 **46 条**。

### Docs

- 新增 [`docs/05-真实测试方案.md`](docs/05-真实测试方案.md)：S0 单测 → S1 验收脚本 → S2 企微沙箱真发 → S3 脏数据演练 → S4 生产灰度，含 4 个 port 的最小实现、留痕表 DDL、日志关键字速查、验收打勾清单、故障注入步骤与回滚方案；并如实列出未验证项（P1-5 仅代码走查、examples/ 缺失）。

## [1.0.1] - 2026-09-22

代码评审修复：发送链路可靠性 + 测试模式解析 fail-closed。

### Fixed

- **P0 发送异常兜底**：`GroupDispatcher.dispatchChunk` 兜住 `sender.send` 抛出的异常（HTTP 超时是常态）——整批判失败、释放当日限流键、异常不冒泡到调用方；`notify()` 对 `dispatch()` 增加防御性兜底，兜底时释放限流键。
- **P0 待发送状态不再冒充跳过**：`NotifyDecision` 新增 `PENDING`（已过闸门、待群发），`NotifyResult.pending()` 改用它；`NotifySummary` 把停留的 `PENDING` 按**失败**统计——发送环节崩溃不会再被误报成"跳过 12 人"。
- **P1 测试模式解析 fail-closed**：`TestModeScope.parse` 重写为逐段 `k=v` 解析，键顺序无关、键名大小写无关；配置非空但缺 `testMode` 开关键、开关值非法、片段缺 `=`，一律按「开启且无白名单」拦截（一条不发）并记 error 日志——配置写错绝不会再静默放行全量发送。

### Changed

- `MessageSender` port 契约补充：实现允许直接抛异常，引擎负责兜底。

### Tests

- 新增回归测试：发送接口异常注入（整批失败 + 限流键释放 + 不冒泡）、配置写错 fail-closed 端到端、解析回归（键顺序 / 大小写 / 非法值 / 缺开关 / 残缺片段）。共 27 条。

## [1.0.0] - 2026-09-21

首个可独立运行、可独立测试的版本，从生产系统的盘点通知实现中抽取并结构化而成。

### Added

- **闸门链引擎** `StocktakingNotifyService`：计划级闸门（状态 / 节假日 / 时间窗 / 清单 / 测试模式）+ 逐人闸门（收件人资格 / 一天一条），任一不过即不发。
- **一天一条限流** `DailyLimitGuard`：限流键维度只到「人 + 自然日」，开始通知与每日提醒、跨计划共用同一把锁；发送失败自动释放，允许当天补发。
- **分组群发** `GroupDispatcher`：按"完整文案"分组，单次调用接收人上限 1000 自动分片；回执带出不可用账号时剔除后重发一次，避免一个坏账号拖垮整批。
- **测试模式** `TestModeScope`：白名单（工号 / 用户ID / 部门 / 角色）命中才发；开启但未配白名单 = 一条都不发（fail-closed）。
- **工作日历** `HolidayCalendar`：写死节假日 + 周末规则，可替换为日历表实现。
- **文案模板** `MessageTemplate`：开始通知 / 每日提醒 / 截止日「最后一天」三种文案，可继承覆盖。
- **扩展点（port）**：`MessageSender`、`DailyLimitStore`、`RespUserProvider`、`TestModeConfigProvider` —— 核心逻辑零运行时依赖（仅 SLF4J），接入方按需实现。
- **节点标识体系** `GateNode`：日志、接口响应、文档三处共用同一套 `GATE-xxx / TEST-MODE / BENEFICIARY / DAY-LIMIT` 标识。
- **单元测试 23 条**：覆盖每道闸门、分组群发、坏账号剔除重发、失败释放限流键、演练预览等。
- **接入示例** `examples/`：企业微信发送器、Redis 限流存储、收件人参考 SQL。
- **文档**：项目简介、架构与闸门链路、接入指南、测试与排错手册。

### Design decisions（值得记录的取舍）

- **宁可不通知，也不要乱通知**：任何判断不出来的情况（无截止时间、清单为空、状态不是进行中、账号匹配不到）一律不发。
- **收件人只按用户ID精确匹配**：不做姓名兜底 —— 同名时宁可漏发，也不能把通知发给非责任人。
- **真实发送不允许任何绕过开关**：时间窗、节假日、状态闸门在发送链路内部强制生效，运维接口也没有豁免参数。
- **测试模式不设自动过期**：只能显式关闭 —— "过期自动放开"会直接变成"发给全员"，而企微消息收不回来。
