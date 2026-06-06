package com.example.aiassistant;

import com.example.aiassistant.service.AudioAutoInstall;
import com.example.aiassistant.service.AudioRouteManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import javax.annotation.Resource;

@SpringBootApplication
public class AiAssistantApplication {
    @Resource
    static AudioAutoInstall autoInstall;
    @Resource
    static AudioRouteManager routeMgr;

    public static void main(String[] args) {
        // 启动优先：自动装驱动→自动配置音频分流
        autoInstall.installAudioDriver();
        routeMgr.startRoute();

        SpringApplication.run(AiAssistantApplication.class, args);
        // 关闭钩子，退出还原声卡配置
        Runtime.getRuntime().addShutdownHook(new Thread(routeMgr::stopRoute));
    }
}
