package com.example.aiassistant;

import com.example.aiassistant.util.BaiduApiUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class text {

    //直接注入项目里现成的工具类，Spring自动装配yml密钥
    @Autowired
    BaiduApiUtil baiduApiUtil;

    //测试文本翻译（英译中）
    @Test
    void testTranslate(){
        String source = "Hello world";
        try {
             String result = baiduApiUtil.translate(source, "en", "zh");
            System.out.println("原文："+source);
            System.out.println("译文："+result);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    //测试日语翻译
    @Test
    void testJpToZh(){
        String source = "こんにちは";
        try {
            String result = baiduApiUtil.translate(source, "jp", "zh");
            System.out.println("日文："+source+"\n译文："+result);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    //ASR语音识别需要pcm二进制，暂时先测文本
}