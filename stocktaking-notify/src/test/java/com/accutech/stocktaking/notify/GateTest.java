package com.accutech.stocktaking.notify;

import com.accutech.stocktaking.notify.gate.GateOutcome;
import com.accutech.stocktaking.notify.gate.HolidayGate;
import com.accutech.stocktaking.notify.gate.PlanStatusGate;
import com.accutech.stocktaking.notify.gate.RecipientGate;
import com.accutech.stocktaking.notify.gate.TimeWindowGate;
import com.accutech.stocktaking.notify.model.GateNode;
import com.accutech.stocktaking.notify.model.NotifyContext;
import com.accutech.stocktaking.notify.model.NotifyType;
import com.accutech.stocktaking.notify.model.RespUser;
import com.accutech.stocktaking.notify.model.StocktakingPlan;
import com.accutech.stocktaking.notify.support.HolidayCalendar;
import com.accutech.stocktaking.notify.testing.InMemorySupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 闸门单测：每一个"不发"的条件都必须有对应断言。
 */
class GateTest {

    /** 2026-09-21 是周一，用作"工作日"基准 */
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 21);

    private static NotifyContext context(StocktakingPlan plan, LocalDate today) {
        return new NotifyContext(plan, NotifyType.REMIND, today, false);
    }

    private static StocktakingPlan plan(String status, LocalDate start, LocalDate end) {
        return new StocktakingPlan(23, "2026年9月盘点", status, "进行中", start, end);
    }

    @Test
    @DisplayName("闸门① 状态：只有「进行中」才放行")
    void planStatusGate() {
        PlanStatusGate gate = new PlanStatusGate();
        assertTrue(gate.check(context(plan(StocktakingPlan.STATUS_RUNNING, MONDAY, MONDAY.plusDays(1)), MONDAY)).isPassed());
        GateOutcome notStarted = gate.check(context(plan(StocktakingPlan.STATUS_NOT_STARTED, MONDAY, MONDAY.plusDays(1)), MONDAY));
        assertFalse(notStarted.isPassed());
        assertEquals(GateNode.GATE_STATUS, notStarted.getNode());
        assertFalse(gate.check(context(plan(StocktakingPlan.STATUS_FINISHED, MONDAY, MONDAY.plusDays(1)), MONDAY)).isPassed());
    }

    @Test
    @DisplayName("闸门② 节假日：写死节假日与周末都不发")
    void holidayGate() {
        HolidayCalendar calendar = HolidayCalendar.of("2026-09-25", "2026-09-26", "2026-09-27");
        HolidayGate gate = new HolidayGate(calendar);
        assertTrue(gate.check(context(plan(StocktakingPlan.STATUS_RUNNING, MONDAY, MONDAY.plusDays(30)), MONDAY)).isPassed());

        // 周五 09-25 属于写死的节假日
        GateOutcome fixedHoliday = gate.check(context(plan(StocktakingPlan.STATUS_RUNNING, MONDAY, MONDAY.plusDays(30)),
                LocalDate.of(2026, 9, 25)));
        assertFalse(fixedHoliday.isPassed());
        assertEquals(GateNode.GATE_HOLIDAY, fixedHoliday.getNode());

        // 周六 / 周日
        assertFalse(gate.check(context(plan(StocktakingPlan.STATUS_RUNNING, MONDAY, MONDAY.plusDays(30)),
                LocalDate.of(2026, 9, 19))).isPassed());
        assertFalse(gate.check(context(plan(StocktakingPlan.STATUS_RUNNING, MONDAY, MONDAY.plusDays(30)),
                LocalDate.of(2026, 9, 20))).isPassed());
    }

    @Test
    @DisplayName("闸门③ 时间窗：无截止时间 / 未开始 / 已过期都不发，起止当天都放行")
    void timeWindowGate() {
        TimeWindowGate gate = new TimeWindowGate();

        GateOutcome noDeadline = gate.check(context(plan(StocktakingPlan.STATUS_RUNNING, MONDAY, null), MONDAY));
        assertFalse(noDeadline.isPassed());
        assertEquals(GateNode.GATE_WINDOW, noDeadline.getNode());

        // 尚未开始（今天 09-21，起点 09-22）
        assertFalse(gate.check(context(plan(StocktakingPlan.STATUS_RUNNING, MONDAY.plusDays(1), MONDAY.plusDays(2)), MONDAY)).isPassed());

        // 已过截止（今天 09-21，截止 09-20）
        assertFalse(gate.check(context(plan(StocktakingPlan.STATUS_RUNNING, MONDAY.minusDays(2), MONDAY.minusDays(1)), MONDAY)).isPassed());

        // 起止当天都属于窗内
        assertTrue(gate.check(context(plan(StocktakingPlan.STATUS_RUNNING, MONDAY, MONDAY), MONDAY)).isPassed());
        assertTrue(gate.check(context(plan(StocktakingPlan.STATUS_RUNNING, MONDAY.minusDays(1), MONDAY.plusDays(1)), MONDAY)).isPassed());
    }

    @Test
    @DisplayName("闸门⑥ 收件人资格：无待盘点 / 无企微号都不发")
    void recipientGate() {
        RecipientGate gate = new RecipientGate();
        NotifyContext context = context(plan(StocktakingPlan.STATUS_RUNNING, MONDAY, MONDAY.plusDays(1)), MONDAY);

        assertTrue(gate.check(context, InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 3)).isPassed());

        GateOutcome done = gate.check(context, InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 0));
        assertFalse(done.isPassed());
        assertEquals(GateNode.BENEFICIARY, done.getNode());

        GateOutcome noWebComId = gate.check(context, InMemorySupport.userWithoutWebComId(16018L, "徐涛", "A10470", 2));
        assertFalse(noWebComId.isPassed());
        assertEquals(GateNode.BENEFICIARY, noWebComId.getNode());
    }

    @Test
    @DisplayName("限流键：维度只到「人 + 自然日」，不带类型与计划")
    void dailyLimitKeyFormat() {
        String key = com.accutech.stocktaking.notify.support.DailyLimitGuard.keyOf(16017L, "2026-09-21");
        assertEquals("stocktaking:notice:daily:16017:2026-09-21", key);
    }
}
