package com.accutech.stocktaking.notify.model;

/**
 * 盘点责任人（本次清单里按责任人聚合后的一行）。
 *
 * <p>接入方（通常是 SQL）需要保证：</p>
 * <ul>
 *   <li>只返回<b>本次计划（planId）清单里的资产责任人</b>；</li>
 *   <li>账号只按 {@code resp_user_id} 精确匹配用户表，<b>不要按姓名兜底匹配</b>
 *       —— 同名会匹配到另一个人，等于把通知发给了非责任人；</li>
 *   <li>{@code assetPending} 只统计本次清单里仍未盘点的数量。</li>
 * </ul>
 */
public final class RespUser {

    /** 责任人用户ID（对应 sys_user.user_id） */
    private final Long userId;

    /** 责任人姓名 */
    private final String respUserName;

    /** 责任人账号/工号（测试模式按工号限定时使用） */
    private final String account;

    /** 企业微信账号；为空表示该责任人无法接收企微通知 */
    private final String webComId;

    /** 所属部门名称（测试模式按部门限定时使用） */
    private final String deptName;

    /** 角色标识，逗号分隔（测试模式按角色限定时使用） */
    private final String roleKeys;

    /** 角色名称，逗号分隔（测试模式按角色限定时使用） */
    private final String roleNames;

    /** 本次清单里的责任资产总数 */
    private final int assetTotal;

    /** 本次清单里仍未盘点的资产数 */
    private final int assetPending;

    public RespUser(Long userId, String respUserName, String account, String webComId, String deptName,
                    String roleKeys, String roleNames, int assetTotal, int assetPending) {
        this.userId = userId;
        this.respUserName = respUserName;
        this.account = account;
        this.webComId = webComId;
        this.deptName = deptName;
        this.roleKeys = roleKeys;
        this.roleNames = roleNames;
        this.assetTotal = assetTotal;
        this.assetPending = assetPending;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRespUserName() {
        return respUserName;
    }

    public String getAccount() {
        return account;
    }

    public String getWebComId() {
        return webComId;
    }

    public String getDeptName() {
        return deptName;
    }

    public String getRoleKeys() {
        return roleKeys;
    }

    public String getRoleNames() {
        return roleNames;
    }

    public int getAssetTotal() {
        return assetTotal;
    }

    public int getAssetPending() {
        return assetPending;
    }
}
