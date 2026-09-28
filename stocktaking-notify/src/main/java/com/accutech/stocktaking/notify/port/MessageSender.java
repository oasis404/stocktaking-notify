package com.accutech.stocktaking.notify.port;

import java.util.Collections;
import java.util.List;

/**
 * 消息发送扩展点：把"批量发文本"这一件事交给接入方实现（企业微信 / 钉钉 / 短信都行）。
 *
 * <p><b>接入要点</b>：</p>
 * <ul>
 *   <li>必须支持一次发送多个接收人（企微 text 消息用 {@code |} 分隔，单次上限 1000 个），
 *       这是"按文案分组群发"能把 200 人压成十几次调用的前提；</li>
 *   <li>失败时请把平台返回的<b>不可用账号</b>放进回执（企微的 {@code invaliduser} / {@code unlicenseduser}），
 *       否则"一个坏账号拖垮整批人"无法补救；</li>
 *   <li>不要把异常吞掉后返回成功 —— 返回 {@link SendReceipt#fail} 才能让上层标记失败并释放限流键。</li>
 *   <li>实现<b>允许直接抛异常</b>（用 HttpURLConnection / RestTemplate 时超时抛异常是常态）：
 *       引擎会把该批标记为失败、释放限流键，并把异常兜在发送环节内部，不会冒泡到调用方。</li>
 * </ul>
 */
public interface MessageSender {

    /**
     * 批量发送文本消息
     *
     * @param webComIds 接收人账号列表（非空）
     * @param content 消息文案
     * @return 发送回执
     */
    SendReceipt send(List<String> webComIds, String content);

    /**
     * 发送回执：成功 / 失败原因 / 不可用账号列表
     */
    final class SendReceipt {

        private final String failReason;

        private final List<String> invalidWebComIds;

        private SendReceipt(String failReason, List<String> invalidWebComIds) {
            this.failReason = failReason;
            this.invalidWebComIds = invalidWebComIds == null
                    ? Collections.<String>emptyList()
                    : invalidWebComIds;
        }

        public static SendReceipt success() {
            return new SendReceipt(null, null);
        }

        public static SendReceipt fail(String failReason) {
            return new SendReceipt(failReason, null);
        }

        /**
         * 失败，且平台明确返回了不可用账号：上层会剔除这些账号后重发一次
         */
        public static SendReceipt fail(String failReason, List<String> invalidWebComIds) {
            return new SendReceipt(failReason, invalidWebComIds);
        }

        public boolean isSuccess() {
            return failReason == null;
        }

        public String getFailReason() {
            return failReason;
        }

        public List<String> getInvalidWebComIds() {
            return invalidWebComIds;
        }
    }
}
