package com.huaban.analysis.jieba;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtil {
    public static void copyFile(Path source, Path destination) {
        try {
            Files.copy(source, destination);
        } catch (IOException e) {
            System.err.printf("src file %s  copy failure!%n", source);
        }
    }
}
