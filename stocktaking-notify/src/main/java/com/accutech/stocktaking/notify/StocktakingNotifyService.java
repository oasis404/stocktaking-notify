package com.accutech.stocktaking.notify;

import com.accutech.stocktaking.notify.gate.GateOutcome;
import com.accutech.stocktaking.notify.gate.HolidayGate;
import com.accutech.stocktaking.notify.gate.PersonGate;
import com.accutech.stocktaking.notify.gate.PlanGate;
import com.accutech.stocktaking.notify.gate.PlanStatusGate;
import com.accutech.stocktaking.notify.gate.RecipientGate;
import com.accutech.stocktaking.notify.gate.RoleGate;
import com.accutech.stocktaking.notify.gate.TimeWindowGate;
import com.accutech.stocktaking.notify.model.GateNode;
import com.accutech.stocktaking.notify.model.NotifyContext;
import com.accutech.stocktaking.notify.model.NotifyDecision;
import com.accutech.stocktaking.notify.model.NotifyResult;
import com.accutech.stocktaking.notify.model.NotifySummary;
import com.accutech.stocktaking.notify.model.NotifyType;
import com.accutech.stocktaking.notify.model.RespUser;
import com.accutech.stocktaking.notify.model.StocktakingPlan;
import com.accutech.stocktaking.notify.port.DailyLimitStore;
import com.accutech.stocktaking.notify.port.MessageSender;
import com.accutech.stocktaking.notify.port.NotifyAuditRepository;
import com.accutech.stocktaking.notify.port.RespUserProvider;
import com.accutech.stocktaking.notify.send.GroupDispatcher;
import com.accutech.stocktaking.notify.send.PendingSend;
import com.accutech.stocktaking.notify.support.DailyLimitGuard;
import com.accutech.stocktaking.notify.support.HolidayCalendar;
import com.accutech.stocktaking.notify.support.MessageTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 盘点通知引擎：<b>唯一的通知出口</b>。
 *
 * <p>无论触发来源是"计划状态改为进行中""每天 16:45 定时任务"还是"运维手工诊断"，
 * 都必须穿过同一套闸门，且<b>没有任何绕过开关</b>（真实发送时时间窗、节假日、状态一律强制生效）。</p>
 *
 * <h3>闸门链</h3>
 * <pre>
 * 计划级：①状态(GATE-STATUS) → ②节假日(GATE-HOLIDAY,START豁免) → ③时间窗(GATE-WINDOW) → ④清单(GATE-LIST,含接收权限过滤)
 * 逐人：  ⑤收件人资格(BENEFICIARY) → ⑥角色过滤(GATE-ROLE,可选) → ⑦一天一条(DAY-LIMIT) → 待群发
 * 发送：  按相同文案分组群发（分片 + 坏账号剔除重发）
 * 汇总：  SUMMARY（发送/跳过/失败/演练）
 * </pre>
 *
 * <h3>设计取向：宁可不通知，也不要乱通知</h3>
 * <ul>
 *   <li>任何一处判断不出来（没有截止时间、清单查不到、状态不是进行中、同名无法唯一定位），一律<b>不发</b>；</li>
 *   <li>收件人只来自"本次盘点清单"，账号只按用户ID精确匹配，<b>不做姓名兜底</b>（同名会发给非责任人）；</li>
 *   <li>接收方资格闸门："权限点存在即启用"——由 {@code RespUserProvider} 实现在清单层过滤
 *       （持有接收权限的角色成员才进入收件人清单，对应生产实现的 RECEIVE-PERM 语义）；</li>
 *   <li>同一责任人同一自然日只发一条，开始通知与提醒、跨计划共用同一把锁。</li>
 * </ul>
 *
 * <p><b>测试模式已移除（2026-09-24 与生产实现同步）</b>：白名单联调开关整体删除，
 * "只发给个别人"的诉求由接收权限点的角色勾选承担（权限点存在即启用，无任何参数开关）。</p>
 */
public final class StocktakingNotifyService {

    private static final Logger log = LoggerFactory.getLogger(StocktakingNotifyService.class);

    private static final String TAG = "[盘点通知]";

    private final List<PlanGate> planGates;

    private final List<PersonGate> personGates;

    private final RespUserProvider respUserProvider;

    private final DailyLimitGuard dailyLimitGuard;

    private final MessageTemplate messageTemplate;

    private final GroupDispatcher dispatcher;

    /** 发送留痕（可为 null = 不落库） */
    private final NotifyAuditRepository auditRepository;

    public StocktakingNotifyService(List<PlanGate> planGates, List<PersonGate> personGates, RespUserProvider respUserProvider,
                                    DailyLimitGuard dailyLimitGuard,
                                    MessageTemplate messageTemplate, GroupDispatcher dispatcher,
                                    NotifyAuditRepository auditRepository) {
        this.planGates = planGates;
        this.personGates = personGates;
        this.respUserProvider = respUserProvider;
        this.dailyLimitGuard = dailyLimitGuard;
        this.messageTemplate = messageTemplate;
        this.dispatcher = dispatcher;
        this.auditRepository = auditRepository;
    }

    /**
     * 发送盘点开始通知（真实发送）
     *
     * @param plan 盘点计划
     * @param today 业务日期
     */
    public List<NotifyResult> sendStartedNotify(StocktakingPlan plan, LocalDate today) {
        return notify(plan, NotifyType.START, today, false);
    }

    /**
     * 发送盘点截止提醒（真实发送）
     *
     * <p>是否使用「最后一天」文案由内部按截止时间推导，调用方不需要传。</p>
     */
    public List<NotifyResult> sendDailyRemind(StocktakingPlan plan, LocalDate today) {
        return notify(plan, NotifyType.REMIND, today, false);
    }

    /**
     * 统一入口
     *
     * @param plan 盘点计划
     * @param type 通知类型
     * @param today 业务日期（"今天"）
     * @param dryRun true-演练：只做只读判定与文案预览，不占用限流键、不发送
     * @return 逐人执行结果；整批被计划级闸门拦下时只返回一条带 gateNode 的说明行
     */
    public List<NotifyResult> notify(StocktakingPlan plan, NotifyType type, LocalDate today, boolean dryRun) {
        List<NotifyResult> results = new ArrayList<NotifyResult>();
        if (plan == null || plan.getPlanId() == null) {
            log.error("{}[INIT] 结果=失败 原因=盘点计划为空，未发送任何通知", TAG);
            return results;
        }
        NotifyContext context = new NotifyContext(plan, type, today, dryRun);

        // 节点①②③：计划级闸门（状态 → 节假日 → 时间窗）
        for (PlanGate gate : planGates) {
            GateOutcome outcome = gate.check(context);
            if (!outcome.isPassed()) {
                return gateOnly(context, outcome.getNode(), outcome.getReason());
            }
            log.info("{}[{}] 结果=通过 计划={} 类型={} 日期={}", TAG, gate.node().label(), plan.getPlanId(),
                    type.code(), context.bizDate());
        }

        // 节点④：清单闸门 —— 收件人与数量只取本次计划（planId）的清单快照
        List<RespUser> respUsers;
        try {
            respUsers = respUserProvider.findDeliverable(plan.getPlanId());
        } catch (Exception e) {
            log.error("{}[GATE-LIST] 结果=失败 计划={} 原因={}", TAG, plan.getPlanId(), e.getMessage(), e);
            return gateOnly(context, GateNode.GATE_LIST, "计划级闸门：责任人清单查询异常：" + e.getMessage());
        }
        if (respUsers == null || respUsers.isEmpty()) {
            return gateOnly(context, GateNode.GATE_LIST,
                    "计划级闸门：本次清单无责任人数据（无责任人资产、无有效账号、未生成清单快照，或全部资产已盘完）");
        }
        int pendingTotal = 0;
        for (RespUser user : respUsers) {
            pendingTotal += user.getAssetPending();
        }
        Map<String, Integer> undeliverable = safeStats(plan.getPlanId());
        log.info("{}[GATE-LIST] 结果=成功 计划={} 可投递责任人={} 待盘点合计={} 演练={} 无法投递资产[无责任人={} 仅姓名无ID={} 系统查无此人={} 已停用离职={}]",
                TAG, plan.getPlanId(), respUsers.size(), pendingTotal, dryRun,
                undeliverable.get("no_resp"), undeliverable.get("name_only"),
                undeliverable.get("user_missing"), undeliverable.get("user_disabled"));

        // 节点⑤：接收方资格（RECEIVE-PERM）——"权限点存在即启用"，由 RespUserProvider 实现在
        // 清单层过滤：只有持有接收权限的角色成员才会出现在 findDeliverable 的返回里。
        // 引擎不做二次判定，也不提供任何绕过参数（与生产实现对齐，2026-09-24 起测试模式整体移除）。

        // 节点⑥⑦：逐人判定 + 占位（单人异常不影响其余人）
        Map<String, List<PendingSend>> groups = new LinkedHashMap<String, List<PendingSend>>();
        for (RespUser user : respUsers) {
            NotifyResult row = NotifyResult.personRow(plan, type, user);
            // 占位成功后若在「入队前」抛异常，占用的限流键必须在 catch 里回收，否则该人当天彻底收不到
            String occupiedDailyKey = null;
            try {
                // 逐人闸门（收件人资格）
                GateOutcome personOutcome = checkPersonGates(context, user);
                if (!personOutcome.isPassed()) {
                    row.skip(personOutcome.getNode(), personOutcome.getReason());
                    log.warn("{}[{}] 结果=跳过 计划={} 责任人={} 工号={} 原因={}", TAG, personOutcome.getNode().label(),
                            plan.getPlanId(), user.getRespUserName(), user.getAccount(), personOutcome.getReason());
                    results.add(row);
                    continue;
                }

                String content = messageTemplate.build(context, user);
                row.setContent(content);
                String dailyKey = DailyLimitGuard.keyOf(user.getUserId(), context.bizDate());

                // 演练：只读探测限流键，让预览能反映"今天这个人还会不会被发到"
                if (dryRun) {
                    boolean occupied = dailyLimitGuard.isOccupied(dailyKey);
                    if (occupied) {
                        row.skip(GateNode.DAY_LIMIT, "演练模式：当日已向该责任人发送过盘点通知，真实发送会被「一天一条」拦下（限流键："
                                + dailyKey + "）");
                    } else {
                        row.preview("演练模式：未占限流键、未调用发送接口（当日限流键可用）");
                    }
                    log.info("{}[PREVIEW] 结果={} 计划={} 类型={} 责任人={} 企微号={} 待盘点={} 限流键={}", TAG,
                            occupied ? "跳过(当日已发)" : "演练", plan.getPlanId(), type.code(),
                            user.getRespUserName(), user.getWebComId(), user.getAssetPending(), dailyKey);
                    results.add(row);
                    continue;
                }

                // 一天一条：先占位再发送（失败会在群发阶段释放）；
                // 硬承诺：任何类型（含开始通知）撞当日已占键一律跳过，开始通知漏了由次日提醒兜底
                if (!dailyLimitGuard.tryOccupy(dailyKey, type.code() + ":" + plan.getPlanId())) {
                    row.skip(GateNode.DAY_LIMIT, "当日已向该责任人发送过盘点通知（一天一条，限流键：" + dailyKey + "）");
                    log.warn("{}[DAY-LIMIT] 结果=跳过 计划={} 类型={} 责任人={} 限流键={}", TAG, plan.getPlanId(),
                            type.code(), user.getRespUserName(), dailyKey);
                    results.add(row);
                    continue;
                }
                occupiedDailyKey = dailyKey;

                row.pending("待群发（与文案相同的责任人合并为一次群发调用）");
                List<PendingSend> groupItems = groups.get(content);
                if (groupItems == null) {
                    groupItems = new ArrayList<PendingSend>();
                    groups.put(content, groupItems);
                }
                groupItems.add(new PendingSend(user, row, dailyKey));
            } catch (Exception e) {
                row.failed("通知判定异常：" + e.getMessage());
                if (occupiedDailyKey != null) {
                    try {
                        dailyLimitGuard.release(occupiedDailyKey);
                    } catch (Exception releaseError) {
                        log.warn("{}[DAY-LIMIT] 结果=注意 计划={} 责任人={} 原因=释放当日限流键失败（当日可能不再补发） 限流键={}",
                                TAG, plan.getPlanId(), user.getRespUserName(), occupiedDailyKey);
                    }
                }
                log.error("{}[SEND] 结果=失败 计划={} 责任人={} 原因=通知判定异常", TAG, plan.getPlanId(),
                        user.getRespUserName(), e);
            }
            results.add(row);
        }

        // 节点⑧：按文案分组群发 → 汇总
        try {
            dispatcher.dispatch(plan.getPlanId(), type, groups);
        } catch (Exception e) {
            // 防御性兜底：dispatch 内部已逐片兜住发送异常，这里拦住其余未知异常，
            // 保证定时任务 / 状态变更接口不被发送环节拖垮；
            // 同时释放限流键并把仍停留在「待群发」的结果行标成失败（汇总按失败统计，不混入跳过）
            log.error("{}[SEND] 结果=异常 计划={} 类型={} 原因=群发调度异常", TAG, plan.getPlanId(), type.code(), e);
            for (List<PendingSend> groupItems : groups.values()) {
                for (PendingSend item : groupItems) {
                    if (NotifyDecision.PENDING == item.getRow().getDecision()) {
                        if (item.getDailyKey() != null) {
                            try {
                                dailyLimitGuard.release(item.getDailyKey());
                            } catch (Exception releaseError) {
                                log.warn("{}[SEND] 结果=注意 计划={} 责任人={} 原因=释放当日限流键失败（当日可能不再补发） 限流键={}",
                                        TAG, plan.getPlanId(), item.getRow().getRespUserName(), item.getDailyKey());
                            }
                        }
                        item.getRow().failed("群发调度异常：" + e.getMessage());
                    }
                }
            }
        }
        NotifySummary summary = NotifySummary.of(results);
        log.info("{}[SUMMARY] 计划={} 类型={} 可投递责任人={} {} 演练={} 一天一条=生效", TAG,
                plan.getPlanId(), type.code(), respUsers.size(), summary, dryRun);
        safeAudit(context, results);
        return results;
    }

    /** 逐人闸门按注册顺序判定，任一不过即返回 */
    private GateOutcome checkPersonGates(NotifyContext context, RespUser user) {
        for (PersonGate gate : personGates) {
            GateOutcome outcome = gate.check(context, user);
            if (!outcome.isPassed()) {
                return outcome;
            }
        }
        return GateOutcome.pass();
    }

    /** 计划级闸门拦截：写一条标准日志并返回只含该说明的结果集 */
    private List<NotifyResult> gateOnly(NotifyContext context, GateNode node, String reason) {
        log.warn("{}{} 结果=跳过 计划={} 类型={} 演练={} 原因={}", TAG, "[" + node.label() + "]",
                context.getPlan().getPlanId(), context.getType().code(), context.isDryRun(), reason);
        List<NotifyResult> results = new ArrayList<NotifyResult>(1);
        results.add(NotifyResult.gateRow(context.getPlan(), context.getType(), node, reason));
        safeAudit(context, results);
        return results;
    }

    /**
     * 发送留痕落库（真实执行才落，演练不落）；留痕失败只记日志，不影响通知主流程
     */
    private void safeAudit(NotifyContext context, List<NotifyResult> results) {
        if (auditRepository == null || context.isDryRun()) {
            return;
        }
        try {
            auditRepository.save(context.getPlan().getPlanId(), context.getType(), context.bizDate(), results);
        } catch (Exception e) {
            log.warn("{}[AUDIT] 结果=注意 计划={} 类型={} 原因=发送留痕落库失败（不影响通知，但当日对账缺这一批）: {}",
                    TAG, context.getPlan().getPlanId(), context.getType().code(), e.getMessage());
        }
    }

    private Map<String, Integer> safeStats(Integer planId) {
        try {
            Map<String, Integer> stats = respUserProvider.undeliverableStats(planId);
            return stats == null ? Collections.<String, Integer>emptyMap() : stats;
        } catch (Exception e) {
            log.warn("{}[GATE-LIST] 结果=注意 计划={} 原因=无法投递资产统计失败（不影响通知）", TAG, planId, e);
            return Collections.<String, Integer>emptyMap();
        }
    }

    private String safe(String text) {
        return text == null || text.trim().length() == 0 ? "-" : text;
    }

    /**
     * 引擎装配器：默认装配「状态 → 节假日 → 时间窗」三道计划级闸门与「收件人资格」逐人闸门，
     * 接入方只需提供数据源、发送器与限流存储。
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private List<PlanGate> planGates;

        private List<PersonGate> personGates;

        private RespUserProvider respUserProvider;

        private DailyLimitStore dailyLimitStore;

        private MessageSender messageSender;

        private MessageTemplate messageTemplate = new MessageTemplate();

        private HolidayCalendar holidayCalendar = new HolidayCalendar(null);

        /** 正式角色过滤：非空时追加 GATE-ROLE 逐人闸门（只发给这些角色的成员） */
        private Collection<String> notifyRoles;

        private NotifyAuditRepository auditRepository;

        public Builder planGates(List<PlanGate> planGates) {
            this.planGates = planGates;
            return this;
        }

        public Builder personGates(List<PersonGate> personGates) {
            this.personGates = personGates;
            return this;
        }

        public Builder respUserProvider(RespUserProvider respUserProvider) {
            this.respUserProvider = respUserProvider;
            return this;
        }

        public Builder dailyLimitStore(DailyLimitStore dailyLimitStore) {
            this.dailyLimitStore = dailyLimitStore;
            return this;
        }

        public Builder messageSender(MessageSender messageSender) {
            this.messageSender = messageSender;
            return this;
        }

        public Builder messageTemplate(MessageTemplate messageTemplate) {
            this.messageTemplate = messageTemplate;
            return this;
        }

        public Builder holidayCalendar(HolidayCalendar holidayCalendar) {
            this.holidayCalendar = holidayCalendar;
            return this;
        }

        /**
         * 配置正式角色过滤（GATE-ROLE 逐人闸门）：只把通知发给这些角色的成员
         *
         * <p>这是长期生效的正式闸门，不影响"一天一条"。
         * 不调用或传空 = 不过滤（默认行为不变）。角色 key/名称都支持，忽略大小写。
         * 生产实现的"只发给个别人"走的是接收权限点（RECEIVE-PERM，在清单提供方过滤），
         * 本闸门供接入方按角色维度做同等过滤。</p>
         */
        public Builder notifyRoles(Collection<String> roles) {
            this.notifyRoles = roles;
            return this;
        }

        /** 配置发送留痕落库（不配 = 不落库） */
        public Builder notifyAuditRepository(NotifyAuditRepository auditRepository) {
            this.auditRepository = auditRepository;
            return this;
        }

        public StocktakingNotifyService build() {
            if (respUserProvider == null) {
                throw new IllegalStateException("必须提供 respUserProvider（收件人数据源）");
            }
            if (messageSender == null) {
                throw new IllegalStateException("必须提供 messageSender（消息发送器）");
            }
            if (dailyLimitStore == null) {
                throw new IllegalStateException("必须提供 dailyLimitStore（一天一条限流存储）");
            }
            List<PlanGate> effectivePlanGates = planGates != null ? planGates
                    : new ArrayList<PlanGate>(Arrays.<PlanGate>asList(
                            new PlanStatusGate(), new HolidayGate(holidayCalendar), new TimeWindowGate()));
            List<PersonGate> effectivePersonGates = personGates != null ? personGates
                    : new ArrayList<PersonGate>(Collections.<PersonGate>singletonList(new RecipientGate()));
            if (notifyRoles != null && !notifyRoles.isEmpty() && personGates == null) {
                effectivePersonGates.add(new RoleGate(notifyRoles));
            }
            DailyLimitGuard guard = new DailyLimitGuard(dailyLimitStore);
            GroupDispatcher dispatcher = new GroupDispatcher(messageSender, guard);
            return new StocktakingNotifyService(effectivePlanGates, effectivePersonGates, respUserProvider,
                    guard, messageTemplate, dispatcher, auditRepository);
        }
    }
}
