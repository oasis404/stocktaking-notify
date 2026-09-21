package com.accutech.stocktaking.notify.support;

import com.accutech.stocktaking.notify.model.RespUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 测试模式：把"只发给白名单"这件事做成 fail-closed。
 *
 * <p>配置格式（{@code |} 分隔）：</p>
 * <pre>
 * testMode=true|accounts=工号1,工号2|userIds=1,2|deptNames=财务部|roleKeys=角色key|setBy=操作人|setAt=时间
 * </pre>
 *
 * <p><b>安全语义</b>：只要 {@code testMode=true}，即使一个白名单条件都没配，
 * 也<b>一条都不发</b>（而不是"没配条件就等于不限"）。测试模式不设自动过期，只能显式关闭 ——
 * 因为"过期自动放开"会直接变成"发给全员"，而多发出去的企微消息收不回来。</p>
 */
public final class TestModeScope {

    private static final String FLAG_ON = "testMode=true";

    private static final TestModeScope OFF = new TestModeScope(null, false,
            Collections.<String>emptyList(), Collections.<String>emptyList(),
            Collections.<String>emptyList(), Collections.<String>emptyList());

    private final String rawConfig;

    private final boolean on;

    private final List<String> accounts;

    private final List<String> userIds;

    private final List<String> deptNames;

    private final List<String> roles;

    private TestModeScope(String rawConfig, boolean on, List<String> accounts, List<String> userIds,
                          List<String> deptNames, List<String> roles) {
        this.rawConfig = rawConfig;
        this.on = on;
        this.accounts = accounts;
        this.userIds = userIds;
        this.deptNames = deptNames;
        this.roles = roles;
    }

    /**
     * 解析配置文本（null / 空白 / 不以 testMode=true 开头 → 未开启）
     */
    public static TestModeScope parse(String config) {
        if (config == null || config.trim().length() == 0) {
            return OFF;
        }
        String text = config.trim();
        if (!text.startsWith(FLAG_ON)) {
            return OFF;
        }
        List<String> accounts = new ArrayList<String>();
        List<String> userIds = new ArrayList<String>();
        List<String> deptNames = new ArrayList<String>();
        List<String> roles = new ArrayList<String>();
        for (String part : text.split("\\|")) {
            int idx = part.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = part.substring(0, idx).trim();
            String value = part.substring(idx + 1);
            if ("accounts".equals(key)) {
                accounts = split(value);
            } else if ("userIds".equals(key)) {
                userIds = split(value);
            } else if ("deptNames".equals(key)) {
                deptNames = split(value);
            } else if ("roleKeys".equals(key) || "roles".equals(key)) {
                roles = split(value);
            }
        }
        return new TestModeScope(text, true, accounts, userIds, deptNames, roles);
    }

    public boolean isOn() {
        return on;
    }

    /**
     * 是否配置了任意白名单条件（开启但无条件 = 一个人都不发）
     */
    public boolean hasCondition() {
        return !accounts.isEmpty() || !userIds.isEmpty() || !deptNames.isEmpty() || !roles.isEmpty();
    }

    /**
     * 是否命中白名单：工号 / 用户ID / 部门 / 角色（任一命中即发送，忽略大小写）
     */
    public boolean matches(RespUser user) {
        if (!on) {
            return true;
        }
        if (containsIgnoreCase(accounts, user.getAccount())) {
            return true;
        }
        if (user.getUserId() != null && containsIgnoreCase(userIds, String.valueOf(user.getUserId()))) {
            return true;
        }
        if (containsIgnoreCase(deptNames, user.getDeptName())) {
            return true;
        }
        for (String roleKey : split(user.getRoleKeys())) {
            if (containsIgnoreCase(roles, roleKey)) {
                return true;
            }
        }
        for (String roleName : split(user.getRoleNames())) {
            if (containsIgnoreCase(roles, roleName)) {
                return true;
            }
        }
        return false;
    }

    /** 配置原文（用于日志与报错，便于操作人核对自己配了什么） */
    public String describe() {
        return rawConfig == null ? "未配置" : rawConfig;
    }

    private static List<String> split(String text) {
        List<String> values = new ArrayList<String>();
        if (text == null || text.trim().length() == 0) {
            return values;
        }
        for (String value : text.split(",")) {
            if (value != null && value.trim().length() > 0) {
                values.add(value.trim());
            }
        }
        return values;
    }

    private static boolean containsIgnoreCase(List<String> values, String target) {
        if (values.isEmpty() || target == null || target.trim().length() == 0) {
            return false;
        }
        for (String value : values) {
            if (value.equalsIgnoreCase(target.trim())) {
                return true;
            }
        }
        return false;
    }
}
