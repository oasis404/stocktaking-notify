package com.accutech.stocktaking.notify.gate;

import com.accutech.stocktaking.notify.model.GateNode;

/**
 * 闸门判定结果：通过 / 被拦下（带原因）。
 */
public final class GateOutcome {

    private static final GateOutcome PASSED = new GateOutcome(null, null);

    private final GateNode node;

    private final String reason;

    private GateOutcome(GateNode node, String reason) {
        this.node = node;
        this.reason = reason;
    }

    public static GateOutcome pass() {
        return PASSED;
    }

    public static GateOutcome block(GateNode node, String reason) {
        return new GateOutcome(node, reason);
    }

    public boolean isPassed() {
        return node == null;
    }

    /** 被拦下的闸门节点；通过时为 null */
    public GateNode getNode() {
        return node;
    }

    /** 拦下原因（可直接进日志与接口响应） */
    public String getReason() {
        return reason;
    }
}
