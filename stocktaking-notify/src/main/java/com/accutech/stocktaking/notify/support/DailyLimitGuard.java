package com.accutech.stocktaking.notify.support;

import com.accutech.stocktaking.notify.port.DailyLimitStore;

/**
 * 「一天一条」限流器。
 *
 * <p>为什么用"占位"而不是"查询 + 校验"：占位（{@code SET NX}）是原子操作，
 * 多实例并发跑、人工补跑同时发生时，也只有一个人能占成功，
 * 从根上避免"同一个人当天收到两条"。</p>
 *
 * <p><b>键的设计</b>：{@code stocktaking:notice:daily:{用户ID}:{业务日期}} ——
 * 维度只到"人 + 自然日"，<b>刻意不带通知类型与计划ID</b>。
 * 因为业务要求是"一天只能给一个人发一条"：开始通知与每日提醒、乃至不同计划之间共用同一把锁，
 * 当天先发出去哪一条，后面的全部跳过。</p>
 *
 * <p><b>失败释放</b>：发送失败时必须释放，否则"企微接口抽了一下"就变成"这个人今天彻底收不到"。</p>
 */
public final class DailyLimitGuard {

    /** 限流键有效期（秒）：25 小时足够覆盖当天，跨天自动失效 */
    public static final int TTL_SECONDS = 25 * 60 * 60;

    private final DailyLimitStore store;

    public DailyLimitGuard(DailyLimitStore store) {
        this.store = store;
    }

    /**
     * 生成限流键
     *
     * @param userId 责任人用户ID
     * @param bizDate 业务日期（yyyy-MM-dd）
     */
    public static String keyOf(Long userId, String bizDate) {
        return "stocktaking:notice:daily:" + userId + ":" + bizDate;
    }

    /**
     * 尝试占位
     *
     * @param key 限流键
     * @param value 键值（建议 "通知类型:计划ID"，便于人工排查这条是谁先占的）
     * @return true-占位成功（今天还没发过，可以发）；false-今天已经发过
     */
    public boolean tryOccupy(String key, String value) {
        return store.tryOccupy(key, value, TTL_SECONDS);
    }

    /** 只读判断（演练预览用，不产生写入） */
    public boolean isOccupied(String key) {
        return store.isOccupied(key);
    }

    /** 释放（发送失败时调用，允许当天重跑补发） */
    public void release(String key) {
        store.release(key);
    }
}
