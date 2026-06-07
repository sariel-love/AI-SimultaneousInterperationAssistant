package com.example.aiassistant.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

@Component
public class AudioRouteManager {
    @Autowired
    private ResourceLoader resLoader;
    private Process proc;
    private File tmpDir;

    public void startRoute() {
        tmpDir = new File(System.getProperty("java.io.tmpdir") + "/audio_route");
        if (!tmpDir.exists()) tmpDir.mkdirs();
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        try {
            File script;
            if (os.contains("win")) {
                Resource r = resLoader.getResource("classpath:script/win/route_start.bat");
                script = new File(tmpDir, "route_start.bat");
                try (InputStream is = r.getInputStream()) {
                    Files.copy(is, script.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                proc = new ProcessBuilder("cmd", "/c", script.getAbsolutePath()).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopRoute() {
        try {
            if (proc != null) proc.destroy();
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            File stop;
            if (os.contains("win")) {
                Resource r = resLoader.getResource("classpath:script/win/route_stop.bat");
                stop = new File(tmpDir, "route_stop.bat");
                // 使用 try-with-resources 并允许覆盖已存在的目标文件
                try (InputStream is = r.getInputStream()) {
                    Files.copy(is, stop.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                new ProcessBuilder("cmd", "/c", stop.getAbsolutePath()).start().waitFor();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}