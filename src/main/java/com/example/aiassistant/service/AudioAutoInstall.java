package com.example.aiassistant.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Locale;

@Component
public class AudioAutoInstall {
    @Autowired
    private ResourceLoader resLoader;

    public void installAudioDriver() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        File tmp = new File(System.getProperty("java.io.tmpdir") + "/audio_driver");
        if (!tmp.exists()) tmp.mkdirs();
        try {
            if (os.contains("win")) {
                Resource bat = resLoader.getResource("classpath:script/win/install.bat");
                File f = new File(tmp, "install.bat");
                try (InputStream is = bat.getInputStream()) {
                    Files.copy(is, f.toPath());
                }
                new ProcessBuilder("cmd", "/c", f.getAbsolutePath()).start().waitFor();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
