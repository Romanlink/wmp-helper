package com.xyoo.helper.convert;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文档转换「整个流程」集成测试（不依赖 MySQL/Redis，手动装配 DocConvertService）。
 * <p>
 * 覆盖：加密上传 → 异步转换 → 解密下载 → 临时文件删除（用完即焚）。
 * </p>
 */
class DocConvertServiceFlowTest {

    @TempDir
    Path convertTemp;

    private ConversionTaskRegistry registry;
    private DocConvertService service;

    @BeforeEach
    void setUp() throws Exception {
        registry = new ConversionTaskRegistry();
        service = new DocConvertService(registry);
        ReflectionTestUtils.setField(service, "tempDirStr", convertTemp.toString());
        ReflectionTestUtils.setField(service, "sofficePath", "");
        ReflectionTestUtils.setField(service, "cleanupMaxAgeMinutes", 30L);
        service.init();
    }

    /** 生成一段含可识别文本的 PDF 字节 */
    private byte[] makePdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Hello RAG bge-m3 vector search test document content.");
                cs.endText();
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);
            return bos.toByteArray();
        }
    }

    @Test
    void testFullFlowEncryptConvertDownloadDelete() throws Exception {
        byte[] pdfBytes = makePdf();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", pdfBytes);

        // 1) 上传（加密落临时目录，异步转换）
        ConversionTask task = service.upload(file);
        assertNotNull(task.getTaskId());
        assertNotNull(task.getToken());
        Path encIn = task.getEncInPath();
        assertTrue(Files.exists(encIn), "加密输入文件应存在");

        // 2) 轮询直到转换完成（最多 30s）
        ConversionTask.Status status = ConversionTask.Status.QUEUED;
        for (int i = 0; i < 60; i++) {
            ConversionTask t = service.getStatus(task.getTaskId());
            assertNotNull(t, "任务不应丢失");
            status = t.getStatus();
            if (status == ConversionTask.Status.DONE || status == ConversionTask.Status.FAILED) {
                break;
            }
            TimeUnit.MILLISECONDS.sleep(500);
        }
        assertEquals(ConversionTask.Status.DONE, status, "转换应成功完成");

        // 3) 明文中间文件应已被删除（用完即焚）
        Path plainIn = convertTemp.resolve("dec_in_" + task.getTaskId() + ".pdf");
        Path plainOut = convertTemp.resolve("dec_in_" + task.getTaskId() + ".docx");
        assertFalse(Files.exists(plainIn), "明文输入 PDF 应已删除");
        assertFalse(Files.exists(plainOut), "明文输出 docx 应已删除");
        assertTrue(Files.exists(task.getEncOutPath()), "加密输出文件应存在");

        // 4) 下载（解密）
        DocConvertService.DecryptedDoc doc = service.download(task.getToken());
        assertTrue(doc.getContent().length > 0, "下载内容不应为空");
        assertTrue(doc.getFileName().endsWith(".docx"), "下载文件名应为 .docx: " + doc.getFileName());

        // 5) 解密产物应为合法 docx（可用 POI 打开且含原文）
        try (XWPFDocument xdoc = new XWPFDocument(
                new java.io.ByteArrayInputStream(doc.getContent()))) {
            StringBuilder sb = new StringBuilder();
            xdoc.getParagraphs().forEach(p -> sb.append(p.getText()).append(" "));
            assertTrue(sb.toString().contains("RAG"),
                    "转换后的 docx 应保留原文内容，实际: " + sb);
        }

        // 6) 下载完成后清理：临时密文删除 + 令牌失效
        service.consume(task.getToken());
        assertFalse(Files.exists(encIn), "消费后加密输入应删除");
        assertFalse(Files.exists(task.getEncOutPath()), "消费后加密输出应删除");
        assertEquals(null, service.getStatus(task.getTaskId()), "消费后任务应从注册表移除");
        assertEquals(null, registry.getByToken(task.getToken()), "消费后令牌应失效");
    }
}
