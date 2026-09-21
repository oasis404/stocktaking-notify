package com.accutech.stocktaking.notify.gate;

import com.accutech.stocktaking.notify.model.GateNode;
import com.accutech.stocktaking.notify.model.NotifyContext;
import com.accutech.stocktaking.notify.model.StocktakingPlan;

/**
 * 闸门①：计划状态必须是「进行中」。
 *
 * <p>为什么放在最前面：通知与盘点回写都只关联"本期正在进行的计划"。
 * 未进行 / 已完成的计划若还能发通知，就会出现"给历史计划催办"这类范围外打扰。</p>
 */
public final class PlanStatusGate implements PlanGate {

    @Override
    public GateNode node() {
        return GateNode.GATE_STATUS;
    }

    @Override
    public GateOutcome check(NotifyContext context) {
        StocktakingPlan plan = context.getPlan();
        if (plan.isRunning()) {
            return GateOutcome.pass();
        }
        return GateOutcome.block(GateNode.GATE_STATUS,
                "计划级闸门：计划状态不是「进行中」（当前=" + plan.statusText() + "），不发送任何通知");
    }
}
