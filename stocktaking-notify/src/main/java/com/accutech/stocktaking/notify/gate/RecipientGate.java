package com.accutech.stocktaking.notify.gate;

import com.accutech.stocktaking.notify.model.GateNode;
import com.accutech.stocktaking.notify.model.NotifyContext;
import com.accutech.stocktaking.notify.model.RespUser;

/**
 * 闸门⑥：收件人资格 —— 必须是"本次清单里仍有待盘点资产的资产责任人本人"，且账号可用。
 *
 * <p>两道判断：</p>
 * <ol>
 *   <li><b>待盘点数为 0</b>：已经盘完的人不再打扰（盘完当天也不会收到"还剩 0 项"的废话）；</li>
 *   <li><b>没有企业微信账号</b>：不发。这里刻意不做"按姓名找账号"之类的兜底 ——
 *       兜底很可能把通知发给同名的另一个人，而"发给非责任人"比"漏发"严重得多。</li>
 * </ol>
 *
 * <p>被拦下的人仍然会出现在返回结果里（decision=SKIP + 原因），
 * 因此运营可以直接从诊断接口看到"谁没收到、为什么"。</p>
 */
public final class RecipientGate implements PersonGate {

    @Override
    public GateNode node() {
        return GateNode.BENEFICIARY;
    }

    @Override
    public GateOutcome check(NotifyContext context, RespUser user) {
        if (user.getAssetPending() <= 0) {
            return GateOutcome.block(GateNode.BENEFICIARY, "本次清单无待盘点资产");
        }
        if (isBlank(user.getWebComId())) {
            return GateOutcome.block(GateNode.BENEFICIARY,
                    "非可投递责任人：无可用企业微信账号（账号为空，或已停用/离职/系统无此用户）");
        }
        return GateOutcome.pass();
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().length() == 0;
    }
}
