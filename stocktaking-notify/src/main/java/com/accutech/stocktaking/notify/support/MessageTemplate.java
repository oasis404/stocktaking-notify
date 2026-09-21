package com.accutech.stocktaking.notify.support;

import com.accutech.stocktaking.notify.model.NotifyContext;
import com.accutech.stocktaking.notify.model.NotifyType;
import com.accutech.stocktaking.notify.model.RespUser;
import com.accutech.stocktaking.notify.model.StocktakingPlan;

/**
 * 通知文案模板。
 *
 * <p>文案是唯一让三个变量决定的内容：<b>待盘点数</b>、<b>是否截止日</b>、<b>截止时间</b>。
 * 正因为"待盘点数相同的人文案逐字相同"，才有 {@code GroupDispatcher} 的"按文案分组群发" ——
 * 200 个人可能只需要十几次接口调用。</p>
 *
 * <p>如需自定义文案，继承本类并覆盖 {@link #build(NotifyContext, RespUser)} 即可，
 * 引擎其余部分无需改动。</p>
 */
public class MessageTemplate {

    /** 默认入口提示语里的系统地址 */
    public static final String DEFAULT_PORTAL_URL = "https://info.accutech.net.cn:4431/";

    private final String portalUrl;

    public MessageTemplate() {
        this(DEFAULT_PORTAL_URL);
    }

    public MessageTemplate(String portalUrl) {
        this.portalUrl = portalUrl;
    }

    /**
     * 组装某个责任人本次应收到的文案
     *
     * @param context 执行上下文（含计划、类型、今天）
     * @param user 责任人（提供待盘点数）
     * @return 文案
     */
    public String build(NotifyContext context, RespUser user) {
        StocktakingPlan plan = context.getPlan();
        String entry = entryTip();
        if (NotifyType.START == context.getType()) {
            return "同事您好，资产盘点计划已开始，" + entry + "。";
        }
        StringBuilder content = new StringBuilder();
        if (context.isDeadlineDay()) {
            content.append("同事您好，今天是本次盘点的最后一天，您还有").append(user.getAssetPending())
                    .append("项资产盘点未执行，").append(entry);
            if (plan.getEndDate() != null) {
                content.append("（").append(plan.getEndDate()).append("）");
            }
        } else {
            content.append("同事您好，您还有").append(user.getAssetPending())
                    .append("项资产盘点未执行，").append(entry);
            if (plan.getEndDate() != null) {
                content.append("，截止时间还剩").append(context.remainDays())
                        .append("天（").append(plan.getEndDate()).append("）");
            }
        }
        content.append("，如有问题请联系财务。");
        return content.toString();
    }

    /** 统一入口提示语：明确引导到「个人盘点清单」 */
    protected String entryTip() {
        return "请到微信工作台鼎勤信息管理小程序或者鼎勤信息管理网站" + portalUrl + "点击个人盘点清单进行盘点";
    }
}
