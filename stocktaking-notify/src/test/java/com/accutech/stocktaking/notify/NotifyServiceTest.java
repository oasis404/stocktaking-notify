package com.accutech.stocktaking.notify;

import com.accutech.stocktaking.notify.model.GateNode;
import com.accutech.stocktaking.notify.model.NotifyDecision;
import com.accutech.stocktaking.notify.model.NotifyResult;
import com.accutech.stocktaking.notify.model.NotifySummary;
import com.accutech.stocktaking.notify.model.NotifyType;
import com.accutech.stocktaking.notify.model.RespUser;
import com.accutech.stocktaking.notify.model.StocktakingPlan;
import com.accutech.stocktaking.notify.support.HolidayCalendar;
import com.accutech.stocktaking.notify.testing.InMemorySupport;
import com.accutech.stocktaking.notify.testing.InMemorySupport.InMemoryDailyLimitStore;
import com.accutech.stocktaking.notify.testing.InMemorySupport.InMemoryMessageSender;
import com.accutech.stocktaking.notify.testing.InMemorySupport.InMemoryNotifyAuditRepository;
import com.accutech.stocktaking.notify.testing.InMemorySupport.InMemoryRespUserProvider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 引擎端到端单测：覆盖"能发出去的路径"和"所有不该发出去的路径"。
 *
 * <p>每条断言都对应一个业务承诺，改动逻辑时这些测试会直接告诉你破坏了哪一条。</p>
 */
class NotifyServiceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 21);

    private static StocktakingPlan runningPlan() {
        return new StocktakingPlan(23, "2026年9月盘点", StocktakingPlan.STATUS_RUNNING, "进行中",
                MONDAY, MONDAY.plusDays(1));
    }

    private static StocktakingPlan plan(String status, LocalDate start, LocalDate end) {
        return new StocktakingPlan(23, "2026年9月盘点", status, "进行中", start, end);
    }

    private static StocktakingNotifyService engine(InMemoryRespUserProvider provider, InMemoryMessageSender sender,
                                                  InMemoryDailyLimitStore store) {
        return StocktakingNotifyService.builder()
                .respUserProvider(provider)
                .messageSender(sender)
                .dailyLimitStore(store)
                .build();
    }

    private static NotifyResult find(List<NotifyResult> results, String respUserName) {
        for (NotifyResult row : results) {
            if (respUserName.equals(row.getRespUserName())) {
                return row;
            }
        }
        throw new AssertionError("结果里找不到责任人：" + respUserName);
    }

    // ==================== 可以发的路径 ====================

    @Test
    @DisplayName("按文案分组群发：同文案的人合并为一次调用（2 种文案 → 2 次调用）")
    void groupByContentReducesCalls() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 1),
                InMemorySupport.user(16018L, "徐涛", "A10470", "xutao", 1),
                InMemorySupport.user(16019L, "张三", "A10001", "zhangsan", 5));
        InMemoryMessageSender sender = new InMemoryMessageSender();
        InMemoryDailyLimitStore store = new InMemoryDailyLimitStore();

        List<NotifyResult> results = engine(provider, sender, store)
                .sendDailyRemind(runningPlan(), MONDAY);

        NotifySummary summary = NotifySummary.of(results);
        assertEquals(3, summary.getSent());
        assertEquals(0, summary.getFailed());
        // 三个人、两种文案 → 只调用两次
        assertEquals(2, sender.callCount());
        // 文案内容：待盘点 1 项、还剩 1 天（2026-09-28 定稿：免登录深链入口）
        assertEquals("同事您好，您还有1项资产盘点未执行，"
                        + "请到企业微信工作台中「鼎勤信息管理」应用，或者「鼎勤信息管理」网站"
                        + "<a href=\"https://open.weixin.qq.com/connect/oauth2/authorize?appid=wwd3b491ac3402f235"
                        + "&redirect_uri=https%3A%2F%2Finfo.accutech.net.cn%3A4431%2Fauth%2Fcorpwx%2Flogin"
                        + "&response_type=code&scope=snsapi_base#wechat_redirect\">请点击此处</a>"
                        + "进入「个人盘点清单」进行盘点，"
                        + "截止时间还剩1天（2026-09-22），如有问题请联系财务。",
                find(results, "余楚贤").getContent());
        assertTrue(find(results, "余楚贤").getContent().equals(find(results, "徐涛").getContent()));
        assertFalse(find(results, "余楚贤").getContent().equals(find(results, "张三").getContent()));
        // 每人一份当日限流键
        assertEquals(3, store.occupiedKeys().size());
    }

    @Test
    @DisplayName("开始通知：文案为「本期资产盘点计划已开始…」+ 免登录深链 + 初次登录提示")
    void startContent() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 2));
        List<NotifyResult> results = engine(provider, new InMemoryMessageSender(), new InMemoryDailyLimitStore())
                .sendStartedNotify(runningPlan(), MONDAY);
        assertEquals("同事您好，本期资产盘点计划已开始，"
                        + "请到企业微信工作台中「鼎勤信息管理」应用，或者「鼎勤信息管理」网站"
                        + "<a href=\"https://open.weixin.qq.com/connect/oauth2/authorize?appid=wwd3b491ac3402f235"
                        + "&redirect_uri=https%3A%2F%2Finfo.accutech.net.cn%3A4431%2Fauth%2Fcorpwx%2Flogin"
                        + "&response_type=code&scope=snsapi_base#wechat_redirect\">请点击此处</a>"
                        + "进入「个人盘点清单」进行盘点。"
                        + "（初次登录，账号为登录人的工号/密码默认为admin123）",
                find(results, "余楚贤").getContent());
    }

    @Test
    @DisplayName("截止日当天：文案升级为「今天是本次盘点的最后一天」")
    void lastDayContent() {
        StocktakingPlan plan = plan(StocktakingPlan.STATUS_RUNNING, MONDAY.minusDays(1), MONDAY);
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 4));
        List<NotifyResult> results = engine(provider, new InMemoryMessageSender(), new InMemoryDailyLimitStore())
                .sendDailyRemind(plan, MONDAY);
        assertTrue(find(results, "余楚贤").getContent().startsWith(
                "同事您好，今天是本次盘点的最后一天（2026-09-21），您还有4项资产盘点未执行，"));
    }

    // ==================== 不该发的路径 ====================

    @Test
    @DisplayName("闸门① 状态不是进行中：整批不发")
    void blockedByStatus() {
        InMemoryMessageSender sender = new InMemoryMessageSender();
        List<NotifyResult> results = engine(new InMemoryRespUserProvider(), sender, new InMemoryDailyLimitStore())
                .sendStartedNotify(plan(StocktakingPlan.STATUS_NOT_STARTED, MONDAY, MONDAY.plusDays(1)), MONDAY);
        assertEquals(1, results.size());
        assertEquals(GateNode.GATE_STATUS, results.get(0).getGateNode());
        assertEquals(0, sender.callCount());
    }

    @Test
    @DisplayName("闸门② 节假日：整批不发（人工触发同样受限）")
    void blockedByHoliday() {
        InMemoryMessageSender sender = new InMemoryMessageSender();
        StocktakingNotifyService service = StocktakingNotifyService.builder()
                .respUserProvider(new InMemoryRespUserProvider().withUsers(23,
                        InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 1)))
                .messageSender(sender)
                .dailyLimitStore(new InMemoryDailyLimitStore())
                .holidayCalendar(HolidayCalendar.of(MONDAY.toString()))
                .build();
        List<NotifyResult> results = service.sendDailyRemind(runningPlan(), MONDAY);
        assertEquals(GateNode.GATE_HOLIDAY, results.get(0).getGateNode());
        assertEquals(0, sender.callCount());
    }

    @Test
    @DisplayName("闸门③ 时间窗：未开始 / 已过期都不发")
    void blockedByWindow() {
        InMemoryMessageSender sender = new InMemoryMessageSender();
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 1));
        StocktakingNotifyService service = engine(provider, sender, new InMemoryDailyLimitStore());

        assertEquals(GateNode.GATE_WINDOW,
                service.sendDailyRemind(plan(StocktakingPlan.STATUS_RUNNING, MONDAY.plusDays(1), MONDAY.plusDays(3)), MONDAY)
                        .get(0).getGateNode());
        assertEquals(GateNode.GATE_WINDOW,
                service.sendDailyRemind(plan(StocktakingPlan.STATUS_RUNNING, MONDAY.minusDays(3), MONDAY.minusDays(1)), MONDAY)
                        .get(0).getGateNode());
        assertEquals(0, sender.callCount());
    }

    @Test
    @DisplayName("闸门⑥ 已盘完的人不发，其余人照发")
    void skipFinishedPerson() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 0),
                InMemorySupport.user(16018L, "徐涛", "A10470", "xutao", 2));
        InMemoryMessageSender sender = new InMemoryMessageSender();
        List<NotifyResult> results = engine(provider, sender, new InMemoryDailyLimitStore()).sendDailyRemind(runningPlan(), MONDAY);

        assertEquals(NotifyDecision.SKIP, find(results, "余楚贤").getDecision());
        assertTrue(find(results, "余楚贤").getReason().contains("无待盘点资产"));
        assertEquals(1, NotifySummary.of(results).getSent());
        assertEquals(1, sender.callCount());
        assertEquals(1, sender.allRecipients().size());
        assertEquals("xutao", sender.allRecipients().get(0));
    }

    @Test
    @DisplayName("闸门⑥ 没有企微号：不发（宁可漏发，也不发给别人）")
    void skipPersonWithoutWebComId() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.userWithoutWebComId(16017L, "余楚贤", "A10529", 1));
        InMemoryMessageSender sender = new InMemoryMessageSender();
        List<NotifyResult> results = engine(provider, sender, new InMemoryDailyLimitStore()).sendDailyRemind(runningPlan(), MONDAY);

        assertEquals(GateNode.BENEFICIARY, find(results, "余楚贤").getGateNode());
        assertTrue(find(results, "余楚贤").getReason().contains("无可用企业微信账号"));
        assertEquals(0, sender.callCount());
    }

    @Test
    @DisplayName("闸门⑦ 一天一条：同一人同一天第二次直接跳过，不再调用发送")
    void dailyLimitBlocksSecondCall() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 1));
        InMemoryMessageSender sender = new InMemoryMessageSender();
        InMemoryDailyLimitStore store = new InMemoryDailyLimitStore();
        StocktakingNotifyService service = engine(provider, sender, store);

        assertEquals(1, NotifySummary.of(service.sendDailyRemind(runningPlan(), MONDAY)).getSent());
        List<NotifyResult> second = service.sendDailyRemind(runningPlan(), MONDAY);
        assertEquals(NotifyDecision.SKIP, second.get(0).getDecision());
        assertEquals(GateNode.DAY_LIMIT, second.get(0).getGateNode());
        assertTrue(second.get(0).getReason().contains("一天一条"));
        // 关键：第二次连发送接口都没调用
        assertEquals(1, sender.callCount());
    }

    @Test
    @DisplayName("闸门⑦ 开始通知与每日提醒共用同一把锁（当天只收一条）")
    void startAndRemindShareDailyLimit() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 1));
        InMemoryMessageSender sender = new InMemoryMessageSender();
        StocktakingNotifyService service = engine(provider, sender, new InMemoryDailyLimitStore());

        assertEquals(1, NotifySummary.of(service.sendStartedNotify(runningPlan(), MONDAY)).getSent());
        List<NotifyResult> remind = service.sendDailyRemind(runningPlan(), MONDAY);
        assertEquals(GateNode.DAY_LIMIT, remind.get(0).getGateNode());
        assertEquals(1, sender.callCount());
    }

    @Test
    @DisplayName("演练模式：不发短信、不占限流键，但能看到文案")
    void dryRunPreview() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 3));
        InMemoryMessageSender sender = new InMemoryMessageSender();
        InMemoryDailyLimitStore store = new InMemoryDailyLimitStore();
        StocktakingNotifyService service = engine(provider, sender, store);

        List<NotifyResult> results = service.notify(runningPlan(), NotifyType.REMIND, MONDAY, true);
        assertEquals(NotifyDecision.PREVIEW, results.get(0).getDecision());
        assertNotNull(results.get(0).getContent());
        assertEquals(0, sender.callCount());
        assertTrue(store.occupiedKeys().isEmpty());
    }

    @Test
    @DisplayName("演练模式：当天已发过的人会被标成「真实发送会被一天一条拦下」")
    void dryRunShowsDailyLimit() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 3));
        InMemoryMessageSender sender = new InMemoryMessageSender();
        StocktakingNotifyService service = engine(provider, sender, new InMemoryDailyLimitStore());

        service.sendDailyRemind(runningPlan(), MONDAY);
        List<NotifyResult> preview = service.notify(runningPlan(), NotifyType.REMIND, MONDAY, true);
        assertEquals(GateNode.DAY_LIMIT, preview.get(0).getGateNode());
        assertTrue(preview.get(0).getReason().contains("会被「一天一条」拦下"));
    }

    // ==================== 发送阶段的容错 ====================

    @Test
    @DisplayName("坏账号：从名单里剔除后重发，不让一个坏账号拖垮整批")
    void invalidAccountIsRemovedAndRetried() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "bad_account", 1),
                InMemorySupport.user(16018L, "徐涛", "A10470", "xutao", 1),
                InMemorySupport.user(16019L, "张三", "A10001", "zhangsan", 1));
        InMemoryMessageSender sender = new InMemoryMessageSender().withInvalidWebComId("bad_account");
        List<NotifyResult> results = engine(provider, sender, new InMemoryDailyLimitStore()).sendDailyRemind(runningPlan(), MONDAY);

        NotifySummary summary = NotifySummary.of(results);
        assertEquals(2, summary.getSent());
        assertEquals(1, summary.getFailed());
        assertTrue(find(results, "余楚贤").getReason().contains("账号不可用"));
        // 共 2 次调用：第一次整批失败，第二次剔除坏账号后只发剩余 2 人
        assertEquals(2, sender.callCount());
        assertEquals(2, sender.batches().get(1).getWebComIds().size());
    }

    @Test
    @DisplayName("与账号无关的失败：整批判失败，并释放限流键以便当天补发")
    void networkFailureReleasesDailyKey() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 1));
        InMemoryMessageSender sender = new InMemoryMessageSender().withGlobalFail("企业微信发送地址未配置");
        InMemoryDailyLimitStore store = new InMemoryDailyLimitStore();
        List<NotifyResult> results = engine(provider, sender, store)
                .sendDailyRemind(runningPlan(), MONDAY);

        assertEquals(NotifyDecision.FAIL, results.get(0).getDecision());
        assertTrue(results.get(0).getReason().contains("释放当日限流键"));
        // 限流键已释放 → 当天可以重跑补发
        assertTrue(store.occupiedKeys().isEmpty());
    }

    @Test
    @DisplayName("发送接口抛异常（HTTP 超时常态）：整批判失败并释放限流键，异常不冒泡到调用方")
    void senderExceptionMarksFailedAndReleasesKeys() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 1),
                InMemorySupport.user(16018L, "徐涛", "A10470", "xutao", 1));
        InMemoryMessageSender sender = new InMemoryMessageSender().withThrowing("HTTP 连接超时");
        InMemoryDailyLimitStore store = new InMemoryDailyLimitStore();

        // 关键：notify() 不向外抛异常，定时任务 / 状态变更接口不会被发送环节拖垮
        List<NotifyResult> results = engine(provider, sender, store)
                .sendDailyRemind(runningPlan(), MONDAY);

        NotifySummary summary = NotifySummary.of(results);
        assertEquals(2, summary.getFailed());
        // 不能把发送环节的失败统计成"跳过"误导运维
        assertEquals(0, summary.getSkipped());
        assertEquals(NotifyDecision.FAIL, find(results, "余楚贤").getDecision());
        assertTrue(find(results, "余楚贤").getReason().contains("发送接口异常"));
        // 已占用的限流键全部释放 → 当天可以重跑补发
        assertTrue(store.occupiedKeys().isEmpty());
    }

    // ==================== 闸门②豁免与角色过滤 ====================

    @Test
    @DisplayName("闸门②豁免：周末/节假日改状态触发开始通知照发（事件只来一次，不能拦成永久丢失）")
    void startNotifyBypassesHolidayGate() {
        InMemoryMessageSender sender = new InMemoryMessageSender();
        StocktakingNotifyService service = StocktakingNotifyService.builder()
                .respUserProvider(new InMemoryRespUserProvider().withUsers(23,
                        InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 1)))
                .messageSender(sender)
                .dailyLimitStore(new InMemoryDailyLimitStore())
                .holidayCalendar(HolidayCalendar.of(MONDAY.toString()))
                .build();

        List<NotifyResult> start = service.sendStartedNotify(runningPlan(), MONDAY);
        assertEquals(1, NotifySummary.of(start).getSent());
        assertEquals(1, sender.callCount());

        // 提醒类仍受节假日限制：被计划级闸门整批拦下
        List<NotifyResult> remind = service.sendDailyRemind(runningPlan(), MONDAY);
        assertEquals(GateNode.GATE_HOLIDAY, remind.get(0).getGateNode());
        assertEquals(1, sender.callCount());
    }

    @Test
    @DisplayName("GATE-ROLE 角色过滤：只发配置角色的成员，其余人写明原因")
    void roleGateFiltersNonMembers() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.userWithRole(16017L, "余楚贤", "A10529", "yuchuxian", 1,
                        "stockaking_manager", "盘点管理员"),
                InMemorySupport.userWithRole(16018L, "徐涛", "A10470", "xutao", 1,
                        "warehouse_keeper", "库管员"));
        InMemoryMessageSender sender = new InMemoryMessageSender();
        StocktakingNotifyService service = StocktakingNotifyService.builder()
                .respUserProvider(provider)
                .messageSender(sender)
                .dailyLimitStore(new InMemoryDailyLimitStore())
                .notifyRoles(java.util.Arrays.asList("stockaking_manager"))
                .build();

        List<NotifyResult> results = service.sendDailyRemind(runningPlan(), MONDAY);

        NotifySummary summary = NotifySummary.of(results);
        assertEquals(1, summary.getSent());
        assertEquals(1, summary.getSkipped());
        assertEquals(GateNode.GATE_ROLE, find(results, "徐涛").getGateNode());
        assertTrue(find(results, "徐涛").getReason().contains("角色不匹配"));
        assertEquals(1, sender.allRecipients().size());
        assertEquals("yuchuxian", sender.allRecipients().get(0));
    }

    @Test
    @DisplayName("GATE-ROLE 未配置 = 闸门关闭，默认行为不变")
    void roleGateDisabledByDefault() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.userWithRole(16017L, "余楚贤", "A10529", "yuchuxian", 1,
                        "stockaking_manager", "盘点管理员"),
                InMemorySupport.userWithRole(16018L, "徐涛", "A10470", "xutao", 1,
                        "warehouse_keeper", "库管员"));
        InMemoryMessageSender sender = new InMemoryMessageSender();
        List<NotifyResult> results = engine(provider, sender, new InMemoryDailyLimitStore()).sendDailyRemind(runningPlan(), MONDAY);
        assertEquals(2, NotifySummary.of(results).getSent());
    }

    @Test
    @DisplayName("发送留痕落库：真实执行落逐人结果，演练不落；落库失败不影响发送")
    void auditRepositoryRecordsResults() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 1));
        InMemoryMessageSender sender = new InMemoryMessageSender();
        InMemoryNotifyAuditRepository audit = new InMemoryNotifyAuditRepository();
        StocktakingNotifyService service = StocktakingNotifyService.builder()
                .respUserProvider(provider)
                .messageSender(sender)
                .dailyLimitStore(new InMemoryDailyLimitStore())
                .notifyAuditRepository(audit)
                .build();

        service.sendDailyRemind(runningPlan(), MONDAY);
        assertEquals(1, audit.saved().size());
        assertEquals(NotifyDecision.SEND, audit.saved().get(0).getDecision());

        // 补跑被一天一条拦下，但留痕照记（对账时看得到"这次为什么没发"）
        service.sendDailyRemind(runningPlan(), MONDAY);
        assertEquals(2, audit.saved().size());
        assertEquals(NotifyDecision.SKIP, audit.saved().get(1).getDecision());

        // 演练不落库
        service.notify(runningPlan(), NotifyType.REMIND, MONDAY, true);
        assertEquals(2, audit.saved().size());

        // 落库失败不影响发送主流程
        InMemoryNotifyAuditRepository brokenAudit = new InMemoryNotifyAuditRepository().withSaveFailure();
        StocktakingNotifyService brokenService = StocktakingNotifyService.builder()
                .respUserProvider(new InMemoryRespUserProvider().withUsers(23,
                        InMemorySupport.user(16019L, "张三", "A10001", "zhangsan", 1)))
                .messageSender(new InMemoryMessageSender())
                .dailyLimitStore(new InMemoryDailyLimitStore())
                .notifyAuditRepository(brokenAudit)
                .build();
        List<NotifyResult> results = brokenService.sendDailyRemind(runningPlan(), MONDAY);
        assertEquals(1, NotifySummary.of(results).getSent());
    }

    @Test
    @DisplayName("重复企微号（数据脏）：一次调用去重发送，结果行仍逐人回写")
    void duplicateWebComIdSentOnce() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 1),
                InMemorySupport.user(16018L, "徐涛", "A10470", "yuchuxian", 1));
        InMemoryMessageSender sender = new InMemoryMessageSender();

        List<NotifyResult> results = engine(provider, sender, new InMemoryDailyLimitStore()).sendDailyRemind(runningPlan(), MONDAY);

        // 同文案同人合并为一次调用，接收人去重后只有 1 个
        assertEquals(1, sender.callCount());
        assertEquals(1, sender.allRecipients().size());
        // 两行结果都正常回写成功（每个人的结果明细不缺）
        assertEquals(2, NotifySummary.of(results).getSent());
    }

    // ==================== 一天一条硬承诺 ====================

    @Test
    @DisplayName("一天一条硬承诺：当日提醒先发后，开始通知同样被拦（开始通知漏了由次日提醒兜底）")
    void startBlockedAfterRemindSameDay() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 1));
        InMemoryMessageSender sender = new InMemoryMessageSender();
        StocktakingNotifyService service = engine(provider, sender, new InMemoryDailyLimitStore());

        // 16:45 每日提醒先发出
        assertEquals(1, NotifySummary.of(service.sendDailyRemind(runningPlan(), MONDAY)).getSent());

        // 16:50 状态改为进行中：开始通知被一天一条拦下（同一天只发一条，硬承诺）
        List<NotifyResult> start = service.sendStartedNotify(runningPlan(), MONDAY);
        assertEquals(GateNode.DAY_LIMIT, start.get(0).getGateNode());
        assertTrue(start.get(0).getReason().contains("一天一条"));
        assertEquals(1, sender.callCount());
    }

    @Test
    @DisplayName("当天重复触发开始通知同样被一天一条拦下")
    void startBlockedOnRepeatedTrigger() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 1));
        InMemoryMessageSender sender = new InMemoryMessageSender();
        StocktakingNotifyService service = engine(provider, sender, new InMemoryDailyLimitStore());

        assertEquals(1, NotifySummary.of(service.sendStartedNotify(runningPlan(), MONDAY)).getSent());
        List<NotifyResult> second = service.sendStartedNotify(runningPlan(), MONDAY);
        assertEquals(GateNode.DAY_LIMIT, second.get(0).getGateNode());
        assertTrue(second.get(0).getReason().contains("一天一条"));
        assertEquals(1, sender.callCount());
    }
}
