package com.xyoo.helper.controller;

import com.xyoo.helper.common.Result;
import com.xyoo.helper.convert.ConversionTask;
import com.xyoo.helper.convert.DocConvertService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 文档转换 REST API（PDF → Word）
 *
 * <pre>
 * POST   /api/doc-convert/upload     — 上传 PDF，加密落临时目录并异步转换，返回 taskId + 一次性下载 token
 * GET    /api/doc-convert/status      — 轮询转换状态（?taskId=）
 * GET    /api/doc-convert/download    — 凭 token 下载 Word（解密流式返回，完成后删除全部临时文件）
 * </pre>
 */
@RestController
@RequestMapping("/api/doc-convert")
public class DocConvertController {

    private static final Logger log = LoggerFactory.getLogger(DocConvertController.class);

    private final DocConvertService docConvertService;

    public DocConvertController(DocConvertService docConvertService) {
        this.docConvertService = docConvertService;
    }

    /**
     * 上传 PDF 并启动转换。返回 taskId（轮询用）与 token（下载用，一次性）。
     */
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        try {
            ConversionTask task = docConvertService.upload(file);
            Map<String, String> data = new HashMap<>();
            data.put("taskId", task.getTaskId());
            data.put("token", task.getToken());
            data.put("status", task.getStatus().name());
            return Result.success("已接收，转换中", data);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (IOException e) {
            log.error("上传转换失败: {}", e.getMessage());
            return Result.error("上传失败：" + e.getMessage());
        }
    }

    /**
     * 轮询转换状态。
     */
    @GetMapping("/status")
    public Result<Map<String, String>> status(@RequestParam("taskId") String taskId) {
        ConversionTask task = docConvertService.getStatus(taskId);
        if (task == null) {
            return Result.error("任务不存在或已过期");
        }
        Map<String, String> data = new HashMap<>();
        data.put("taskId", task.getTaskId());
        data.put("status", task.getStatus().name());
        if (task.getError() != null) {
            data.put("error", task.getError());
        }
        return Result.success(data);
    }

    /**
     * 下载转换后的 Word 文档（解密流式返回，完成后删除全部临时文件）。
     */
    @GetMapping("/download")
    public void download(@RequestParam("token") String token, HttpServletResponse response) {
        DocConvertService.DecryptedDoc doc = null;
        try {
            doc = docConvertService.download(token);
        } catch (IllegalArgumentException e) {
            writeError(response, 404, e.getMessage());
            return;
        } catch (IllegalStateException e) {
            writeError(response, 409, e.getMessage());
            return;
        } catch (IOException e) {
            log.error("下载解密失败: {}", e.getMessage());
            writeError(response, 500, "下载失败：" + e.getMessage());
            return;
        }

        boolean written = false;
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(doc.getFileName(), StandardCharsets.UTF_8)
                    .build();
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
            response.setContentLength(doc.getContent().length);

            try (OutputStream os = response.getOutputStream()) {
                os.write(doc.getContent());
                os.flush();
            }
            written = true;
        } catch (IOException e) {
            log.error("写出下载流失败: {}", e.getMessage());
        }
        // 仅当内容已成功写出时才消费令牌并清理临时文件；
        // 若写出失败（如连接中断/客户端取消），保留令牌与密文，允许前端重试，避免一次性令牌被无效消耗。
        if (written) {
            docConvertService.consume(token);
        }
    }

    private void writeError(HttpServletResponse response, int status, String message) {
        try {
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            String body = "{\"code\":" + status + ",\"message\":\"" +
                    message.replace("\"", "'") + "\",\"data\":null}";
            response.getWriter().write(body);
        } catch (IOException ignored) {
            // ignore
        }
    }
}
