package com.xyoo.helper.convert;

import java.nio.file.Path;
import java.time.Instant;

/**
 * 一次「PDF → Word」转换任务的内存态描述。
 * <p>
 * 临时文件全程加密落盘，本对象仅保存在内存（不落库），
 * 下载完成或清理调度触发后即被移除，符合「用完即焚」。
 * </p>
 */
public class ConversionTask {

    /** 任务生命周期 */
    public enum Status {
        /** 已接收、排队中 */
        QUEUED,
        /** 正在转换 */
        CONVERTING,
        /** 转换完成，可下载 */
        DONE,
        /** 转换失败 */
        FAILED
    }

    /** 任务 ID（UUID，用于状态轮询） */
    private String taskId;

    /** 一次性下载令牌（UUID，下载后即失效） */
    private String token;

    /** 本任务专属的 AES 密码（仅存内存） */
    private String password;

    private Status status = Status.QUEUED;

    /** 失败原因 */
    private String error;

    /** 原始文件名（用于下载时生成 .docx 文件名） */
    private String originalFileName;

    /** 加密后的输入文件（PDF 密文） */
    private Path encInPath;

    /** 加密后的输出文件（Word 密文） */
    private Path encOutPath;

    /** 创建时间（用于超龄清理判断） */
    private Instant createTime = Instant.now();

    public ConversionTask() {
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public Path getEncInPath() {
        return encInPath;
    }

    public void setEncInPath(Path encInPath) {
        this.encInPath = encInPath;
    }

    public Path getEncOutPath() {
        return encOutPath;
    }

    public void setEncOutPath(Path encOutPath) {
        this.encOutPath = encOutPath;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Instant createTime) {
        this.createTime = createTime;
    }
}
