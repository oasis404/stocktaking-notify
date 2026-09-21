package com.accutech.stocktaking.notify.port;

import com.accutech.stocktaking.notify.model.RespUser;

import java.util.List;
import java.util.Map;

/**
 * 收件人数据源扩展点。
 *
 * <p><b>这是"只发给资产责任人本人"的落地点</b>，实现时必须满足：</p>
 * <ol>
 *   <li>只查本次计划（planId）的清单快照，不跨期、不取全量资产；</li>
 *   <li>账号只按 {@code resp_user_id} 精确匹配用户表，<b>不做姓名兜底</b>（同名会发错人）；</li>
 *   <li>匹配不到有效账号的责任人不要塞进来 —— 宁可这个人收不到，也不能发给别人。</li>
 * </ol>
 *
 * <p>参考 SQL 见 {@code examples/MyBatisRespUserProvider.sql}。</p>
 */
public interface RespUserProvider {

    /**
     * 查询本次计划中"可投递"的责任人（能精确匹配到有效账号）
     *
     * @param planId 盘点计划ID
     * @return 责任人列表；返回空表示本次清单无人可投递
     */
    List<RespUser> findDeliverable(Integer planId);

    /**
     * 统计本次清单中"无法投递"的资产分类数量，用于日志暴露数据问题（避免静默漏人）
     *
     * <p>约定 key：{@code no_resp}（无责任人）、{@code name_only}（只有姓名没有用户ID）、
     * {@code user_missing}（系统查无此人）、{@code user_disabled}（账号已停用或离职）。</p>
     *
     * @param planId 盘点计划ID
     * @return 统计结果；实现困难时可返回空 Map（只影响日志，不影响通知主流程）
     */
    Map<String, Integer> undeliverableStats(Integer planId);
}
