package com.ps117.infrastructure;

import java.nio.file.Files;
import java.nio.file.Path;

public class FileManager {
    public static void ensureDirectory(Path path) throws Exception {
        Files.createDirectories(path);
    }
}
