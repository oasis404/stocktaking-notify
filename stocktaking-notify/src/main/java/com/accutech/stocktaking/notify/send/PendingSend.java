package com.accutech.stocktaking.notify.send;

import com.accutech.stocktaking.notify.model.NotifyResult;
import com.accutech.stocktaking.notify.model.RespUser;

/**
 * 待发送项：责任人 + 结果行 + 已占用的当日限流键。
 *
 * <p>引擎是"先逐人判定占位、再按文案统一群发"，所以要把这三样从判定阶段带到发送阶段：
 * 结果行用于回写成功/失败（诊断接口直接看得到），限流键用于发送失败时释放（允许当天补发）。</p>
 */
public final class PendingSend {

    private final RespUser user;

    private final NotifyResult row;

    /** 已占用的当日限流键；测试模式下未占限流，为 null */
    private final String dailyKey;

    public PendingSend(RespUser user, NotifyResult row, String dailyKey) {
        this.user = user;
        this.row = row;
        this.dailyKey = dailyKey;
    }

    public RespUser getUser() {
        return user;
    }

    public NotifyResult getRow() {
        return row;
    }

    public String getDailyKey() {
        return dailyKey;
    }
}
