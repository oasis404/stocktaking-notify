package com.accutech.stocktaking.notify.port;

/**
 * 测试模式配置读取扩展点。
 *
 * <p>测试模式用于联调：开启后只向白名单（工号 / 用户ID / 部门 / 角色）命中的人发送，
 * 其余人一律不发。配置格式（{@code |} 分隔的键值对）：</p>
 *
 * <pre>
 * testMode=true|accounts=A10529,A10001|userIds=16017|deptNames=财务部|roleKeys=stocktaking_manager|setBy=admin|setAt=2026-09-21 12:40
 * </pre>
 *
 * <p>存储位置由接入方决定（系统参数表 / 配置中心 / 环境变量均可）。
 * 建议放在<b>持久化</b>存储里而不是缓存里：缓存被清掉会让测试模式"意外关闭"，
 * 而意外关闭会直接变成"发给全员"。</p>
 */
public interface TestModeConfigProvider {

    /**
     * 读取测试模式配置
     *
     * @return 配置文本；{@code null} 或空白表示未开启测试模式
     */
    String readConfig();
}
