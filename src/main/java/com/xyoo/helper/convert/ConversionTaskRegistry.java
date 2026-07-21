package com.xyoo.helper.convert;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 转换任务的内存注册表。
 * <p>
 * key 同时维护 taskId 与 token 两套索引：状态轮询用 taskId，下载用一次性 token。
 * 任务下载完成或失败清理后即时移除；服务重启会丢失，但临时文件由定时清理兜底。
 * </p>
 */
@Component
public class ConversionTaskRegistry {

    private final Map<String, ConversionTask> byTaskId = new ConcurrentHashMap<>();
    private final Map<String, String> tokenToTaskId = new ConcurrentHashMap<>();

    public void register(ConversionTask task) {
        byTaskId.put(task.getTaskId(), task);
        tokenToTaskId.put(task.getToken(), task.getTaskId());
    }

    public ConversionTask getByTaskId(String taskId) {
        return byTaskId.get(taskId);
    }

    public ConversionTask getByToken(String token) {
        String taskId = tokenToTaskId.get(token);
        return taskId == null ? null : byTaskId.get(taskId);
    }

    /**
     * 移除任务（同时清掉 token 索引），使其下载令牌失效。
     */
    public void remove(String taskId) {
        ConversionTask task = byTaskId.remove(taskId);
        if (task != null) {
            tokenToTaskId.remove(task.getToken());
        }
    }

    public Collection<ConversionTask> all() {
        return byTaskId.values();
    }
}
