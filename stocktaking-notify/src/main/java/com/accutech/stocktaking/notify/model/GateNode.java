package com.accutech.stocktaking.notify.model;

/**
 * 闸门节点标识。
 *
 * <p>同一套标识同时用于三种地方，保证"日志、接口响应、文档"三处能互相对照：</p>
 * <ul>
 *   <li>日志前缀：{@code [盘点通知][GATE-STATUS] 结果=跳过 ...}</li>
 *   <li>返回结果的 {@code gateNode} 字段（整批被计划级闸门拦下时）</li>
 *   <li>逐人明细的 {@code gateNode} 字段（被逐人闸门拦下时）</li>
 * </ul>
 *
 * <p>闸门顺序（前一关不过，后面的根本不会执行）：</p>
 * <pre>
 * GATE-STATUS → GATE-HOLIDAY → GATE-WINDOW → GATE-LIST → TEST-MODE
 *            → BENEFICIARY → DAY-LIMIT → 发送
 * </pre>
 */
public enum GateNode {

    /** 计划状态必须是「进行中」 */
    GATE_STATUS("GATE-STATUS", "计划状态必须为「进行中」"),

    /** 节假日与周末不通知 */
    GATE_HOLIDAY("GATE-HOLIDAY", "节假日与周末不通知"),

    /** 只在实际开始时间 ~ 盘点截止时间之间通知 */
    GATE_WINDOW("GATE-WINDOW", "只在实际开始时间~盘点截止时间之间通知"),

    /** 收件人与数量只取本次盘点清单（按 planId） */
    GATE_LIST("GATE-LIST", "收件人与数量只取本次盘点清单（按 planId）"),

    /** 测试模式白名单限制（fail-closed） */
    TEST_MODE("TEST-MODE", "测试模式白名单限制（未配置白名单时一条都不发）"),

    /** 逐人：必须是资产责任人本人 + 有待盘点资产 + 有企业微信账号 */
    BENEFICIARY("BENEFICIARY", "收件人资格：必须是有待盘点资产的资产责任人本人"),

    /** 逐人：同一责任人同一自然日只发一条 */
    DAY_LIMIT("DAY-LIMIT", "同一责任人同一自然日只发一条");

    private final String label;

    private final String description;

    GateNode(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
