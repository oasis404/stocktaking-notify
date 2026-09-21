package com.accutech.stocktaking.notify.testing;

import com.accutech.stocktaking.notify.model.RespUser;
import com.accutech.stocktaking.notify.port.DailyLimitStore;
import com.accutech.stocktaking.notify.port.MessageSender;
import com.accutech.stocktaking.notify.port.RespUserProvider;
import com.accutech.stocktaking.notify.port.TestModeConfigProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 测试/演示用的内存实现（生产环境请换成 Redis、企微接口与数据库实现，见 {@code examples/}）。
 *
 * <p>把它们放在测试源码里而不是主源码，是为了让主源码保持"纯逻辑、零外部依赖"。</p>
 */
public final class InMemorySupport {

    private InMemorySupport() {
    }

    /**
     * 内存版消息发送器：记录每次调用的接收人与文案，并支持模拟两类失败。
     */
    public static final class InMemoryMessageSender implements MessageSender {

        /** 每次实际调用记录 */
        private final List<SentBatch> batches = new ArrayList<SentBatch>();

        /** 不可用账号：命中时整批失败并把这些账号带回去（模拟企微 invaliduser 行为） */
        private final Set<String> invalidWebComIds = new LinkedHashSet<String>();

        /** 与账号无关的失败原因（模拟"地址未配置/服务异常"） */
        private String globalFailReason;

        @Override
        public SendReceipt send(List<String> webComIds, String content) {
            List<String> ids = new ArrayList<String>(webComIds);
            batches.add(new SentBatch(ids, content));
            if (globalFailReason != null) {
                return SendReceipt.fail(globalFailReason);
            }
            List<String> invalid = new ArrayList<String>();
            for (String id : ids) {
                if (invalidWebComIds.contains(id)) {
                    invalid.add(id);
                }
            }
            if (!invalid.isEmpty()) {
                return SendReceipt.fail("企业微信返回失败：errcode=81013，errmsg=invalid user", invalid);
            }
            return SendReceipt.success();
        }

        public InMemoryMessageSender withInvalidWebComId(String... ids) {
            Collections.addAll(this.invalidWebComIds, ids);
            return this;
        }

        public InMemoryMessageSender withGlobalFail(String reason) {
            this.globalFailReason = reason;
            return this;
        }

        /** 实际调用次数（用于验证"按文案分组"是否真的减少了调用） */
        public int callCount() {
            return batches.size();
        }

        public List<SentBatch> batches() {
            return batches;
        }

        /** 所有被发送过的接收人 */
        public List<String> allRecipients() {
            List<String> all = new ArrayList<String>();
            for (SentBatch batch : batches) {
                all.addAll(batch.getWebComIds());
            }
            return all;
        }
    }

    /** 一次发送调用的记录 */
    public static final class SentBatch {

        private final List<String> webComIds;

        private final String content;

        SentBatch(List<String> webComIds, String content) {
            this.webComIds = webComIds;
            this.content = content;
        }

        public List<String> getWebComIds() {
            return webComIds;
        }

        public String getContent() {
            return content;
        }
    }

    /**
     * 内存版限流存储：Set 语义即 "SET NX"（存在则占位失败）。
     */
    public static final class InMemoryDailyLimitStore implements DailyLimitStore {

        private final Map<String, String> keys = new LinkedHashMap<String, String>();

        @Override
        public boolean tryOccupy(String key, String value, int ttlSeconds) {
            if (keys.containsKey(key)) {
                return false;
            }
            keys.put(key, value);
            return true;
        }

        @Override
        public void release(String key) {
            keys.remove(key);
        }

        @Override
        public boolean isOccupied(String key) {
            return keys.containsKey(key);
        }

        public Set<String> occupiedKeys() {
            return keys.keySet();
        }
    }

    /**
     * 内存版收件人数据源。
     */
    public static final class InMemoryRespUserProvider implements RespUserProvider {

        private final Map<Integer, List<RespUser>> deliverable = new HashMap<Integer, List<RespUser>>();

        private Map<String, Integer> stats = new HashMap<String, Integer>();

        public InMemoryRespUserProvider withUsers(Integer planId, RespUser... users) {
            List<RespUser> list = new ArrayList<RespUser>();
            Collections.addAll(list, users);
            deliverable.put(planId, list);
            return this;
        }

        public InMemoryRespUserProvider withUndeliverableStats(String key, int count) {
            stats.put(key, count);
            return this;
        }

        @Override
        public List<RespUser> findDeliverable(Integer planId) {
            List<RespUser> users = deliverable.get(planId);
            return users == null ? new ArrayList<RespUser>() : users;
        }

        @Override
        public Map<String, Integer> undeliverableStats(Integer planId) {
            return stats;
        }
    }

    /**
     * 内存版测试模式配置。
     */
    public static final class InMemoryTestModeConfigProvider implements TestModeConfigProvider {

        private String config;

        public InMemoryTestModeConfigProvider() {
            this(null);
        }

        public InMemoryTestModeConfigProvider(String config) {
            this.config = config;
        }

        public void setConfig(String config) {
            this.config = config;
        }

        @Override
        public String readConfig() {
            return config;
        }
    }

    /**
     * 快捷构造责任人
     */
    public static RespUser user(long userId, String name, String account, String webComId, int pending) {
        return new RespUser(userId, name, account, webComId, "财务部", "stocktaking_manager", "盘点管理员",
                pending + 3, pending);
    }

    /** 构造一个"没有企微号"的责任人 */
    public static RespUser userWithoutWebComId(long userId, String name, String account, int pending) {
        return new RespUser(userId, name, account, null, "财务部", null, null, pending + 1, pending);
    }
}
