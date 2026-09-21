package com.accutech.stocktaking.notify.model;

/**
 * 单条通知的最终判定结果。
 *
 * <p>每个责任人一定有一条结果，取值只有这四种 —— 排错时只要看 decision 就能知道
 * "发出去了 / 被闸门拦了 / 发失败了 / 只是演练"。</p>
 */
public enum NotifyDecision {

    /** 发送成功 */
    SEND,

    /** 跳过（未发送，原因见 reason） */
    SKIP,

    /** 发送失败（企微或链路异常，原因见 reason） */
    FAIL,

    /** 演练：只做判定与文案预览，不发送 */
    PREVIEW
}
