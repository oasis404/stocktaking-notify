package com.accutech.stocktaking.notify.support;

import com.accutech.stocktaking.notify.model.NotifyContext;
import com.accutech.stocktaking.notify.model.NotifyType;
import com.accutech.stocktaking.notify.model.RespUser;
import com.accutech.stocktaking.notify.model.StocktakingPlan;

/**
 * 通知文案模板（2026-09-28 定稿，与生产实现逐字一致）。
 *
 * <p>文案是唯一让三个变量决定的内容：<b>待盘点数</b>、<b>是否截止日</b>、<b>截止时间</b>。
 * 正因为"待盘点数相同的人文案逐字相同"，才有 {@code GroupDispatcher} 的"按文案分组群发" ——
 * 200 个人可能只需要十几次接口调用。</p>
 *
 * <p><b>2026-09-28 变更</b>：入口提示语改为企微<b>免登录深链</b>（OAuth 静默授权直达系统，
 * 免输账号密码，仅企微客户端内有效）；开始通知增加「本期」与「初次登录」提示；
 * 截止日消息的日期上移到「最后一天」之后。文案经生产工程
 * {@code EamStocktakingNotifyContentTest} 基准测试钉死，本类与其保持逐字一致。</p>
 *
 * <p>如需自定义文案，继承本类并覆盖 {@link #build(NotifyContext, RespUser)} 或
 * {@link #entryTip()} 即可，引擎其余部分无需改动。</p>
 */
public class MessageTemplate {

    /**
     * 企微免登录深链：企微客户端内点击 → OAuth 静默授权 → 携授权码回系统按工号签发令牌。
     * 仅企微客户端内有效；redirect_uri 必须 URL 编码。
     */
    public static final String DEEP_LINK_URL =
            "https://open.weixin.qq.com/connect/oauth2/authorize?appid=wwd3b491ac3402f235"
                    + "&redirect_uri=https%3A%2F%2Finfo.accutech.net.cn%3A4431%2Fauth%2Fcorpwx%2Flogin"
                    + "&response_type=code&scope=snsapi_base#wechat_redirect";

    /** 初次登录提示（仅开始通知携带一次，提醒/最后一天不重复） */
    public static final String FIRST_LOGIN_TIP = "（初次登录，账号为登录人的工号/密码默认为admin123）";

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
            return "同事您好，本期资产盘点计划已开始，" + entry + "。" + FIRST_LOGIN_TIP;
        }
        StringBuilder content = new StringBuilder();
        if (context.isDeadlineDay()) {
            content.append("同事您好，今天是本次盘点的最后一天");
            if (plan.getEndDate() != null) {
                content.append("（").append(plan.getEndDate()).append("）");
            }
            content.append("，您还有").append(user.getAssetPending())
                    .append("项资产盘点未执行，").append(entry);
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

    /**
     * 统一入口提示语：应用 + 网站（免登录深链）双入口，
     * 引导到「个人盘点清单」（「」内的名称为 2026-09-28 定稿）
     */
    protected String entryTip() {
        return "请到企业微信工作台中「鼎勤信息管理」应用，或者「鼎勤信息管理」网站"
                + "<a href=\"" + DEEP_LINK_URL + "\">请点击此处</a>"
                + "进入「个人盘点清单」进行盘点";
    }
}
