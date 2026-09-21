package com.accutech.stocktaking.notify.gate;

import com.accutech.stocktaking.notify.model.GateNode;
import com.accutech.stocktaking.notify.model.NotifyContext;
import com.accutech.stocktaking.notify.model.RespUser;

/**
 * 逐人闸门：判定"这个责任人"能不能收到通知。
 *
 * <p>同样是纯函数：不写库、不占用限流键。
 * "一天一条"需要写限流键，因此它由 {@code DailyLimitGuard} 单独承担（见该类注释）。</p>
 */
public interface PersonGate {

    /** 本闸门的节点标识 */
    GateNode node();

    /**
     * 判定
     *
     * @param context 执行上下文
     * @param user 责任人
     * @return 通过 / 被拦下
     */
    GateOutcome check(NotifyContext context, RespUser user);
}
