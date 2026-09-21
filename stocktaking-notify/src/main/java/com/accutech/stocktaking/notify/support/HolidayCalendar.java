package com.accutech.stocktaking.notify.support;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 工作日历：判断某天是否"节假日/周末（不发通知）"。
 *
 * <p>当前实现是<b>写死 + 周末</b>的简化版：</p>
 * <ul>
 *   <li>法定节假日：由 {@code holidays} 显式列举（换年只需改这一个集合）；</li>
 *   <li>周末：默认周六、周日都不发；公司周六上班时可关掉 {@code skipSaturday}；</li>
 *   <li>调休上班日：本简化版无法表达，若业务需要"调休日要发"，请接入工作日历表（见文档）。</li>
 * </ul>
 *
 * <p>对外只暴露 {@link #isHoliday(LocalDate)}，因此从"写死"升级到"查日历表"时，
 * 调用方一行都不用改。</p>
 */
public final class HolidayCalendar {

    /** 需要跳过的法定节假日（yyyy-MM-dd） */
    private final Set<String> holidays;

    /** 是否跳过周六 */
    private final boolean skipSaturday;

    /** 是否跳过周日 */
    private final boolean skipSunday;

    /**
     * 构造：节假日 + 周六周日都不发（默认）
     *
     * @param holidays 节假日日期（yyyy-MM-dd），可为空
     */
    public HolidayCalendar(Set<String> holidays) {
        this(holidays, true, true);
    }

    public HolidayCalendar(Set<String> holidays, boolean skipSaturday, boolean skipSunday) {
        this.holidays = holidays == null ? Collections.<String>emptySet() : new HashSet<String>(holidays);
        this.skipSaturday = skipSaturday;
        this.skipSunday = skipSunday;
    }

    /**
     * 便捷构造：把可变参数当作节假日列表
     */
    public static HolidayCalendar of(String... holidayDates) {
        return new HolidayCalendar(new HashSet<String>(Arrays.asList(holidayDates)));
    }

    /**
     * 是否属于"不通知"的日期（节假日或周末）
     *
     * @param date 待判断日期
     * @return true-不通知
     */
    public boolean isHoliday(LocalDate date) {
        if (date == null) {
            return false;
        }
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (skipSaturday && dayOfWeek == DayOfWeek.SATURDAY) {
            return true;
        }
        if (skipSunday && dayOfWeek == DayOfWeek.SUNDAY) {
            return true;
        }
        return holidays.contains(date.toString());
    }
}
