package com.example.aiassistant.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.commons.io.IOUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;

@Component
public class BaiduApiUtil {
    //翻译配置
    @Value("${baidu-trans.appId}")
    private String transAppId;
    @Value("${baidu-trans.secretKey}")
    private String transSecret;

    //语音ASR配置
    @Value("${baidu-asr.appId}")
    private String asrAppId;
    @Value("${baidu-asr.apiKey}")
    private String asrApiKey;
    @Value("${baidu-asr.secretKey}")
    private String asrSecret;
    @Value("${baidu-asr.langEn}")
    private int enLang;
    @Value("${baidu-asr.langJp}")
    private int jpLang;

    private final String ASR_URL = "https://vop.baidu.com/server_api";
    private final String TRANS_URL = "https://fanyi-api.baidu.com/ait/api/aiTextTranslate";

    //语音识别：使用ASR的appid+secret
    public String asrToText(byte[] pcm, String langType) {
        try {
            int pid = "jp".equals(langType) ? jpLang : enLang;
            String base64 = Base64.getEncoder().encodeToString(pcm);
            long ts = System.currentTimeMillis() / 1000;
            String rand = String.valueOf(new Random().nextInt(99999999));
            //ASR签名：asrAppId + rand + ts + asrSecret
            String sign = DigestUtils.md5Hex(asrAppId + rand + ts + asrSecret);

            JSONObject req = new JSONObject();
            req.put("dev_pid", pid);
            req.put("speech", base64);
            req.put("len", pcm.length);
            req.put("rate", 16000);
            req.put("appid", asrAppId);
            req.put("cuid", "trans123");
            req.put("timestamp", ts);
            req.put("rand", rand);
            req.put("sign", sign);

            String res = postJson(ASR_URL, req.toString());
            JSONObject jo = JSONObject.parseObject(res);
            if (jo.getInteger("err_no") == 0) {
                return jo.getJSONArray("result").getString(0);
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    //文本翻译：使用翻译平台appid+secret
    public String translate(String q, String from, String to) throws Exception {
        //随机salt
        String salt = String.valueOf(System.currentTimeMillis());
        //拼接签名原文：appid+q+salt+密钥
        String sourceSign = transAppId + q + salt + transSecret;
        String sign = DigestUtils.md5Hex(sourceSign).toLowerCase();

        //完整参数：appid必填！
        StringBuilder sb = new StringBuilder();
        sb.append("appid=").append(URLEncoder.encode(transAppId, "UTF-8"))
                .append("&q=").append(URLEncoder.encode(q, "UTF-8"))
                .append("&from=").append(from)
                .append("&to=").append(to)
                .append("&salt=").append(salt)
                .append("&sign=").append(sign);

        HttpPost httpPost = new HttpPost(TRANS_URL);
        //固定请求头
        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        httpPost.setEntity(new StringEntity(sb.toString(), StandardCharsets.UTF_8));

        CloseableHttpClient client = HttpClients.createDefault();
        CloseableHttpResponse resp = client.execute(httpPost);
        String json = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);

        JSONObject obj = JSON.parseObject(json);
        //错误拦截
        if(obj.containsKey("error_code")){
            throw new RuntimeException("翻译异常:"+obj.getString("error_msg"));
        }
        return obj.getJSONArray("trans_result").getJSONObject(0).getString("dst");
    }

    private String postJson(String url, String json) throws Exception {
        HttpPost post = new HttpPost(url);
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(json, StandardCharsets.UTF_8));
        try (CloseableHttpClient http = HttpClients.createDefault();
             CloseableHttpResponse resp = http.execute(post)) {
            InputStream is = resp.getEntity().getContent();
            byte[] bytes = IOUtils.toByteArray(is);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
