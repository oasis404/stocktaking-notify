package com.accutech.stocktaking.notify.gate;

import com.accutech.stocktaking.notify.model.GateNode;
import com.accutech.stocktaking.notify.model.NotifyContext;
import com.accutech.stocktaking.notify.support.HolidayCalendar;

/**
 * 闸门②：节假日与周末不通知。
 *
 * <p>人工触发（改状态 / 诊断手动发送）与定时触发同样受限 —— 这是"节假日不打扰"的唯一落地点，
 * 不存在"手工点一下就能绕过"的通道。</p>
 */
public final class HolidayGate implements PlanGate {

    private final HolidayCalendar calendar;

    public HolidayGate(HolidayCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public GateNode node() {
        return GateNode.GATE_HOLIDAY;
    }

    @Override
    public GateOutcome check(NotifyContext context) {
        if (!calendar.isHoliday(context.getToday())) {
            return GateOutcome.pass();
        }
        return GateOutcome.block(GateNode.GATE_HOLIDAY,
                "计划级闸门：节假日/周末（" + context.bizDate() + "）不通知");
    }
}
