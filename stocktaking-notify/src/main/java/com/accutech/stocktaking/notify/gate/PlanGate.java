package com.accutech.stocktaking.notify.gate;

import com.accutech.stocktaking.notify.model.GateNode;
import com.accutech.stocktaking.notify.model.NotifyContext;

/**
 * 计划级闸门：判定"这一期计划的这一批通知"能不能发。
 *
 * <p>实现必须是<b>纯函数</b>（只读、无副作用、可重复调用），
 * 这样"诊断看到的结果"必然等于"定时任务真实的结果"。</p>
 */
public interface PlanGate {

    /** 本闸门的节点标识 */
    GateNode node();

    /**
     * 判定
     *
     * @param context 执行上下文
     * @return 通过 / 被拦下
     */
    GateOutcome check(NotifyContext context);
}
