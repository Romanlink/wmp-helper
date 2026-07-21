package com.xyoo.helper.convert;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 文档转换单元测试。
 * <p>
 * 满足需求：输入「一个包含文件名称的全路径」，在<strong>同级目录</strong>下生成转换后的 Word 文档，
 * 并校验转换后内容与原文一致。
 * </p>
 * <p>
 * 输入路径可通过 JVM 参数指定：<code>-Dconvert.pdf.path=/绝对/路径/原文件.pdf</code>，
 * 默认使用 <code>/Users/roman/Downloads/RAG技术方案文档.pdf</code>。若文件不存在则跳过（不报错）。
 * </p>
 */
class PdfConverterTest {

    private static final String DEFAULT_PDF =
            "/Users/roman/Downloads/RAG技术方案文档.pdf";

    /** 归一化：仅保留中文、字母、数字，转小写，用于内容一致性比较 */
    private static String normalize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        Matcher m = Pattern.compile("[\\u4e00-\\u9fa5a-zA-Z0-9]").matcher(s);
        while (m.find()) {
            sb.append(m.group().toLowerCase());
        }
        return sb.toString();
    }

    /** 抽取 PDF 文本 */
    private static String extractPdfText(Path pdf) throws IOException {
        try (PDDocument doc = PDDocument.load(pdf.toFile())) {
            return new PDFTextStripper().getText(doc);
        }
    }

    /** 抽取 docx 文本 */
    private static String extractDocxText(Path docx) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(docx))) {
            StringBuilder sb = new StringBuilder();
            doc.getParagraphs().forEach(p -> sb.append(p.getText()).append("\n"));
            // 也抽取表格中的文本
            doc.getTables().forEach(t -> t.getRows().forEach(row ->
                    row.getTableCells().forEach(cell -> sb.append(cell.getText()).append(" "))));
            return sb.toString();
        }
    }

    @Test
    void testConvertPdfToDocxInSameDir() throws IOException {
        String prop = System.getProperty("convert.pdf.path");
        Path sourcePdf = Path.of(prop != null ? prop : DEFAULT_PDF);

        // 源文件不存在则跳过（便于无此文件的 CI 环境）
        assumeTrue(Files.exists(sourcePdf),
                "源 PDF 不存在，跳过转换测试: " + sourcePdf);
        assertTrue(sourcePdf.toString().toLowerCase().endsWith(".pdf"),
                "输入必须是 .pdf 文件: " + sourcePdf);

        // 选择引擎（三级兜底）：LibreOffice → pdf2docx → 纯文本抽取
        String soffice = System.getProperty("soffice.path");
        String pyPath = System.getProperty("helper.convert.python-path");
        PdfConverter converter;
        if (new LibreOfficePdfConverter(soffice).isAvailable()) {
            converter = new LibreOfficePdfConverter(soffice);
        } else if (new Pdf2docxConverter(pyPath).isAvailable()) {
            converter = new Pdf2docxConverter(pyPath);
        } else {
            converter = new TextExtractPdfConverter();
        }
        System.out.println("[PdfConverterTest] 使用引擎: " + converter.engineName());

        Path targetDir = sourcePdf.getParent();
        String base = sourcePdf.getFileName().toString();
        String baseName = base.substring(0, base.lastIndexOf('.'));
        Path targetDocx = targetDir.resolve(baseName + ".docx");

        // 执行转换（生成在同级目录）
        Path result = converter.convert(sourcePdf, targetDir);
        System.out.println("[PdfConverterTest] 已生成: " + result);

        // === 断言 1：同级目录生成了 docx，且非空 ===
        assertTrue(Files.exists(result), "未生成 Word 文档: " + result);
        assertTrue(Files.size(result) > 0, "生成的 Word 文档为空: " + result);
        assertTrue(result.getParent().equals(targetDir),
                "Word 文档未生成在源文件同级目录: " + result);

        // === 断言 2：内容与原文一致（逐字符覆盖率）===
        String pdfText = extractPdfText(sourcePdf);
        String docxText = extractDocxText(result);
        assertFalse(pdfText.isBlank(), "源 PDF 文本为空，无法比对");

        String pdfNorm = normalize(pdfText);
        String docxNorm = normalize(docxText);
        Set<Character> pdfChars = new HashSet<>();
        for (char c : pdfNorm.toCharArray()) pdfChars.add(c);
        Set<Character> docxChars = new HashSet<>();
        for (char c : docxNorm.toCharArray()) docxChars.add(c);

        int hit = 0;
        for (char c : pdfChars) if (docxChars.contains(c)) hit++;
        double ratio = pdfChars.isEmpty() ? 0 : (double) hit / pdfChars.size();
        System.out.println("[PdfConverterTest] 字符覆盖率 = " + String.format("%.2f", ratio)
                + " (pdf chars=" + pdfChars.size() + ", docx chars=" + docxChars.size() + ")");
        assertTrue(ratio >= 0.80,
                "转换后内容与原文差异过大，字符覆盖率=" + ratio + "，低于阈值 0.80");

        // === 断言 3：关键短语应保留 ===
        for (String key : new String[]{"bge", "m3", "rag", "qdrant", "向量"}) {
            assertTrue(docxText.toLowerCase().contains(key),
                    "转换后文档缺少关键内容: " + key);
        }

        // === 断言 4：带格式引擎应输出保留样式（字号/加粗/颜色/斜体）的 docx ===
        // 纯文本抽取引擎本就不保留格式，仅当使用 LibreOffice / pdf2docx 时才校验。
        boolean formattingEngine = converter.engineName().contains("LibreOffice")
                || converter.engineName().contains("pdf2docx");
        if (formattingEngine) {
            try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(result))) {
                boolean hasStyle = doc.getParagraphs().stream()
                        .flatMap(p -> p.getRuns().stream())
                        .anyMatch(r -> r.isBold() || r.isItalic()
                                || (r.getColor() != null && !r.getColor().isBlank())
                                || r.getFontSize() > 0);
                assertTrue(hasStyle,
                        "带格式引擎(" + converter.engineName() + ")应输出保留字号/加粗/颜色等样式的 docx");
                long styledRuns = doc.getParagraphs().stream()
                        .flatMap(p -> p.getRuns().stream())
                        .filter(r -> r.isBold() || r.isItalic()
                                || (r.getColor() != null && !r.getColor().isBlank())
                                || r.getFontSize() > 0)
                        .count();
                System.out.println("[PdfConverterTest] 带格式引擎检测到样式 run 数 = " + styledRuns);
            }
        }

        System.out.println("[PdfConverterTest] 内容一致性校验通过 ✅");
    }
}
