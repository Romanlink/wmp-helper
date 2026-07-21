package com.xyoo.helper.convert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Python {@code pdf2docx} 的 PDF → Word 转换引擎（保真度较高，本地/无 LibreOffice 环境首选）。
 * <p>
 * 通过子进程调用 {@code <python> -c "from pdf2docx import Converter; ..."} 完成转换。
 * pdf2docx 专为「PDF → DOCX」设计，能较好保留字号、颜色、字体、加粗/斜体、标题层级与表格等格式，
 * 通常优于纯文本抽取（TextExtract），复杂度又低于部署 LibreOffice。
 * </p>
 * <p>
 * Python 解释器解析顺序：显式配置路径 → 托管的隔离 venv（~/.workbuddy/binaries/python/envs/default）→
 * PATH 中的 python3 / python。只要其中任意一个装了 pdf2docx 即可启用。
 * </p>
 */
public class Pdf2docxConverter implements PdfConverter {

    private static final Logger log = LoggerFactory.getLogger(Pdf2docxConverter.class);

    /** 显式配置的 Python 路径（可为空，表示自动探测） */
    private final String configuredPath;

    /** 解析后缓存的 Python 路径（"" 表示未找到） */
    private String resolvedPython;
    private boolean resolved;

    public Pdf2docxConverter(String configuredPath) {
        this.configuredPath = configuredPath;
    }

    @Override
    public String engineName() {
        return "pdf2docx(Python)";
    }

    @Override
    public boolean isAvailable() {
        return resolvePython() != null;
    }

    /** 解析可用的 Python 解释器（已安装 pdf2docx） */
    private String resolvePython() {
        if (resolved) {
            return resolvedPython.isEmpty() ? null : resolvedPython;
        }
        synchronized (this) {
            if (resolved) {
                return resolvedPython.isEmpty() ? null : resolvedPython;
            }
            List<String> candidates = new ArrayList<>();
            if (configuredPath != null && !configuredPath.isBlank()) {
                candidates.add(configuredPath);
            }
            // 托管的隔离 venv（macOS/Linux 均在 ~/.workbuddy 下，按 user.home 计算以跨用户）
            String managed = System.getProperty("user.home")
                    + "/.workbuddy/binaries/python/envs/default/bin/python";
            candidates.add(managed);
            candidates.add("python3");
            candidates.add("python");

            for (String c : candidates) {
                if (c == null) {
                    continue;
                }
                // python3 / python 走 PATH，无法以文件是否存在判断，直接尝试导入
                boolean isPathProbe = !(c.equals("python3") || c.equals("python"));
                if (isPathProbe && !Files.isRegularFile(Path.of(c))) {
                    continue;
                }
                if (canImportPdf2docx(c)) {
                    resolvedPython = c;
                    resolved = true;
                    return resolvedPython;
                }
            }
            resolvedPython = "";
            resolved = true;
            return null;
        }
    }

    /** 判断该 Python 能否 import pdf2docx */
    private boolean canImportPdf2docx(String python) {
        try {
            Process p = new ProcessBuilder(python, "-c", "import pdf2docx")
                    .redirectErrorStream(true)
                    .start();
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                p.getInputStream().transferTo(bos);
            }
            return p.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    @Override
    public Path convert(Path sourcePdf, Path targetDir) throws IOException {
        String py = resolvePython();
        if (py == null) {
            throw new IOException("pdf2docx 不可用：未找到已安装 pdf2docx 的 Python，"
                    + "请先执行 pip install pdf2docx（或配置 helper.convert.python-path）");
        }

        String base = sourcePdf.getFileName().toString();
        int dot = base.lastIndexOf('.');
        String baseName = dot > 0 ? base.substring(0, dot) : base;
        Path out = targetDir.resolve(baseName + ".docx");

        List<String> cmd = new ArrayList<>();
        cmd.add(py);
        cmd.add("-c");
        cmd.add("import sys; from pdf2docx import Converter;"
                + "c=Converter(sys.argv[1]); c.convert(sys.argv[2]); c.close()");
        cmd.add(sourcePdf.toString());
        cmd.add(out.toString());

        log.info("调用 pdf2docx 转换: {}", String.join(" ", cmd));
        Instant start = Instant.now();
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        String output;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            proc.getInputStream().transferTo(bos);
            output = bos.toString(StandardCharsets.UTF_8);
        }

        try {
            int exit = proc.waitFor();
            if (exit != 0) {
                throw new IOException("pdf2docx 转换失败（exit=" + exit + "）: " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("pdf2docx 转换被中断", e);
        }

        if (!Files.exists(out)) {
            throw new IOException("pdf2docx 转换完成但未找到输出文件: " + out);
        }
        log.info("pdf2docx 转换完成，耗时 {}ms，输出: {}",
                Duration.between(start, Instant.now()).toMillis(), out);
        return out;
    }
}
