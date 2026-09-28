package com.accutech.stocktaking.notify.send;

import com.accutech.stocktaking.notify.model.NotifyResult;
import com.accutech.stocktaking.notify.model.NotifyType;
import com.accutech.stocktaking.notify.port.MessageSender;
import com.accutech.stocktaking.notify.support.DailyLimitGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分组群发器：把"每个人一条"压成"每种文案一条"。
 *
 * <p><b>为什么要分组</b>：企微文本消息的接收人支持用 {@code |} 分隔（单次上限 1000 个），
 * 而文案只由「待盘点数 + 是否截止日 + 截止时间」决定 —— 同文案的人可以合成一次接口调用。
 * 200 人串行单发约 1~2 分钟，按文案分组后通常只剩十几次调用（十几秒）。</p>
 *
 * <p><b>坏账号处理</b>：群发时只要有 1 个非法/无接口许可的账号，企微会整条失败。
 * 因此当回执带出不可用账号时，剔除这些人后<b>重发一次</b>，
 * 避免"一个坏账号拖垮上百人"；仍失败则整批判失败并释放限流键（允许当天补发）。</p>
 *
 * <p><b>与账号无关的失败</b>（地址未配置、总开关未开、服务异常）不重试，直接整批判失败并记原始原因。</p>
 */
public final class GroupDispatcher {

    private static final Logger log = LoggerFactory.getLogger(GroupDispatcher.class);

    private static final String TAG = "[盘点通知]";

    /** 单次调用的接收人上限（企微限制：touser 用 | 分隔最多 1000 个） */
    public static final int MAX_RECIPIENTS_PER_CALL = 1000;

    /** 剔除坏账号后最多重发几轮 */
    private static final int MAX_ATTEMPTS = 2;

    private final MessageSender sender;

    private final DailyLimitGuard dailyLimitGuard;

    public GroupDispatcher(MessageSender sender, DailyLimitGuard dailyLimitGuard) {
        this.sender = sender;
        this.dailyLimitGuard = dailyLimitGuard;
    }

    /**
     * 执行分组群发
     *
     * <p>不返回统计值：发送结果统一回写到每条 {@link NotifyResult}（成功 / 失败 + 原因），
     * 汇总只认 {@code NotifySummary.of(results)} 这一个事实源，避免两套统计口径打架。</p>
     *
     * @param planId 盘点计划ID（仅用于日志）
     * @param type 通知类型（仅用于日志）
     * @param groups 分组：key=完整文案，value=该文案下的待发人员
     */
    public void dispatch(Integer planId, NotifyType type, Map<String, List<PendingSend>> groups) {
        int sent = 0;
        int failed = 0;
        for (Map.Entry<String, List<PendingSend>> group : groups.entrySet()) {
            List<PendingSend> items = group.getValue();
            for (int from = 0; from < items.size(); from += MAX_RECIPIENTS_PER_CALL) {
                int to = Math.min(from + MAX_RECIPIENTS_PER_CALL, items.size());
                List<PendingSend> chunk = new ArrayList<PendingSend>(items.subList(from, to));
                int[] counts = dispatchChunk(planId, type, chunk, group.getKey());
                sent += counts[0];
                failed += counts[1];
            }
        }
        if (!groups.isEmpty()) {
            log.info("{}[SEND] 方式=群发 计划={} 类型={} 文案={} 种 待发人数={} 成功={} 失败={} 单次上限={}",
                    TAG, planId, type.code(), groups.size(), sent + failed, sent, failed, MAX_RECIPIENTS_PER_CALL);
        }
    }

    /**
     * 发送一批（同一文案 + 同一分片）
     */
    private int[] dispatchChunk(Integer planId, NotifyType type, List<PendingSend> items, String content) {
        List<PendingSend> pending = new ArrayList<PendingSend>(items);
        List<PendingSend> invalidItems = new ArrayList<PendingSend>();
        int sentCount = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS && !pending.isEmpty(); attempt++) {
            List<String> recipients = joinWebComIds(pending);
            MessageSender.SendReceipt receipt;
            try {
                receipt = sender.send(recipients, content);
            } catch (Exception e) {
                // 接入方实现允许抛异常（HTTP 超时是常态，port 契约未禁止）：
                // 视为整批未送达 —— 标记失败并释放限流键（markFailed 内处理），异常不冒泡到调用方
                log.error("{}[SEND] 结果=异常 方式=群发 计划={} 类型={} 人数={} 第{}轮 原因={}",
                        TAG, planId, type.code(), pending.size(), attempt + 1, e.getMessage(), e);
                for (PendingSend item : pending) {
                    markFailed(item, "发送接口异常：" + e.getMessage());
                }
                pending.clear();
                break;
            }
            if (receipt.isSuccess()) {
                for (PendingSend item : pending) {
                    markSent(item);
                }
                // 成功人数按去重后的企微号统计：同一批出现重复号（数据脏）时，实际接收人比行数少
                if (recipients.size() != pending.size()) {
                    log.warn("{}[SEND] 结果=注意 计划={} 类型={} 存在重复企微号（数据脏），成功人数按去重后统计：{}→{}",
                            TAG, planId, type.code(), pending.size(), recipients.size());
                }
                sentCount += recipients.size();
                log.info("{}[SEND] 结果=成功 方式=群发 计划={} 类型={} 人数={} 第{}轮 文案长度={}",
                        TAG, planId, type.code(), pending.size(), attempt + 1, content.length());
                pending.clear();
                break;
            }
            // 失败：先看是不是"名单里有不可用账号"引起的
            List<String> invalidIds = receipt.getInvalidWebComIds();
            List<PendingSend> retry = new ArrayList<PendingSend>();
            for (PendingSend item : pending) {
                if (containsIgnoreCase(invalidIds, item.getUser().getWebComId())) {
                    invalidItems.add(item);
                } else {
                    retry.add(item);
                }
            }
            if (retry.size() == pending.size()) {
                // 与账号无关的失败：整批判失败，不重试
                for (PendingSend item : pending) {
                    markFailed(item, receipt.getFailReason());
                }
                log.error("{}[SEND] 结果=失败 方式=群发 计划={} 类型={} 人数={} 原因={}",
                        TAG, planId, type.code(), pending.size(), receipt.getFailReason());
                pending.clear();
                break;
            }
            log.warn("{}[SEND] 结果=部分失败 方式=群发 计划={} 无效账号={} 剔除后重发人数={} 原因={}",
                    TAG, planId, invalidIds, retry.size(), receipt.getFailReason());
            pending = retry;
        }
        // 不可用账号：明确标失败（带上具体账号，便于推动业务在用户表里修正）
        for (PendingSend item : invalidItems) {
            markFailed(item, "企业微信账号不可用（不在应用可见范围/无接口许可/已离职）：" + item.getUser().getWebComId());
        }
        // 剔除坏账号后重发仍失败：剩余的人标失败
        for (PendingSend item : pending) {
            markFailed(item, "剔除不可用账号后重发仍失败");
        }
        return new int[] { sentCount, items.size() - sentCount };
    }

    /** 接收人列表：按企微约定拼接（去重，避免重复计入 1000 上限） */
    private List<String> joinWebComIds(List<PendingSend> items) {
        Set<String> ids = new LinkedHashSet<String>();
        for (PendingSend item : items) {
            String webComId = item.getUser().getWebComId();
            if (webComId != null && webComId.trim().length() > 0) {
                ids.add(webComId.trim());
            }
        }
        return new ArrayList<String>(ids);
    }

    private void markSent(PendingSend item) {
        NotifyResult row = item.getRow();
        row.sent("群发成功");
    }

    /** 标记失败并释放限流键（允许当天重跑补发，成功的人不受影响） */
    private void markFailed(PendingSend item, String failReason) {
        if (item.getDailyKey() != null) {
            try {
                dailyLimitGuard.release(item.getDailyKey());
            } catch (Exception e) {
                log.warn("{}[SEND] 结果=注意 计划={} 责任人={} 原因=释放当日限流键失败（当日可能不再补发） 限流键={}",
                        TAG, item.getRow().getPlanId(), item.getRow().getRespUserName(), item.getDailyKey());
            }
        }
        NotifyResult row = item.getRow();
        row.failed(failReason + "（已释放当日限流键，可重跑补发）");
        log.error("{}[SEND] 结果=失败 计划={} 责任人={} 企微号={} 限流键={} 原因={}",
                TAG, row.getPlanId(), row.getRespUserName(), item.getUser().getWebComId(), item.getDailyKey(), failReason);
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        if (values == null || values.isEmpty() || target == null || target.trim().length() == 0) {
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
