package com.example.aiassistant;

import com.example.aiassistant.util.BaiduApiUtil;
import javafx.scene.shape.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Paths;

@SpringBootTest
public class text {

    //直接注入项目里现成的工具类，Spring自动装配yml密钥
    @Autowired
    BaiduApiUtil baiduApiUtil;

    //测试文本翻译（英译中）
    @Test
    void testTranslate() {
        String source = "Hello world";
        try {
            String result = baiduApiUtil.translate(source, "en", "zh");
            System.out.println("原文：" + source);
            System.out.println("译文：" + result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //测试日语翻译
    @Test
    void testJpToZh() {
        String source = "こんにちは";
        try {
            String result = baiduApiUtil.translate(source, "jp", "zh");
            System.out.println("日文：" + source + "\n译文：" + result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void testAsrFile() throws Exception {
        //读取本地pcm裸文件
        byte[] pcm = Files.readAllBytes(Paths.get("src/main/resources/debug_audio_16k_mono.pcm"));
        //语种：zh=中文、en=英文
        String text = baiduApiUtil.asrToText(pcm, "zh");
        System.out.println("ASR识别结果：" + text);
        //顺带测试翻译
//        if(text!=null&&!text.isBlank()){
//            String trans = baiduApiUtil.translate(text,"auto","zh");
//            System.out.println("翻译结果："+trans);
//        }
    }
}