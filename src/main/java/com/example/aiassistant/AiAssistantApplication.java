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
        System.out.println("=== SpringBoot 服务启动完成 ===");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void afterAppReady() {
        // 1. 单独线程执行音频驱动+路由（防止阻塞主线程）
        new Thread(this::runAudioTask, "Audio-Task-Thread").start();

        // 2. 单独线程启动悬浮窗
        new Thread(() -> {
            try {
                System.out.println("执行悬浮窗启动");
                SubtitleFloatWindow.startWindow();
            } catch (Exception e) {
                System.err.println("悬浮窗启动异常：");
                e.printStackTrace();
            }
        }, "Float-Window-Thread").start();

        // 3. 注册退出钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                routeMgr.stopRoute();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
    }

    /**
     * 音频初始化任务（独立线程，卡死/报错都不影响窗口）
     */
    private void runAudioTask() {
        try {
            System.out.println("开始安装音频驱动");
            autoInstall.installAudioDriver();
            System.out.println("驱动安装完成，启动音频路由");
            routeMgr.startRoute();
            System.out.println("音频路由启动成功");
        } catch (Exception e) {
            System.err.println("音频初始化失败：");
            e.printStackTrace();
        }
    }
}