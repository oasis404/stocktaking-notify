package com.accutech.stocktaking.notify.port;

/**
 * 「一天一条」限流存储扩展点。
 *
 * <p>生产环境请用 Redis 实现（{@code SET key value NX EX ttl}），
 * 保证多实例部署、并发补跑时同一个人当天也只发一条；示例见 {@code examples/RedisDailyLimitStore.java}。</p>
 */
public interface DailyLimitStore {

    /**
     * 占位（setIfAbsent + TTL）
     *
     * @param key 限流键
     * @param value 键值（建议写入 "通知类型:计划ID"，便于人工查看这条是谁先占的）
     * @param ttlSeconds 过期秒数（建议 25 小时，足够覆盖当天）
     * @return true-占位成功（今天还没发过）；false-已被占用（今天已经发过）
     */
    boolean tryOccupy(String key, String value, int ttlSeconds);

    /**
     * 释放占位（发送失败时调用，允许当天重跑补发）
     */
    void release(String key);

    /**
     * 只读判断是否已占用（演练预览用，不产生任何写入）
     */
    boolean isOccupied(String key);
}
