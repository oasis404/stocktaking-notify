package com.accutech.stocktaking.notify.model;

/**
 * 一条执行结果：要么是"整批被计划级闸门拦下"的说明行，要么是"某个责任人的逐人判定行"。
 *
 * <p>排错时只看三个字段就够：{@code decision}（发没发）、{@code gateNode}（被哪关拦下）、
 * {@code reason}（为什么）。</p>
 */
public final class NotifyResult {

    private final Integer planId;

    private final String planName;

    private final NotifyType noticeType;

    private Long respUserId;

    private String respUserName;

    private String account;

    private String webComId;

    private String deptName;

    private Integer assetTotal;

    private Integer assetPending;

    private NotifyDecision decision;

    /** 被哪道闸门拦下（通过或成功时为 null） */
    private GateNode gateNode;

    /** 判定原因 / 失败原因 */
    private String reason;

    /** 实际发送（或演练预览）的文案 */
    private String content;

    private NotifyResult(Integer planId, String planName, NotifyType noticeType) {
        this.planId = planId;
        this.planName = planName;
        this.noticeType = noticeType;
    }

    /**
     * 计划级闸门拦截行（整批不发时只有这一条）
     */
    public static NotifyResult gateRow(StocktakingPlan plan, NotifyType type, GateNode node, String reason) {
        NotifyResult row = new NotifyResult(plan.getPlanId(), plan.getPlanName(), type);
        row.gateNode = node;
        row.decision = NotifyDecision.SKIP;
        row.reason = reason;
        return row;
    }

    /**
     * 逐人判定行
     */
    public static NotifyResult personRow(StocktakingPlan plan, NotifyType type, RespUser user) {
        NotifyResult row = new NotifyResult(plan.getPlanId(), plan.getPlanName(), type);
        row.respUserId = user.getUserId();
        row.respUserName = user.getRespUserName();
        row.account = user.getAccount();
        row.webComId = user.getWebComId();
        row.deptName = user.getDeptName();
        row.assetTotal = user.getAssetTotal();
        row.assetPending = user.getAssetPending();
        return row;
    }

    /** 标记：跳过（被逐人闸门拦下） */
    public NotifyResult skip(GateNode node, String reason) {
        this.decision = NotifyDecision.SKIP;
        this.gateNode = node;
        this.reason = reason;
        return this;
    }

    /** 标记：已通过全部闸门，等待与同文案的人合并群发 */
    public NotifyResult pending(String reason) {
        this.decision = NotifyDecision.SKIP;
        this.gateNode = null;
        this.reason = reason;
        return this;
    }

    /** 标记：发送成功 */
    public NotifyResult sent(String reason) {
        this.decision = NotifyDecision.SEND;
        this.gateNode = null;
        this.reason = reason;
        return this;
    }

    /** 标记：发送失败 */
    public NotifyResult failed(String reason) {
        this.decision = NotifyDecision.FAIL;
        this.gateNode = null;
        this.reason = reason;
        return this;
    }

    /** 标记：演练预览 */
    public NotifyResult preview(String reason) {
        this.decision = NotifyDecision.PREVIEW;
        this.gateNode = null;
        this.reason = reason;
        return this;
    }

    public Integer getPlanId() {
        return planId;
    }

    public String getPlanName() {
        return planName;
    }

    public NotifyType getNoticeType() {
        return noticeType;
    }

    public Long getRespUserId() {
        return respUserId;
    }

    public String getRespUserName() {
        return respUserName;
    }

    public String getAccount() {
        return account;
    }

    public String getWebComId() {
        return webComId;
    }

    public String getDeptName() {
        return deptName;
    }

    public Integer getAssetTotal() {
        return assetTotal;
    }

    public Integer getAssetPending() {
        return assetPending;
    }

    public NotifyDecision getDecision() {
        return decision;
    }

    public GateNode getGateNode() {
        return gateNode;
    }

    public String getReason() {
        return reason;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
