package com.accutech.stocktaking.notify;

import com.accutech.stocktaking.notify.model.NotifyResult;
import com.accutech.stocktaking.notify.model.NotifySummary;
import com.accutech.stocktaking.notify.model.StocktakingPlan;
import com.accutech.stocktaking.notify.support.HolidayCalendar;
import com.accutech.stocktaking.notify.testing.InMemorySupport;
import com.accutech.stocktaking.notify.testing.InMemorySupport.InMemoryDailyLimitStore;
import com.accutech.stocktaking.notify.testing.InMemorySupport.InMemoryMessageSender;
import com.accutech.stocktaking.notify.testing.InMemorySupport.InMemoryRespUserProvider;
import com.accutech.stocktaking.notify.testing.InMemorySupport.InMemoryTestModeConfigProvider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

/**
 * 演练演示：跑一遍典型场景，把结果打印出来。
 *
 * <p>它不是断言测试，而是"可执行的文档"：{@code mvn test} 之后就能看到
 * 闸门日志（GATE-STATUS / GATE-WINDOW / DAY-LIMIT …）与最终判定，
 * 新人接手时先跑这个文件最容易理解整条链路。</p>
 */
class NotifyWalkthroughTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    private static StocktakingPlan plan() {
        return new StocktakingPlan(23, "2026年9月盘点", StocktakingPlan.STATUS_RUNNING, "进行中",
                LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 22));
    }

    private static void print(String title, List<NotifyResult> results) {
        System.out.println();
        System.out.println("===== " + title + " =====");
        System.out.println(NotifySummary.of(results));
        for (NotifyResult row : results) {
            System.out.println(String.format("  [%s] %s | %s | %s",
                    row.getDecision(),
                    row.getRespUserName() == null ? "(整批)" : row.getRespUserName(),
                    row.getGateNode() == null ? "-" : row.getGateNode().label(),
                    row.getReason()));
        }
    }

    @Test
    @DisplayName("演练演示：正常发送 / 一天一条 / 节假日 / 测试模式")
    void walkthrough() {
        InMemoryRespUserProvider provider = new InMemoryRespUserProvider().withUsers(23,
                InMemorySupport.user(16017L, "余楚贤", "A10529", "yuchuxian", 1),
                InMemorySupport.user(16018L, "徐涛", "A10470", "xutao", 2));
        InMemoryMessageSender sender = new InMemoryMessageSender();
        InMemoryDailyLimitStore store = new InMemoryDailyLimitStore();
        StocktakingNotifyService service = StocktakingNotifyService.builder()
                .respUserProvider(provider)
                .messageSender(sender)
                .dailyLimitStore(store)
                .testModeConfigProvider(new InMemoryTestModeConfigProvider())
                .build();

        // ① 正常发送
        print("① 正常发送（两人、两种文案 → 调用 " + sender.callCount() + " 次前）",
                service.sendDailyRemind(plan(), TODAY));
        System.out.println("  实际调用发送接口次数：" + sender.callCount());

        // ② 同一天再跑一次 → 一天一条
        print("② 同一天再跑一次（应被一天一条拦下）", service.sendDailyRemind(plan(), TODAY));

        // ③ 演练预览
        print("③ 演练预览（不发送、不占限流键）", service.notify(plan(), com.accutech.stocktaking.notify.model.NotifyType.REMIND, TODAY, true));

        // ④ 节假日
        StocktakingNotifyService holidayService = StocktakingNotifyService.builder()
                .respUserProvider(provider)
                .messageSender(sender)
                .dailyLimitStore(store)
                .holidayCalendar(HolidayCalendar.of(TODAY.toString()))
                .build();
        print("④ 把今天设为节假日（整批不发）", holidayService.sendDailyRemind(plan(), TODAY));
    }
}
