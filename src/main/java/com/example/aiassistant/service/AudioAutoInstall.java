package com.example.aiassistant.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Locale;

@Component
public class AudioAutoInstall {
    @Autowired
    private ResourceLoader resLoader;

    public void installAudioDriver() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        // 拼接临时目录
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "audio_driver");
        File tmp = tempDir.toFile();

        try {
            // 目录存在则递归删除所有内容，解决文件已存在报错
            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
            // 重新创建空目录
            Files.createDirectories(tempDir);

            if (os.contains("win")) {
                Resource bat = resLoader.getResource("classpath:script.win/install.bat");
                File f = new File(tmp, "install.bat");
                try (InputStream is = bat.getInputStream()) {
                    Files.copy(is, f.toPath());
                }
                // 执行批处理
                new ProcessBuilder("cmd", "/c", f.getAbsolutePath()).start().waitFor();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}