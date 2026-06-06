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

    //缓存token
    private String accessToken;

    //语音识别：【按百度官方短语音JSON规范重构】
    public String asrToText(byte[] pcm, String langType) {
        try {
            //token为空则获取
            if (accessToken == null || accessToken.isEmpty()) {
                accessToken = getAsrAccessToken();
            }
            String base64 = Base64.getEncoder().encodeToString(pcm);
            int len = pcm.length;

            //语种dev_pid：1537中文、1737英文，百度原生无日语短语音
            int devPid = "en".equals(langType) ? enLang : 1537;

            JSONObject req = new JSONObject();
            //===== 百度ASR JSON必填字段【关键修复：补format、channel】=====
            req.put("format", "pcm");
            req.put("rate", 16000);
            req.put("channel", 1);
            req.put("cuid", "trans123");
            req.put("token", accessToken);
            req.put("len", len);
            req.put("speech", base64);
            req.put("dev_pid", devPid);

            //移除：appid/rand/timestamp/sign（翻译接口字段，ASR不识别，报3300元凶）

            System.out.println("ASR请求JSON：" + req);
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
                }
                return errMsg;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    //获取百度AIP access_token（ASR鉴权用，有效期30天）
    private String getAsrAccessToken() throws Exception {
        String url = TOKEN_URL + "?grant_type=client_credentials&client_id=" + asrApiKey + "&client_secret=" + asrSecret;
        try (CloseableHttpClient http = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(url);
            CloseableHttpResponse resp = http.execute(get);
            String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
            JSONObject json = JSON.parseObject(body);
            return json.getString("access_token");
        }
    }

    //文本翻译：原有逻辑不动（翻译保留appid+sign规则，不受ASR影响）
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

    //POST-JSON通用
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