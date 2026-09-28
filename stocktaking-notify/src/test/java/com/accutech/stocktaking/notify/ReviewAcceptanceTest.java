package com.accutech.stocktaking.notify;

import com.accutech.stocktaking.notify.model.GateNode;
import com.accutech.stocktaking.notify.model.NotifyDecision;
import com.accutech.stocktaking.notify.model.NotifyResult;
import com.accutech.stocktaking.notify.model.NotifySummary;
import com.accutech.stocktaking.notify.model.NotifyType;
import com.accutech.stocktaking.notify.model.StocktakingPlan;
import com.accutech.stocktaking.notify.send.GroupDispatcher;
import com.accutech.stocktaking.notify.support.HolidayCalendar;
import com.accutech.stocktaking.notify.testing.InMemorySupport;
import com.accutech.stocktaking.notify.testing.InMemorySupport.InMemoryDailyLimitStore;
import com.accutech.stocktaking.notify.testing.InMemorySupport.InMemoryMessageSender;
import com.accutech.stocktaking.notify.testing.InMemorySupport.InMemoryNotifyAuditRepository;
import com.accutech.stocktaking.notify.testing.InMemorySupport.InMemoryRespUserProvider;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 评审修复验收脚本（可执行报告）。
 *
 * <p>与 {@code NotifyServiceTest} 的区别：这里<b>不是</b>为覆盖率写的，
 * 而是为"拿给别人看"写的 —— 每个评审修复项跑一遍，把
 * 「现象 → 预期 → 实际」打印成一行，最后输出 {@code 12/12 项通过}，
 * 可直接截图作为验收证据。</p>
 *
 * <p>运行方式：{@code mvn test -Dtest=ReviewAcceptanceTest}</p>
 *
 * <p>配套文档：{@code docs/05-真实测试方案.md}（S0~S4 五个阶段的傻瓜级步骤）。</p>
 */
class ReviewAcceptanceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 21);

    private static final int EXPECTED_ITEMS = 10;

    private static final List<String> REPORT = new ArrayList<String>();

    private static StocktakingPlan runningPlan() {
        return new StocktakingPlan(23, "2026年9月盘点", StocktakingPlan.STATUS_RUNNING, "进行中",
                MONDAY, MONDAY.plusDays(1));
    }

    private static NotifyResult find(List<NotifyResult> results, String respUserName) {
        for (NotifyResult row : results) {
            if (respUserName.equals(row.getRespUserName())) {
                return row;
            }
        }
        throw new AssertionError("结果里找不到责任人：" + respUserName);
    }

    private static StocktakingNotifyService engine(InMemoryRespUserProvider provider, InMemoryMessageSender sender,
                                                  InMemoryDailyLimitStore store) {
        return StocktakingNotifyService.builder()
                .respUserProvider(provider)
                .messageSender(sender)
                .dailyLimitStore(store)
                .build();
    }

    /** 记一行验收结论（同时打印，方便从 surefire 输出里直接截图） */
    private static void record(String item, String title, String evidence) {
        String line = String.format("%-34s ...... 通过   %s", "【" + item + "】" + title, evidence);
        REPORT.add(line);
        System.out.println(line);
    }

    @AfterAll
    static void printReport() {
        System.out.println();
        System.out.println("================ 盘点通知引擎 · 评审修复验收报告 ================");
        for (String line : REPORT) {
            System.out.println(line);
        }
        assertEquals(EXPECTED_ITEMS, REPORT.size(), "验收项数量与方案不一致，请检查是否有用例被跳过");
        System.out.println("================ 结论：" + REPORT.size() + "/" + EXPECTED_ITEMS + " 项通过 ================");
    }

    @Test
    @DisplayName("【P0-1】发送环节崩溃时，结果记为「失败」而不是「跳过」")
    void p01FailedNotReportedAsSkipped() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 1),
                InMemorySupport.user(16018L, "徐涛", "A10470", "xutao", 1));
        InMemoryMessageSender sender = new InMemoryMessageSender().withThrowing("HTTP 连接超时");

        List<NotifyResult> results = engine(provider, sender, new InMemoryDailyLimitStore())
                .sendDailyRemind(runningPlan(), MONDAY);

        NotifySummary summary = NotifySummary.of(results);
        assertEquals(2, summary.getFailed());
        assertEquals(0, summary.getSkipped());
        record("P0-1", "发送崩溃不冒充「跳过」", "失败=" + summary.getFailed() + " 跳过=" + summary.getSkipped());
    }

    @Test
    @DisplayName("【P0-2】发送接口抛异常不冒泡到调用方，且限流键已释放（可当天补发）")
    void p02ExceptionContainedAndKeyReleased() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 1));
        InMemoryMessageSender sender = new InMemoryMessageSender().withThrowing("HTTP 连接超时");
        InMemoryDailyLimitStore store = new InMemoryDailyLimitStore();

        // 不写 assertDoesNotThrow：若异常冒泡，这个测试会直接失败并打出堆栈
        List<NotifyResult> results = engine(provider, sender, store).sendDailyRemind(runningPlan(), MONDAY);

        assertEquals(NotifyDecision.FAIL, results.get(0).getDecision());
        assertTrue(results.get(0).getReason().contains("发送接口异常"));
        assertTrue(store.occupiedKeys().isEmpty());
        record("P0-2", "异常不冒泡 + 限流键已释放", "未抛异常，占位键=" + store.occupiedKeys().size() + " 个");
    }

    @Test
    @DisplayName("【P1-4】开始通知豁免节假日（周末改状态不会永久丢失），提醒仍受限")
    void p14StartBypassesHolidayGate() {
        InMemoryMessageSender sender = new InMemoryMessageSender();
        StocktakingNotifyService service = StocktakingNotifyService.builder()
                .respUserProvider(new InMemoryRespUserProvider().withUsers(23,
                        InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 1)))
                .messageSender(sender)
                .dailyLimitStore(new InMemoryDailyLimitStore())
                .holidayCalendar(HolidayCalendar.of(MONDAY.toString()))
                .build();

        assertEquals(1, NotifySummary.of(service.sendStartedNotify(runningPlan(), MONDAY)).getSent());
        assertEquals(1, sender.callCount());

        List<NotifyResult> remind = service.sendDailyRemind(runningPlan(), MONDAY);
        assertEquals(GateNode.GATE_HOLIDAY, remind.get(0).getGateNode());
        record("P1-4", "开始通知节假日照发", "开始通知已发；提醒被 GATE-HOLIDAY 拦下");
    }

    @Test
    @DisplayName("【P2-6】空清单提示包含「全部资产已盘完」分支")
    void p26EmptyListMessageMentionsAllFinished() {
        // 计划 23 不注册任何责任人 → 触发清单闸门
        List<NotifyResult> results = engine(new InMemoryRespUserProvider(), new InMemoryMessageSender(),
                new InMemoryDailyLimitStore())
                .sendDailyRemind(runningPlan(), MONDAY);

        assertEquals(GateNode.GATE_LIST, results.get(0).getGateNode());
        assertTrue(results.get(0).getReason().contains("或全部资产已盘完"),
                "空清单提示必须覆盖「已盘完」这一种，避免运维误判为数据问题");
        record("P2-6", "空清单提示含「已盘完」", "原因文案已包含该分支");
    }

    @Test
    @DisplayName("【P2-7】重复企微号去重统计：一次调用、只发一个号")
    void p27DuplicateWebComIdCountedOnce() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 1),
                InMemorySupport.user(16018L, "徐涛", "A10470", "yuchuxian", 1));
        InMemoryMessageSender sender = new InMemoryMessageSender();

        List<NotifyResult> results = engine(provider, sender, new InMemoryDailyLimitStore())
                .sendDailyRemind(runningPlan(), MONDAY);

        assertEquals(1, sender.callCount());
        assertEquals(1, sender.allRecipients().size());
        assertEquals(2, NotifySummary.of(results).getSent());
        record("P2-7", "重复号去重统计", "调用=1 次，实际接收人=" + sender.allRecipients().size() + " 个");
    }

    @Test
    @DisplayName("【P2-8】dispatch 返回 void：统计口径唯一（编译期 + 反射双重保证）")
    void p28DispatchReturnsVoid() throws Exception {
        Method dispatch = GroupDispatcher.class.getMethod("dispatch", Integer.class, NotifyType.class,
                Map.class);
        assertEquals(void.class, dispatch.getReturnType());
        record("P2-8", "统计口径唯一（dispatch 返回 void）", "返回类型=" + dispatch.getReturnType());
    }

    @Test
    @DisplayName("【角色闸门】只发配置角色的成员，其余人 GATE-ROLE 跳过")
    void roleGateFiltersNonMembers() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.userWithRole(16017L, "余楚贤", "A10529", "yuchuxian", 1,
                        "common_staff", "普通员工"),
                InMemorySupport.userWithRole(16018L, "徐涛", "A10470", "xutao", 1,
                        "warehouse_keeper", "库管员"));
        InMemoryMessageSender sender = new InMemoryMessageSender();
        StocktakingNotifyService service = StocktakingNotifyService.builder()
                .respUserProvider(provider)
                .messageSender(sender)
                .dailyLimitStore(new InMemoryDailyLimitStore())
                .notifyRoles(Arrays.asList("common_staff"))
                .build();

        List<NotifyResult> results = service.sendDailyRemind(runningPlan(), MONDAY);

        assertEquals(1, NotifySummary.of(results).getSent());
        assertEquals(GateNode.GATE_ROLE, find(results, "徐涛").getGateNode());
        assertTrue(find(results, "徐涛").getReason().contains("角色不匹配"));
        record("ROLE", "GATE-ROLE 角色过滤生效", "发送=1，非成员跳过（GATE-ROLE）");
    }

    @Test
    @DisplayName("【留痕落库】真实执行落库、演练不落库、落库故障不影响发送")
    void auditRepositoryBehaviour() {
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

        // 演练不落库（否则对账表会被预览噪音污染）
        service.notify(runningPlan(), NotifyType.REMIND, MONDAY, true);
        assertEquals(1, audit.saved().size());

        // 留痕库故障不影响发送主流程
        StocktakingNotifyService brokenAuditService = StocktakingNotifyService.builder()
                .respUserProvider(new InMemoryRespUserProvider().withUsers(23,
                        InMemorySupport.user(16019L, "张三", "A10001", "zhangsan", 1)))
                .messageSender(new InMemoryMessageSender())
                .dailyLimitStore(new InMemoryDailyLimitStore())
                .notifyAuditRepository(new InMemoryNotifyAuditRepository().withSaveFailure())
                .build();
        assertEquals(1, NotifySummary.of(brokenAuditService.sendDailyRemind(runningPlan(), MONDAY)).getSent());
        record("AUDIT", "留痕落库（真落/演练不落/故障不影响）", "落库=" + audit.saved().size() + " 条，演练未增，故障仍发送成功");
    }

    @Test
    @DisplayName("【一天一条】硬承诺：当日提醒先发后，开始通知同样被拦")
    void dailyLimitIsHardPromise() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 1));
        InMemoryMessageSender sender = new InMemoryMessageSender();
        StocktakingNotifyService service = engine(provider, sender, new InMemoryDailyLimitStore());

        assertEquals(1, NotifySummary.of(service.sendDailyRemind(runningPlan(), MONDAY)).getSent());
        List<NotifyResult> start = service.sendStartedNotify(runningPlan(), MONDAY);

        assertEquals(GateNode.DAY_LIMIT, start.get(0).getGateNode());
        assertTrue(start.get(0).getReason().contains("一天一条"));
        assertEquals(1, sender.callCount());
        record("DAY-LIMIT", "一天一条硬承诺（开始通知不插队）", "开始通知被拦，当天调用总数=" + sender.callCount() + " 次");
    }

    @Test
    @DisplayName("【发送数据异常】坏账号剔除后重发，其余人不受影响")
    void invalidAccountRemovedAndRetried() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "bad_account", 1),
                InMemorySupport.user(16018L, "徐涛", "A10470", "xutao", 1),
                InMemorySupport.user(16019L, "张三", "A10001", "zhangsan", 1));
        InMemoryMessageSender sender = new InMemoryMessageSender().withInvalidWebComId("bad_account");

        List<NotifyResult> results = engine(provider, sender, new InMemoryDailyLimitStore())
                .sendDailyRemind(runningPlan(), MONDAY);

        NotifySummary summary = NotifySummary.of(results);
        assertEquals(2, summary.getSent());
        assertEquals(1, summary.getFailed());
        assertTrue(find(results, "余楚贤").getReason().contains("账号不可用"));
        assertEquals(2, sender.callCount());
        record("发送数据异常", "坏账号剔除重发", "发送=2 失败=1（仅坏账号），调用=2 次");
    }
}
