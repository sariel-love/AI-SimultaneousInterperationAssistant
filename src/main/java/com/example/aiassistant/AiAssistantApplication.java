package com.example.aiassistant;

import com.example.aiassistant.service.AudioAutoInstall;
import com.example.aiassistant.service.AudioRouteManager;
import com.example.aiassistant.ui.SubtitleFloatWindow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class AiAssistantApplication {

    @Autowired
    private AudioAutoInstall autoInstall;

    @Autowired
    private AudioRouteManager routeMgr;

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        SpringApplication.run(AiAssistantApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void afterStart() {
        try {
            // 1. 安装驱动
            autoInstall.installAudioDriver();
            // 2. 启动音频路由
            routeMgr.startRoute();
            // 3. 打开悬浮窗
            SubtitleFloatWindow.startWindow();

            // 4. 在非静态上下文里注册关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                routeMgr.stopRoute();
            }));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}