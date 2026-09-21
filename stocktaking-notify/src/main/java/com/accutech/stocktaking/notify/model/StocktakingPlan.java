package com.accutech.stocktaking.notify.model;

import java.time.LocalDate;

/**
 * 盘点计划（只保留通知引擎关心的字段）。
 *
 * <p>「一期计划 = 一份盘点清单快照」，通知的收件人与数量都只从这一期的清单里取，
 * 因此 planId 是整条链路的主键。</p>
 */
public final class StocktakingPlan {

    /** 计划状态：未进行 */
    public static final String STATUS_NOT_STARTED = "1";

    /** 计划状态：进行中 —— 只有该状态允许发送通知 */
    public static final String STATUS_RUNNING = "2";

    /** 计划状态：已完成 */
    public static final String STATUS_FINISHED = "3";

    /** 计划ID（主键） */
    private final Integer planId;

    /** 计划名称 */
    private final String planName;

    /** 计划状态：1 未进行 / 2 进行中 / 3 已完成 */
    private final String status;

    /** 状态中文名（用于日志与报错，可为空） */
    private final String statusLabel;

    /**
     * 通知时间窗起点（实际开始时间）
     *
     * <p>业务上优先取「实际开始时间」，该字段为空时才回退「计划开始时间」，
     * 接入方按同一口径传入即可；为空表示不限起点（只以状态为准）。</p>
     */
    private final LocalDate startDate;

    /** 通知时间窗终点（实际结束时间 = 盘点截止时间）；为空表示无法判断通知范围，一律不发 */
    private final LocalDate endDate;

    public StocktakingPlan(Integer planId, String planName, String status, String statusLabel,
                           LocalDate startDate, LocalDate endDate) {
        this.planId = planId;
        this.planName = planName;
        this.status = status;
        this.statusLabel = statusLabel;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public Integer getPlanId() {
        return planId;
    }

    public String getPlanName() {
        return planName;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    /** 是否处于「进行中」 */
    public boolean isRunning() {
        return STATUS_RUNNING.equals(status);
    }

    /** 状态展示文本：优先中文名，缺失时用原始值 */
    public String statusText() {
        return statusLabel != null && statusLabel.trim().length() > 0 ? statusLabel : status;
    }
}
