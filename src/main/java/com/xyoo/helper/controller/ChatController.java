package com.xyoo.helper.controller;

import com.xyoo.helper.common.BaseController;
import com.xyoo.helper.common.LoginUser;
import com.xyoo.helper.rag.RetrievalService;
import com.xyoo.helper.service.AuthService;
import com.xyoo.helper.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
public class ChatController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final RetrievalService retrievalService;
    private final AuthService authService;

    public ChatController(ChatService chatService, RetrievalService retrievalService, AuthService authService) {
        this.chatService = chatService;
        this.retrievalService = retrievalService;
        this.authService = authService;
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
     * RAG 流式问答（基于私有文档，带角色权限过滤）。
     * <p>接收 JSON {@code {message}}；先按当前登录人角色可见菜单检索相关文档片段，
     * 再拼成系统提示交给大模型生成带依据的回答。需要登录（前端 fetch 带 token）。</p>
     */
    @PostMapping(value = "/rag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void rag(@RequestBody Map<String, String> req, HttpServletResponse response) {
        String message = (req == null || req.get("message") == null) ? "" : req.get("message");
        if (message.isBlank()) {
            writeSimple(response, "请输入您的问题。");
            return;
        }

        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        try {
            PrintWriter writer = response.getWriter();
            writer.write(":ok\n\n");
            writer.flush();
            response.flushBuffer();

            // 解析当前登录人角色 → 可见菜单集合（RBAC 过滤）
            LoginUser cur = getCurInfo();
            Set<Long> allowed = (cur != null && cur.getRoleId() != null)
                    ? authService.findModuleIdsByRole(cur.getRoleId())
                    : Collections.emptySet();

            // 检索相关片段
            List<RetrievalService.RetrievedChunk> chunks = retrievalService.retrieve(message, allowed);
            String systemPrompt = buildRagPrompt(message, chunks);

            chatService.streamRag(systemPrompt, message, token -> {
                try {
                    writeToken(writer, response, token);
                } catch (IOException e) {
                    throw new RuntimeException("SSE 写出失败", e);
                }
            });

            writer.write("data:[DONE]\n\n");
            writer.flush();
            response.flushBuffer();
        } catch (IOException e) {
            logWarnAndClose(response, e.getMessage());
        } catch (Exception e) {
            logWarnAndClose(response, "RAG 对话处理异常：" + e.getMessage());
        }
    }

    /**
     * 构造 RAG 系统提示：约束模型仅基于检索资料回答，并附上来源片段。
     */
    private String buildRagPrompt(String question, List<RetrievalService.RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个企业私有文档智能助手。请严格依据下面提供的【参考资料】回答用户问题。\n");
        sb.append("要求：\n");
        sb.append("1. 仅使用参考资料中的内容作答；若资料中不存在相关信息，请明确说明「根据现有资料无法回答该问题」，不要编造。\n");
        sb.append("2. 回答末尾用「参考文档：xxx」标注引用来源标题（若有）。\n");
        sb.append("3. 语言简洁、准确，使用中文。\n\n");

        if (chunks == null || chunks.isEmpty()) {
            sb.append("【参考资料】：（当前无相关文档）\n");
        } else {
            sb.append("【参考资料】\n");
            int i = 1;
            for (RetrievalService.RetrievedChunk c : chunks) {
                String title = (c.getDocTitle() == null || c.getDocTitle().isBlank()) ? "未命名文档" : c.getDocTitle();
                sb.append(i).append(". 《").append(title).append("》\n").append(c.getContent()).append("\n\n");
                i++;
            }
        }
        sb.append("用户问题：").append(question);
        return sb.toString();
    }

    /**
     * 直接返回一条非流式提示（用于参数校验失败等场景）。
     */
    private void writeSimple(HttpServletResponse response, String msg) {
        try {
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            PrintWriter writer = response.getWriter();
            String normalized = msg.replace("\r\n", "\n").replace('\r', '\n');
            for (String line : normalized.split("\n", -1)) {
                writer.write("data: " + line + "\n");
            }
            writer.write("data:[DONE]\n\n");
            writer.flush();
            response.flushBuffer();
        } catch (Exception ignored) {
            // 客户端已断开，静默处理
        }
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
