package com.ps117.reporting;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public class ReportExporter {
    public void writeFile(Path path, List<String> lines) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, lines, java.nio.charset.StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }
}
