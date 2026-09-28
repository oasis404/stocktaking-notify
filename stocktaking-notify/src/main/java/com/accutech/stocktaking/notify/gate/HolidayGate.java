package com.accutech.stocktaking.notify.gate;

import com.accutech.stocktaking.notify.model.GateNode;
import com.accutech.stocktaking.notify.model.NotifyContext;
import com.accutech.stocktaking.notify.model.NotifyType;
import com.accutech.stocktaking.notify.support.HolidayCalendar;

/**
 * 闸门②：节假日与周末不通知。
 *
 * <p>人工触发（改状态 / 诊断手动发送）与定时触发同样受限 —— 这是"节假日不打扰"的唯一落地点，
 * 不存在"手工点一下就能绕过"的通道。</p>
 *
 * <p><b>唯一豁免：开始通知（START）</b>。它由"计划状态改为进行中"这一事件触发，只发生一次；
 * 若那天恰好是周末/节假日被拦下，状态不会再变、事件不会再来，这个人将<b>永远</b>收不到
 * "计划已开始"，只能靠每日提醒间接得知。而"计划已开始"比"还剩 N 天"重要，
 * 所以提醒类（REMIND）继续受节假日限制，事件类（START）不受。</p>
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
        // START 是一次性事件通知：被节假日拦下 = 永久丢失，因此豁免（详见类注释）
        if (context.getType() == NotifyType.START) {
            return GateOutcome.pass();
        }
        if (!calendar.isHoliday(context.getToday())) {
            return GateOutcome.pass();
        }
        return GateOutcome.block(GateNode.GATE_HOLIDAY,
                "计划级闸门：节假日/周末（" + context.bizDate() + "）不通知");
    }
}
