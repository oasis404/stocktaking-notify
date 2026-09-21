package com.accutech.stocktaking.notify.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 一次通知执行的上下文。
 *
 * <p>把"今天"固定在这里，而不是每次判定都去 {@code LocalDate.now()}：
 * 一是保证一批通知内所有闸门、限流键、文案用的是同一天（跨零点也不会自相矛盾），
 * 二是让单元测试可以自由构造日期。</p>
 */
public final class NotifyContext {

    private static final DateTimeFormatter BIZ_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final StocktakingPlan plan;

    private final NotifyType type;

    /** 业务日期（通知执行的"今天"） */
    private final LocalDate today;

    /** 演练模式：只判定与预览，不占用限流键、不发送 */
    private final boolean dryRun;

    /** 测试模式是否开启（由服务在过完计划级闸门后回填，供"测试模式跳过一天一条"使用） */
    private boolean testModeOn;

    public NotifyContext(StocktakingPlan plan, NotifyType type, LocalDate today, boolean dryRun) {
        this.plan = plan;
        this.type = type;
        this.today = today;
        this.dryRun = dryRun;
    }

    public StocktakingPlan getPlan() {
        return plan;
    }

    public NotifyType getType() {
        return type;
    }

    public LocalDate getToday() {
        return today;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean isTestModeOn() {
        return testModeOn;
    }

    public void setTestModeOn(boolean testModeOn) {
        this.testModeOn = testModeOn;
    }

    /** 业务日期文本（yyyy-MM-dd），用于限流键与日志 */
    public String bizDate() {
        return today.format(BIZ_DATE);
    }

    /**
     * 距截止时间的剩余自然日（截止日当天为 0，已过期也为 0）
     */
    public long remainDays() {
        LocalDate end = plan.getEndDate();
        if (end == null) {
            return 0L;
        }
        long days = ChronoUnit.DAYS.between(today, end);
        return days < 0 ? 0L : days;
    }

    /** 今天是否就是截止日（决定提醒文案是否升级为"最后一天"） */
    public boolean isDeadlineDay() {
        LocalDate end = plan.getEndDate();
        return end != null && end.equals(today);
    }
}
