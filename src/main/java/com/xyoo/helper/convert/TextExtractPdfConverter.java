package com.xyoo.helper.convert;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 不依赖 LibreOffice 的回退转换引擎：用 PDFBox 抽取 PDF 文本，再用 Apache POI 写入 .docx。
 * <p>
 * 优点：零外部依赖、必然可用、便于自动化自测与内容一致性校验；
 * 局限：仅保留文本（不保留复杂排版/图片），保真度低于 LibreOffice。
 * </p>
 */
public class TextExtractPdfConverter implements PdfConverter {

    private static final Logger log = LoggerFactory.getLogger(TextExtractPdfConverter.class);

    @Override
    public String engineName() {
        return "TextExtract(PDFBox+POI)";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Path convert(Path sourcePdf, Path targetDir) throws IOException {
        String base = sourcePdf.getFileName().toString();
        int dot = base.lastIndexOf('.');
        String baseName = dot > 0 ? base.substring(0, dot) : base;
        Path out = targetDir.resolve(baseName + ".docx");

        // 1) 抽取 PDF 文本
        String pdfText;
        try (PDDocument doc = PDDocument.load(sourcePdf.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            pdfText = stripper.getText(doc);
        }

        // 2) 写入 docx（按段落切分，尽量保留可读结构）
        try (XWPFDocument xdoc = new XWPFDocument()) {
            for (String para : pdfText.split("\\n")) {
                XWPFParagraph p = xdoc.createParagraph();
                p.createRun().setText(para);
            }
            try (java.io.OutputStream os = Files.newOutputStream(out)) {
                xdoc.write(os);
            }
        }

        log.info("文本抽取转换完成，输出: {}（字符数 {}）", out, pdfText.length());
        return out;
    }
}
