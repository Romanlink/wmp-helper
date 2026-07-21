package com.xyoo.helper.convert;

import java.io.IOException;
import java.nio.file.Path;

/**
 * PDF → Word 转换引擎抽象。
 * <p>
 * 实现可插拔：生产环境用 {@link LibreOfficePdfConverter}（高保真），
 * 无 LibreOffice 环境（如开发机未安装）自动回退到 {@link TextExtractPdfConverter}。
 * </p>
 */
public interface PdfConverter {

    /**
     * 将源 PDF 转换为 Word 文档，输出到指定目录。
     *
     * @param sourcePdf 源 PDF 路径（明文，调用方负责其生命周期）
     * @param targetDir 输出目录（已存在）
     * @return 生成的 .docx 文件路径
     * @throws IOException 转换失败
     */
    Path convert(Path sourcePdf, Path targetDir) throws IOException;

    /**
     * 引擎名称（用于日志与状态展示）
     */
    String engineName();

    /**
     * 当前引擎是否可用（不可用时应回退到其它实现）
     */
    boolean isAvailable();
}
