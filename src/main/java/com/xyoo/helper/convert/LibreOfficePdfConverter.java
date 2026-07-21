package com.xyoo.helper.convert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 基于 LibreOffice 无头模式的 PDF → Word 转换引擎（保真度高，推荐生产使用）。
 * <p>
 * 通过 {@code soffice --headless --convert-to docx --outdir <dir> <pdf>} 调用。
 * soffice 路径可配置，未配置时自动探测常见安装位置。
 * </p>
 */
public class LibreOfficePdfConverter implements PdfConverter {

    private static final Logger log = LoggerFactory.getLogger(LibreOfficePdfConverter.class);

    /** 显式配置的路径（可为空，表示自动探测） */
    private final String configuredPath;

    public LibreOfficePdfConverter(String configuredPath) {
        this.configuredPath = configuredPath;
    }

    @Override
    public String engineName() {
        return "LibreOffice";
    }

    @Override
    public boolean isAvailable() {
        return resolveSoffice() != null;
    }

    /**
     * 解析 soffice 可执行文件：优先用配置路径，否则在 PATH 与常见安装位置中探测。
     */
    private String resolveSoffice() {
        if (configuredPath != null && !configuredPath.isBlank()) {
            if (Files.isExecutable(Path.of(configuredPath))) {
                return configuredPath;
            }
            log.warn("配置的 soffice 路径不可用: {}，尝试自动探测", configuredPath);
        }
        // PATH 中查找
        String fromPath = findOnPath("soffice");
        if (fromPath != null) {
            return fromPath;
        }
        fromPath = findOnPath("libreoffice");
        if (fromPath != null) {
            return fromPath;
        }
        // 常见安装位置
        String[] candidates = {
                "/Applications/LibreOffice.app/Contents/MacOS/soffice",
                "/usr/bin/soffice",
                "/usr/local/bin/soffice",
                "/opt/libreoffice/program/soffice"
        };
        for (String c : candidates) {
            if (Files.isExecutable(Path.of(c))) {
                return c;
            }
        }
        return null;
    }

    private String findOnPath(String cmd) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }
        for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
            Path p = Path.of(dir, cmd);
            if (Files.isExecutable(p)) {
                return p.toString();
            }
        }
        return null;
    }

    @Override
    public Path convert(Path sourcePdf, Path targetDir) throws IOException {
        String soffice = resolveSoffice();
        if (soffice == null) {
            throw new IOException("LibreOffice 不可用：未找到 soffice 可执行文件，请安装 LibreOffice 或通过 helper.convert.soffice-path 配置");
        }

        // 计算期望输出名：<源文件名去扩展名>.docx
        String base = sourcePdf.getFileName().toString();
        int dot = base.lastIndexOf('.');
        String baseName = dot > 0 ? base.substring(0, dot) : base;
        Path expectedOut = targetDir.resolve(baseName + ".docx");

        List<String> cmd = new ArrayList<>();
        cmd.add(soffice);
        cmd.add("--headless");
        cmd.add("--convert-to");
        cmd.add("docx");
        cmd.add("--outdir");
        cmd.add(targetDir.toString());
        cmd.add(sourcePdf.toString());

        log.info("调用 LibreOffice 转换: {}", String.join(" ", cmd));
        Instant start = Instant.now();
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            throw new IOException("启动 LibreOffice 进程失败: " + e.getMessage(), e);
        }

        // 读取子进程输出，避免写满管道阻塞
        String output;
        try (java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
            proc.getInputStream().transferTo(bos);
            output = bos.toString(java.nio.charset.StandardCharsets.UTF_8);
        }

        try {
            int exit = proc.waitFor();
            if (exit != 0) {
                throw new IOException("LibreOffice 转换失败（exit=" + exit + "）: " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("LibreOffice 转换被中断", e);
        }

        // soffice 有时输出名与期望不同（如带路径），做一次兜底查找
        if (Files.exists(expectedOut)) {
            log.info("LibreOffice 转换完成，耗时 {}ms，输出: {}",
                    java.time.Duration.between(start, Instant.now()).toMillis(), expectedOut);
            return expectedOut;
        }
        // 兜底：在输出目录中查找本次新生成的 .docx
        Path fallback = findNewestDocx(targetDir, start);
        if (fallback != null && Files.exists(fallback)) {
            return fallback;
        }
        throw new IOException("LibreOffice 转换完成但未找到输出文件: " + expectedOut);
    }

    private Path findNewestDocx(Path dir, Instant since) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".docx"))
                    .filter(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis() >= since.toEpochMilli() - 2000;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .max((a, b) -> {
                        try {
                            return Long.compare(Files.getLastModifiedTime(a).toMillis(),
                                    Files.getLastModifiedTime(b).toMillis());
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .orElse(null);
        }
    }
}
