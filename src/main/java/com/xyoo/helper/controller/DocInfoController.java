package com.xyoo.helper.controller;

import com.xyoo.helper.common.Result;
import com.xyoo.helper.entity.DocHistory;
import com.xyoo.helper.entity.DocInfo;
import com.xyoo.helper.service.DocInfoService;
import com.xyoo.helper.util.FileEncryptionUtil;
import com.xyoo.helper.util.PdfContentParser;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档管理 REST API
 *
 * <pre>
 * POST   /api/docs/upload            — 上传 PDF 文件，返回完整路径
 * GET    /api/docs?menuId=1          — 根据所属菜单查询文档列表；不传 menuId 返回全部
 * GET    /api/docs/search?keyword=x  — 模糊搜索（标题+标签+内容）
 * GET    /api/docs/byTag?tag=x       — 根据标签查询
 * GET    /api/docs/{docId}           — 根据 docId 查询详情
 * POST   /api/docs                   — 新增文档（内容超 5000 字自动截断）
 * PUT    /api/docs/{docId}           — 编辑文档
 * PUT    /api/docs/{docId}/tags      — 全量更新文档标签
 * DELETE /api/docs/{docId}           — 删除文档
 * GET    /api/docs/{docId}/history   — 查询编辑历史
 * GET    /api/docs/{docId}/download  — 下载原始 PDF 文件
 * </pre>
 */
@RestController
@RequestMapping("/api/docs")
public class DocInfoController {

    private static final Logger log = LoggerFactory.getLogger(DocInfoController.class);

    private final DocInfoService docInfoService;

    @Value("${helper.upload.dir:./uploads}")
    private String uploadDir;

    public DocInfoController(DocInfoService docInfoService) {
        this.docInfoService = docInfoService;
    }

    // ================================================================
    // 文件上传接口
    // ================================================================

    /**
     * 上传 PDF 文件，加密存储并返回文件路径、密码与解析后的 Markdown 内容。
     * <p>
     * 上传完成后自动解析 PDF 文本内容，转换为 Markdown 格式（超过 5000 字自动在语法边界截断）。
     * 前端可将 {@code parsedContent} 自动填入文档内容的表单字段。
     * </p>
     */
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        // 校验非空
        if (file.isEmpty()) {
            throw new RuntimeException("上传文件不能为空");
        }

        // 校验 PDF 类型
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            throw new RuntimeException("仅支持上传 PDF 文件");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new RuntimeException("仅支持上传 PDF 文件");
        }

        // 创建上传目录
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 生成唯一文件名
        String savedName = UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                + "_" + originalFilename.replaceAll("[^a-zA-Z0-9.\\-_\u4e00-\u9fa5]", "_");

        // 获取原始字节（加密前先保存，用于后续内容解析）
        byte[] originalBytes = file.getBytes();

        // 生成密码并加密文件内容
        String attachPwd = FileEncryptionUtil.generatePassword();
        byte[] encryptedBytes = FileEncryptionUtil.encrypt(originalBytes, attachPwd);

        // 保存加密后的文件
        Path destPath = uploadPath.resolve(savedName);
        Files.write(destPath, encryptedBytes);

        // === 解析 PDF 内容为 Markdown ===
        String parsedContent = "";
        try {
            parsedContent = PdfContentParser.parseToMarkdown(originalBytes);
            log.info("PDF 解析成功，提取 {} 字符的 Markdown 内容", parsedContent.length());
        } catch (Exception e) {
            log.warn("PDF 内容解析失败（文件仍正常上传）: {}", e.getMessage());
            // 解析失败不影响上传，仅返回空内容
        }

        String absolutePath = destPath.toAbsolutePath().toString();
        Map<String, String> result = new java.util.HashMap<>();
        result.put("filePath", absolutePath);
        result.put("attachPwd", attachPwd);
        result.put("parsedContent", parsedContent);
        return Result.success(result);
    }

    // ================================================================
    // 查询接口
    // ================================================================

    /**
     * 根据所属菜单查询文档列表（仅展示的）；不传 menuId 时返回全部可见文档
     */
    @GetMapping
    public Result<List<DocInfo>> listByMenu(@RequestParam(required = false) Long menuId) {
        List<DocInfo> list = docInfoService.listByMenuId(menuId);
        return Result.success(list);
    }

    /**
     * 根据文档业务ID查询详情
     */
    @GetMapping("/{docId}")
    public Result<DocInfo> getByDocId(@PathVariable String docId) {
        DocInfo doc = docInfoService.getByDocId(docId)
                .orElseThrow(() -> new RuntimeException("文档不存在，docId=" + docId));
        return Result.success(doc);
    }

    /**
     * 根据标签查询文档列表（仅展示的）
     */
    @GetMapping("/byTag")
    public Result<List<DocInfo>> listByTag(@RequestParam String tag) {
        List<DocInfo> list = docInfoService.listByTag(tag);
        return Result.success(list);
    }

    /**
     * 模糊搜索：匹配文档标题、标签、内容（仅展示的）
     */
    @GetMapping("/search")
    public Result<List<DocInfo>> search(@RequestParam String keyword) {
        List<DocInfo> list = docInfoService.search(keyword);
        return Result.success(list);
    }

    /**
     * 查询文档编辑历史（拉链表）
     */
    @GetMapping("/{docId}/history")
    public Result<List<DocHistory>> getHistory(@PathVariable String docId) {
        DocInfo doc = docInfoService.getByDocId(docId)
                .orElseThrow(() -> new RuntimeException("文档不存在，docId=" + docId));
        List<DocHistory> history = docInfoService.getHistory(doc.getId());
        return Result.success(history);
    }

    // ================================================================
    // 新增 & 编辑接口
    // ================================================================

    /**
     * 新增文档
     */
    @PostMapping
    public Result<DocInfo> create(@Valid @RequestBody DocInfo doc) {
        DocInfo created = docInfoService.create(doc);
        return Result.success(created);
    }

    /**
     * 编辑文档（自动记录到拉链表）
     *
     * @param docId    文档业务ID
     * @param operator 操作人（Header 传入）
     * @param summary  变更摘要（Header 传入）
     */
    @PutMapping("/{docId}")
    public Result<DocInfo> update(@PathVariable String docId,
                                  @RequestBody DocInfo doc,
                                  @RequestHeader(value = "X-Operator", defaultValue = "") String operator,
                                  @RequestHeader(value = "X-Summary", defaultValue = "") String summary) {
        DocInfo updated = docInfoService.update(docId, doc, operator, summary);
        return Result.success(updated);
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/{docId}")
    public Result<Void> delete(@PathVariable String docId) {
        docInfoService.delete(docId);
        return Result.success(null);
    }

    /**
     * 全量更新文档标签
     * <p>
     * 接收 raw body 格式为 {@code { "docTags": "标签A|标签B|标签C" }}，
     * 后端自动按 | 分割、去空白、去重后全量替换。
     * </p>
     */
    @PutMapping("/{docId}/tags")
    public Result<DocInfo> updateTags(@PathVariable String docId,
                                      @RequestBody java.util.Map<String, String> body) {
        String docTags = body.getOrDefault("docTags", "");
        DocInfo updated = docInfoService.updateTags(docId, docTags);
        return Result.success(updated);
    }

    // ================================================================
    // 文件下载接口
    // ================================================================

    /**
     * 下载原始 PDF 文件（自动解密后返回）
     * <p>
     * 根据文档的 original_path 定位加密的本地文件，
     * 使用文档中保存的 attachPwd 解密后返回下载。
     * </p>
     */
    @GetMapping("/{docId}/download")
    public ResponseEntity<Resource> download(@PathVariable String docId) throws UnsupportedEncodingException {
        DocInfo doc = docInfoService.getByDocId(docId)
                .orElseThrow(() -> new RuntimeException("文档不存在，docId=" + docId));

        String originalPath = doc.getOriginalPath();
        if (originalPath == null || originalPath.trim().isEmpty()) {
            throw new RuntimeException("该文档没有原始文件");
        }

        File file = new File(originalPath);
        if (!file.exists()) {
            throw new RuntimeException("原始文件不存在: " + originalPath);
        }

        // 读取加密文件并解密
        byte[] decryptedBytes;
        try {
            byte[] encryptedBytes = Files.readAllBytes(file.toPath());
            String attachPwd = doc.getAttachPwd();
            if (attachPwd == null || attachPwd.trim().isEmpty()) {
                throw new RuntimeException("文档缺少解密密码");
            }
            decryptedBytes = FileEncryptionUtil.decrypt(encryptedBytes, attachPwd);
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败", e);
        }

        Resource resource = new ByteArrayResource(decryptedBytes);
        String encodedFileName = URLEncoder.encode(file.getName(), "UTF-8")
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFileName)
                .body(resource);
    }
}
