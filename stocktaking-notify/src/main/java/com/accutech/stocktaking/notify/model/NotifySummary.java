package com.accutech.stocktaking.notify.model;

import java.util.List;

/**
 * 一次通知执行的汇总（发送 / 跳过 / 失败 / 演练各多少条）。
 *
 * <p>定时任务、诊断接口、人工补跑都用它输出"到底发出去了几条"，
 * 避免只看日志里的"处理了 N 条"而误判。</p>
 */
public final class NotifySummary {

    private final int total;

    private final int sent;

    private final int skipped;

    private final int failed;

    private final int preview;

    private NotifySummary(int total, int sent, int skipped, int failed, int preview) {
        this.total = total;
        this.sent = sent;
        this.skipped = skipped;
        this.failed = failed;
        this.preview = preview;
    }

    /**
     * 按结果集统计
     */
    public static NotifySummary of(List<NotifyResult> results) {
        int sent = 0;
        int skipped = 0;
        int failed = 0;
        int preview = 0;
        int total = results == null ? 0 : results.size();
        if (results != null) {
            for (NotifyResult row : results) {
                NotifyDecision decision = row.getDecision();
                if (NotifyDecision.SEND == decision) {
                    sent++;
                } else if (NotifyDecision.FAIL == decision) {
                    failed++;
                } else if (NotifyDecision.PREVIEW == decision) {
                    preview++;
                } else {
                    skipped++;
                }
            }
        }
        return new NotifySummary(total, sent, skipped, failed, preview);
    }

    public int getTotal() {
        return total;
    }

    public int getSent() {
        return sent;
    }

    public int getSkipped() {
        return skipped;
    }

    public int getFailed() {
        return failed;
    }

    public int getPreview() {
        return preview;
    }

    @Override
    public String toString() {
        return "汇总[发送=" + sent + " 跳过=" + skipped + " 失败=" + failed + " 演练=" + preview + "]";
    }
}
