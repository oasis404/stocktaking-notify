package com.accutech.stocktaking.notify.gate;

import com.accutech.stocktaking.notify.model.GateNode;
import com.accutech.stocktaking.notify.model.NotifyContext;
import com.accutech.stocktaking.notify.model.RespUser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 闸门（逐人）：角色过滤 —— 只把通知发给配置角色的成员。
 *
 * <p><b>与 {@code TestModeScope} 的 roleKeys 匹配不是一回事</b>：那是临时联调开关
 * （不设自动过期、且会跳过"一天一条"），不能当正式的角色过滤用；
 * 本闸门是正式闸门 —— 配置长期生效、不影响一天一条语义、被拦下的人照常出现在结果明细里
 * （decision=SKIP + GATE-ROLE + 原因），诊断接口看得到"谁因为角色没收到"。</p>
 *
 * <p><b>未配置任何角色（null / 空）= 闸门关闭</b>，所有人放行 —— 保持引擎默认行为不变。</p>
 *
 * <p>匹配规则：{@code roleKeys} 与 {@code roleNames} 都是逗号分隔，
 * 任一命中即放行（忽略大小写与首尾空格）。</p>
 */
public final class RoleGate implements PersonGate {

    private final List<String> allowedRoles;

    public RoleGate(Collection<String> allowedRoles) {
        if (allowedRoles == null) {
            this.allowedRoles = Collections.<String>emptyList();
        } else {
            this.allowedRoles = new ArrayList<String>();
            for (String role : allowedRoles) {
                if (role != null && role.trim().length() > 0) {
                    this.allowedRoles.add(role.trim());
                }
            }
        }
    }

    /** 是否启用（配置了至少一个角色才启用） */
    public boolean isEnabled() {
        return !allowedRoles.isEmpty();
    }

    @Override
    public GateNode node() {
        return GateNode.GATE_ROLE;
    }

    @Override
    public GateOutcome check(NotifyContext context, RespUser user) {
        if (!isEnabled()) {
            return GateOutcome.pass();
        }
        if (matches(user.getRoleKeys()) || matches(user.getRoleNames())) {
            return GateOutcome.pass();
        }
        return GateOutcome.block(GateNode.GATE_ROLE,
                "角色不匹配（责任人角色=[" + safe(user.getRoleKeys()) + "/" + safe(user.getRoleNames())
                        + "]，允许角色=" + allowedRoles + "）");
    }

    private boolean matches(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.trim().length() == 0) {
            return false;
        }
        for (String value : commaSeparated.split(",")) {
            String trimmed = value == null ? "" : value.trim();
            for (String allowed : allowedRoles) {
                if (trimmed.equalsIgnoreCase(allowed)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String safe(String text) {
        return text == null || text.trim().length() == 0 ? "-" : text;
    }
}
