package com.accutech.stocktaking.notify.model;

/**
 * 通知类型。
 *
 * <p>两种类型的闸门链基本相同（开始通知豁免节假日闸门），只有文案不同；两者共享同一把「一天一条」限流键，
 * 同一个人同一天只会收到其中一条（<b>硬承诺，开始通知也不例外</b>：
 * 若当天提醒先发出，开始通知会被拦下，由第二天起的提醒兜底）。</p>
 */
public enum NotifyType {

    /** 盘点开始通知：计划状态变为「进行中」时触发 */
    START("start", "开始通知"),

    /** 盘点截止提醒：定时任务每天触发 */
    REMIND("remind", "截止提醒");

    private final String code;

    private final String label;

    NotifyType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /**
     * 按 code 解析（用于接口入参），未匹配时返回默认值
     */
    public static NotifyType of(String code, NotifyType defaultValue) {
        if (code != null) {
            for (NotifyType type : values()) {
                if (type.code.equalsIgnoreCase(code.trim())) {
                    return type;
                }
            }
        }
        return defaultValue;
    }
}
