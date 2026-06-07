package com.example.aiassistant.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
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
import java.util.UUID;

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

    private final String ASR_URL = "http://vop.baidu.com/server_api";
    private final String TRANS_URL = "https://fanyi-api.baidu.com/ait/api/aiTextTranslate";
    private final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";

    // 缓存Token + 过期时间（新增：自动续期）
    private String accessToken;
    private long tokenExpireTime = 0;
    // 全局固定设备ID
    private static final String CUID = UUID.randomUUID().toString().replace("-", "");



    public String getAccessToken() {
        // Token 过期自动重新获取
        if (System.currentTimeMillis() > tokenExpireTime || accessToken == null || accessToken.isEmpty()) {
            try {
                refreshAsrToken();
            } catch (Exception e) {
                e.printStackTrace();
                return "";
            }
        }
        return accessToken;
    }

    //语音识别
    public String asrToText(byte[] pcm, String langType) {
        try {
            //token为空/过期则刷新
            accessToken = getAccessToken();
            if (accessToken.isEmpty()) {
                return "Token获取失败";
            }
            String base64 = Base64.getEncoder().encodeToString(pcm);
            int len = pcm.length;

            int devPid = "en".equals(langType) ? enLang : 1537;

            JSONObject req = new JSONObject();
            req.put("format", "pcm");
            req.put("rate", 16000);
            req.put("channel", 1);
            req.put("cuid", CUID);
            req.put("token", accessToken);
            req.put("len", len);
            req.put("speech", base64);
            req.put("dev_pid", devPid);

            String res = postJson(ASR_URL, req.toString());
            JSONObject jo = JSONObject.parseObject(res);
            if (jo.getInteger("err_no") == 0) {
                return jo.getJSONArray("result").getString(0);
            } else {
                String errMsg = jo.getString("err_msg");
                System.out.println("ASR错误码：" + jo.getInteger("err_no") + "，错误信息：" + errMsg);
                //token失效清空，下次重新拉取
                if ("invalid token".equalsIgnoreCase(errMsg)) {
                    accessToken = null;
                    tokenExpireTime = 0;
                }
                return errMsg;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 获取并缓存Token + 记录过期时间（改造原有方法，增加过期逻辑）
    private void refreshAsrToken() throws Exception {
        String url = TOKEN_URL + "?grant_type=client_credentials&client_id=" + asrApiKey + "&client_secret=" + asrSecret;
        try (CloseableHttpClient http = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(url);
            CloseableHttpResponse resp = http.execute(get);
            String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
            JSONObject json = JSON.parseObject(body);
            accessToken = json.getString("access_token");
            long expiresIn = json.getLongValue("expires_in");
            // 提前60秒判定过期，避免临界失效
            tokenExpireTime = System.currentTimeMillis() + (expiresIn - 60) * 1000;
        }
    }

    //文本翻译
    public String translate(String q, String from, String to) throws Exception {
        String salt = String.valueOf(System.currentTimeMillis());
        String sourceSign = transAppId + q + salt + transSecret;
        String sign = org.apache.commons.codec.digest.DigestUtils.md5Hex(sourceSign).toLowerCase();

        StringBuilder sb = new StringBuilder();
        sb.append("appid=").append(URLEncoder.encode(transAppId, "UTF-8"))
                .append("&q=").append(URLEncoder.encode(q, "UTF-8"))
                .append("&from=").append(from)
                .append("&to=").append(to)
                .append("&salt=").append(salt)
                .append("&sign=").append(sign);

        HttpPost httpPost = new HttpPost(TRANS_URL);
        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        httpPost.setEntity(new StringEntity(sb.toString(), StandardCharsets.UTF_8));

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse resp = client.execute(httpPost)) {
            String json = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
            JSONObject obj = JSON.parseObject(json);
            if (obj.containsKey("error_code")) {
                throw new RuntimeException("翻译异常:" + obj.getString("error_msg"));
            }
            return obj.getJSONArray("trans_result").getJSONObject(0).getString("dst");
        }
    }

    //POST-JSON通用（原有逻辑保留）
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