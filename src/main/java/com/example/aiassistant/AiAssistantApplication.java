package com.example.aiassistant;

import com.example.aiassistant.service.AudioAutoInstall;
import com.example.aiassistant.service.AudioRouteManager;
import com.example.aiassistant.ui.SubtitleFloatWindow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import javax.annotation.Resource;

@SpringBootApplication
public class AiAssistantApplication {
//    @Resource
//     static  AudioAutoInstall autoInstall;
//    @Resource
//     static AudioRouteManager routeMgr;

    public static void main(String[] args) {
        // 启动优先：自动装驱动→自动配置音频分流
//        autoInstall.installAudioDriver();
//        routeMgr.startRoute();
        System.setProperty("java.awt.headless", "false");
        SpringApplication.run(AiAssistantApplication.class, args);
        // 关闭钩子，退出还原声卡配置
//        Runtime.getRuntime().addShutdownHook(new Thread(routeMgr::stopRoute));
    }
    @EventListener(ApplicationReadyEvent.class)
    public void openFloatWindow() {
        SubtitleFloatWindow.startWindow();
    }
}
