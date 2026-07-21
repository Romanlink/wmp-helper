package com.xyoo.helper.convert;

import com.xyoo.helper.util.FileEncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 文档转换编排服务（PDF → Word）。
 * <p>
 * 流程：上传 → 加密落临时目录（enc_in） → 异步解密+转换 → 加密输出（enc_out） →
 * 前端轮询状态 → 下载时解密流式返回 → 删除全部临时文件。
 * 明文中间文件仅瞬态存在，用完即删；所有落盘文件均为 AES-256-GCM 密文。
 * </p>
 */
@Service
public class DocConvertService {

    private static final Logger log = LoggerFactory.getLogger(DocConvertService.class);

    @Value("${helper.convert.temp-dir:./convert-temp}")
    private String tempDirStr;

    @Value("${helper.convert.soffice-path:}")
    private String sofficePath;

    @Value("${helper.convert.python-path:}")
    private String pythonPath;

    @Value("${helper.convert.cleanup-max-age-minutes:30}")
    private long cleanupMaxAgeMinutes;

    private final ConversionTaskRegistry registry;
    private final LibreOfficePdfConverter libreOffice;
    private final Pdf2docxConverter pdf2docx;
    private final TextExtractPdfConverter textExtract;

    private Path tempDir;
    private ExecutorService executor;

    public DocConvertService(ConversionTaskRegistry registry) {
        this.registry = registry;
        this.libreOffice = new LibreOfficePdfConverter(sofficePath);
        this.pdf2docx = new Pdf2docxConverter(pythonPath);
        this.textExtract = new TextExtractPdfConverter();
    }

    @PostConstruct
    public void init() throws IOException {
        this.tempDir = Path.of(tempDirStr);
        Files.createDirectories(tempDir);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "doc-convert");
            t.setDaemon(true);
            return t;
        });
        log.info("文档转换服务初始化：临时目录={}，清理阈值={}分钟", tempDir, cleanupMaxAgeMinutes);
    }

    /**
     * 选择可用引擎（三级兜底）：LibreOffice（生产高保真） → pdf2docx（本地/无 LO） → 纯文本抽取（最后兜底）。
     * 保真度依次降低，但可用性依次提高，保证任意环境都能产出可编辑的 Word。
     */
    private PdfConverter selectConverter() {
        if (libreOffice.isAvailable()) {
            return libreOffice;
        }
        if (pdf2docx.isAvailable()) {
            return pdf2docx;
        }
        return textExtract;
    }

    /**
     * 接收上传的 PDF，加密落盘并异步启动转换。
     *
     * @return 任务信息（含 taskId 与一次性下载 token）
     */
    public ConversionTask upload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("仅支持上传 PDF 文件");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.equals("application/pdf")
                && !contentType.equals("application/octet-stream")) {
            throw new IllegalArgumentException("仅支持上传 PDF 文件");
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        String token = UUID.randomUUID().toString().replace("-", "");
        String password = FileEncryptionUtil.generatePassword();

        ConversionTask task = new ConversionTask();
        task.setTaskId(taskId);
        task.setToken(token);
        task.setPassword(password);
        task.setOriginalFileName(originalFilename);
        task.setEncInPath(tempDir.resolve("enc_in_" + taskId + ".bin"));
        task.setStatus(ConversionTask.Status.QUEUED);

        // 加密落盘
        byte[] original = file.getBytes();
        byte[] encrypted = FileEncryptionUtil.encrypt(original, password);
        Files.write(task.getEncInPath(), encrypted);

        registry.register(task);
        log.info("收到转换任务 {}，原始文件={}，引擎={}", taskId, originalFilename, selectConverter().engineName());

        // 异步执行转换
        executor.submit(() -> runConvert(task));
        return task;
    }

    /** 异步转换执行体 */
    private void runConvert(ConversionTask task) {
        Path decIn = tempDir.resolve("dec_in_" + task.getTaskId() + ".pdf");
        Path encOut = tempDir.resolve("enc_out_" + task.getTaskId() + ".bin");
        Path plainOutDocx = null;
        try {
            task.setStatus(ConversionTask.Status.CONVERTING);

            // 解密输入
            byte[] encInBytes = Files.readAllBytes(task.getEncInPath());
            byte[] pdfBytes = FileEncryptionUtil.decrypt(encInBytes, task.getPassword());
            Files.write(decIn, pdfBytes);

            // 转换（明文 docx 由引擎写到临时目录，base 名与 decIn 一致）
            PdfConverter converter = selectConverter();
            plainOutDocx = converter.convert(decIn, tempDir);

            // 加密输出
            byte[] docxBytes = Files.readAllBytes(plainOutDocx);
            byte[] encOutBytes = FileEncryptionUtil.encrypt(docxBytes, task.getPassword());
            Files.write(encOut, encOutBytes);
            task.setEncOutPath(encOut);
            task.setStatus(ConversionTask.Status.DONE);
            log.info("转换任务 {} 完成（引擎={}）", task.getTaskId(), converter.engineName());
        } catch (Exception e) {
            task.setStatus(ConversionTask.Status.FAILED);
            task.setError(e.getMessage());
            log.error("转换任务 {} 失败: {}", task.getTaskId(), e.getMessage());
        } finally {
            // 删除明文中间文件（输入 pdf、输出 docx），落盘只保留密文
            safeDelete(decIn);
            if (plainOutDocx != null) {
                safeDelete(plainOutDocx);
            }
            // 失败且无输出时，也清理已加密的输入，避免孤儿密文堆积
            if (task.getStatus() == ConversionTask.Status.FAILED && task.getEncOutPath() == null) {
                safeDelete(task.getEncInPath());
            }
        }
    }

    /** 查询任务状态 */
    public ConversionTask getStatus(String taskId) {
        return registry.getByTaskId(taskId);
    }

    /**
     * 凭一次性 token 取出解密后的 Word 内容。调用方在流式返回后应调用 {@link #consume(String)} 清理。
     */
    public DecryptedDoc download(String token) throws IOException {
        ConversionTask task = registry.getByToken(token);
        if (task == null) {
            throw new IllegalArgumentException("下载令牌无效或已失效");
        }
        if (task.getStatus() != ConversionTask.Status.DONE || task.getEncOutPath() == null) {
            if (task.getStatus() == ConversionTask.Status.FAILED) {
                throw new IllegalStateException("转换失败：" + task.getError());
            }
            throw new IllegalStateException("转换尚未完成，当前状态：" + task.getStatus());
        }
        byte[] encOutBytes = Files.readAllBytes(task.getEncOutPath());
        byte[] docxBytes = FileEncryptionUtil.decrypt(encOutBytes, task.getPassword());
        return new DecryptedDoc(docxBytes, toDocxFileName(task.getOriginalFileName()));
    }

    /** 下载完成后清理：删除临时密文并从注册表移除（令牌失效） */
    public void consume(String token) {
        ConversionTask task = registry.getByToken(token);
        if (task == null) {
            return;
        }
        safeDelete(task.getEncInPath());
        safeDelete(task.getEncOutPath());
        registry.remove(task.getTaskId());
        log.info("转换任务 {} 资源已清理", task.getTaskId());
    }

    /** 下载内容载体 */
    public static class DecryptedDoc {
        private final byte[] content;
        private final String fileName;

        public DecryptedDoc(byte[] content, String fileName) {
            this.content = content;
            this.fileName = fileName;
        }

        public byte[] getContent() {
            return content;
        }

        public String getFileName() {
            return fileName;
        }
    }

    /**
     * 定时清理超龄的孤儿临时文件（覆盖用户未下载即关闭页面、或进程重启后的残留）。
     */
    @Scheduled(fixedDelayString = "${helper.convert.cleanup-max-age-minutes:30000*60}")
    public void cleanupStaleFiles() {
        try {
            if (!Files.exists(tempDir)) {
                return;
            }
            Instant threshold = Instant.now().minus(Duration.ofMinutes(cleanupMaxAgeMinutes));
            String[] prefixes = {"enc_in_", "enc_out_", "dec_in_"};
            try (var stream = Files.list(tempDir)) {
                stream.filter(p -> {
                    String name = p.getFileName().toString();
                    for (String pre : prefixes) {
                        if (name.startsWith(pre)) {
                            return true;
                        }
                    }
                    return false;
                }).filter(p -> {
                    try {
                        return Files.getLastModifiedTime(p).toMillis() < threshold.toEpochMilli();
                    } catch (IOException e) {
                        return false;
                    }
                }).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                        log.info("清理超龄临时文件: {}", p);
                    } catch (IOException e) {
                        log.warn("删除临时文件失败 {}: {}", p, e.getMessage());
                    }
                });
            }
        } catch (IOException e) {
            log.warn("临时文件清理异常: {}", e.getMessage());
        }
    }

    private void safeDelete(Path p) {
        if (p == null) {
            return;
        }
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            log.warn("删除临时文件失败 {}: {}", p, e.getMessage());
        }
    }

    /** 生成下载文件名：将 .pdf 替换为 .docx，否则追加 .docx */
    private static String toDocxFileName(String original) {
        if (original == null || original.isBlank()) {
            return "converted.docx";
        }
        int dot = original.toLowerCase().lastIndexOf(".pdf");
        if (dot > 0) {
            return original.substring(0, dot) + ".docx";
        }
        return original + ".docx";
    }
}
