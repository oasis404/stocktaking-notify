package com.accutech.stocktaking.notify.gate;

import com.accutech.stocktaking.notify.model.GateNode;
import com.accutech.stocktaking.notify.model.NotifyContext;
import com.accutech.stocktaking.notify.model.StocktakingPlan;

import java.time.LocalDate;

/**
 * 闸门③：只在实际开始时间 ~ 盘点截止时间之间通知。
 *
 * <p>三种情况一律不发：</p>
 * <ul>
 *   <li><b>未设置截止时间</b>：无法判断通知范围，宁可不发；</li>
 *   <li><b>尚未开始</b>：今天早于时间窗起点；</li>
 *   <li><b>已过截止时间</b>：今天晚于时间窗终点。</li>
 * </ul>
 *
 * <p>边界按"天"处理：起止当天都属于窗内（截止日当天仍会提醒，且文案升级为"最后一天"）。</p>
 */
public final class TimeWindowGate implements PlanGate {

    @Override
    public GateNode node() {
        return GateNode.GATE_WINDOW;
    }

    @Override
    public GateOutcome check(NotifyContext context) {
        StocktakingPlan plan = context.getPlan();
        LocalDate today = context.getToday();
        LocalDate end = plan.getEndDate();
        if (end == null) {
            return GateOutcome.block(GateNode.GATE_WINDOW,
                    "时间窗闸门：未设置截止时间（实际结束时间），无法判断通知范围");
        }
        LocalDate start = plan.getStartDate();
        if (start != null && today.isBefore(start)) {
            return GateOutcome.block(GateNode.GATE_WINDOW,
                    "时间窗闸门：尚未开始（开始时间 " + start + "）");
        }
        if (today.isAfter(end)) {
            return GateOutcome.block(GateNode.GATE_WINDOW,
                    "时间窗闸门：已过截止时间（" + end + "）");
        }
        return GateOutcome.pass();
    }
}
