package com.accutech.stocktaking.notify.port;

import com.accutech.stocktaking.notify.model.NotifyResult;
import com.accutech.stocktaking.notify.model.NotifyType;

import java.util.List;

/**
 * 发送留痕扩展点：把每次通知的逐人结果落库，事后能回答"这个人到底收到没有、为什么"。
 *
 * <p>没有留痕，事后追责 / 对账只能靠日志翻找；接入方实现本接口后，
 * 引擎会在每次<b>真实执行</b>（演练 dryRun 不落库）结束时把完整结果集交给你，
 * 包括：发送成功 / 被哪道闸门拦下 / 发送失败及原因。</p>
 *
 * <p><b>实现约定</b>：</p>
 * <ul>
 *   <li>批量写入，一张宽表即可，建议结构：
 *       {@code notify_audit(id, plan_id, plan_name, notice_type, biz_date, resp_user_id,
 *       resp_user_name, account, web_com_id, dept_name, decision, gate_node, reason, created_time)}；</li>
 *   <li>同一个 (planId, type, bizDate) 可能因"发送失败后人工补跑"出现多批留痕，
 *       不要做唯一约束挡掉，补跑记录本身就是对账依据；</li>
 *   <li>落库失败<b>不要抛出去影响发送主流程</b> —— 引擎侧已兜底，但实现侧也应自查自警。</li>
 * </ul>
 */
public interface NotifyAuditRepository {

    /**
     * 保存一次通知执行的逐人结果
     *
     * @param planId 盘点计划ID
     * @param type 通知类型
     * @param bizDate 业务日期（yyyy-MM-dd）
     * @param results 逐人结果集（整批被计划级闸门拦下时只有一条说明行）
     */
    void save(Integer planId, NotifyType type, String bizDate, List<NotifyResult> results);
}
