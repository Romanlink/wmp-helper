package com.xyoo.helper.controller;

import com.xyoo.helper.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * AI 对话 SSE 接口
 *
 * <pre>
 * GET /api/chat/stream?message=xxx  — SSE 流式对话（调用本地 Ollama 模型）
 * </pre>
 *
 * 不使用 SseEmitter（它会经过 Tomcat 的 output buffer 缓冲，导致
 * 客户端收不到逐字效果），而是直接写 HttpServletResponse 输出流，
 * 每收到一个 token 立即 flushBuffer() 刷到 socket。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * SSE 流式对话 — 转发本地 Ollama 大模型的输出
     */
    @GetMapping(value = "/stream")
    public void stream(@RequestParam String message, HttpServletResponse response) {
        // 设置 SSE 标准头 + 禁用各级缓冲
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");        // Nginx 禁用缓冲

        try {
            PrintWriter writer = response.getWriter();

            // 先发送一个注释行确保 HTTP 头和 SSE 流已连通
            writer.write(":ok\n\n");
            writer.flush();
            response.flushBuffer();

            // 调用本地大模型，逐 token 转发
            chatService.streamChat(message, token -> {
                try {
                    writeToken(writer, response, token);
                } catch (IOException e) {
                    // 客户端断开或写出失败 —— 抛出非受检异常终止整条流
                    throw new RuntimeException("SSE 写出失败", e);
                }
            });

            // 结束标记
            writer.write("data:[DONE]\n\n");
            writer.flush();
            response.flushBuffer();

        } catch (IOException e) {
            // 客户端断开或 Ollama 连接失败
            logWarnAndClose(response, e.getMessage());
        } catch (Exception e) {
            logWarnAndClose(response, "对话处理异常：" + e.getMessage());
        }
    }

    /**
     * 将单个 token 以 SSE 格式写出。
     * <p>
     * 若 token 内含换行符，按行拆分并逐行以 {@code data:} 发送，
     * 这样 SSE 规范会把多行重新合并为带 \n 的完整内容，避免帧错位。
     */
    private void writeToken(PrintWriter writer, HttpServletResponse response, String token) throws IOException {
        if (token == null || token.isEmpty()) {
            return;
        }
        String normalized = token.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            // 注意：data: 后必须保留一个空格（SSE 规范的可选分隔符），
            // 前端 parseSseFrame 会剥掉这唯一一个空格；若不加空格，
            // 内容行首的空格（如代码缩进）会被误当分隔符剥掉，导致空格丢失。
            sb.append("data: ").append(line).append("\n");
        }
        sb.append("\n");
        writer.write(sb.toString());
        writer.flush();
        response.flushBuffer();
    }

    /**
     * 异常兜底：尝试向客户端返回一条错误提示并关闭 SSE 流
     */
    private void logWarnAndClose(HttpServletResponse response, String reason) {
        log.warn("AI 对话流异常: {}", reason);
        try {
            PrintWriter writer = response.getWriter();
            String msg = "抱歉，调用本地大模型失败：" + (reason != null ? reason : "未知错误")
                    + "。请确认 Ollama 服务已启动。";
            String normalized = msg.replace("\r\n", "\n").replace('\r', '\n');
            String[] lines = normalized.split("\n", -1);
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                sb.append("data: ").append(line).append("\n");
            }
            sb.append("\n");
            writer.write(sb.toString());
            writer.write("data:[DONE]\n\n");
            writer.flush();
            response.flushBuffer();
        } catch (Exception ignored) {
            // 客户端已断开，静默处理
        }
    }
}
