package com.example.aiassistant.service;

import com.example.aiassistant.util.BaiduApiUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;

@Component
public class AudioCaptureTask {
    private static final Logger logger = LoggerFactory.getLogger(AudioCaptureTask.class);

    @Value("${audio.captureDeviceName:}")
    private String devName;
    @Value("${audio.sampleRate:16000}")
    private int targetRate;

    // ===================== 低延迟配置（重点可调）=====================
    private static final int SLICE_DURATION_MS = 800;        // 单切片时长(ms)，越小延迟越低：300/400/500
    private static final int SHORT_SILENCE_MS = 300;        // 短停顿：不截断，继续拼接
    private static final int LONG_SILENCE_MS = 600;         // 长停顿：判定句子结束，输出整句
    private static final int MAX_SPEECH_MS = 6000;          // 单句最大时长兜底
    private static final double SILENCE_ENERGY = 80.0;      // 静默能量阈值
    // =================================================================

    private TargetDataLine line;
    private volatile boolean running = true;

    // 会话累积缓冲区（拼接一整句话）
    private final ByteArrayOutputStream sessionBuf = new ByteArrayOutputStream();
    private long lastVoiceTime = 0;
    private long speechStart = 0;

    @Autowired
    private BaiduApiUtil baiduApiUtil;

    @PostConstruct
    public void startCapture() {
        Thread t = new Thread(this::captureLoop, "Audio-Capture-LowDelay");
        t.setDaemon(true);
        t.start();
        logger.info("低延迟音频采集启动，切片{}ms，长停顿{}ms输出整句", SLICE_DURATION_MS, LONG_SILENCE_MS);
    }

    @PreDestroy
    public void stopCapture() {
        running = false;
        handleRemainingAudio();
        if (line != null) {
            line.close();
            line = null;
        }
        logger.info("音频采集已停止");
    }

    private void captureLoop() {
        try {
            Mixer.Info[] mixers = AudioSystem.getMixerInfo();
            selectDevice(mixers);
            if (line == null) return;

            AudioFormat format = line.getFormat();
            int frameSize = format.getFrameSize();
            // 计算单切片字节数（固定切片时长，控制基础延迟）
            int sliceBytes = (int) (format.getSampleRate() * frameSize * SLICE_DURATION_MS / 1000.0);
            byte[] buffer = new byte[4096];
            ByteArrayOutputStream sliceBuf = new ByteArrayOutputStream();

            logger.info("音频格式：{}Hz, {}bit, {}声道，单切片字节数:{}",
                    format.getSampleRate(), format.getSampleSizeInBits(), format.getChannels(), sliceBytes);

            while (running) {
                int len = line.read(buffer, 0, buffer.length);
                if (len <= 0) continue;

                sliceBuf.write(buffer, 0, len);
                long now = System.currentTimeMillis();

                // 切片攒够，处理当前片段
                if (sliceBuf.size() >= sliceBytes) {
                    byte[] sliceData = sliceBuf.toByteArray();
                    sliceBuf.reset();

                    double energy = calculateEnergy(sliceData);
                    boolean isSilence = energy < SILENCE_ENERGY;

                    // 有声音：更新时间、写入会话缓冲区
                    if (!isSilence) {
                        lastVoiceTime = now;
                        if (speechStart == 0) speechStart = now;
                        sessionBuf.write(sliceData);
                    }

                    // 触发输出整句的条件
                    boolean needOutput = false;
                    long silenceDur = now - lastVoiceTime;

                    // 条件1：长停顿，句子结束
                    if (speechStart > 0 && isSilence && silenceDur > LONG_SILENCE_MS) {
                        needOutput = true;
                    }
                    // 条件2：单句超时兜底
                    if (speechStart > 0 && (now - speechStart) > MAX_SPEECH_MS) {
                        needOutput = true;
                    }

                    if (needOutput) {
                        processWholeSentence(format);
                        resetStatus();
                    }
                }
            }
        } catch (Exception e) {
            logger.error("音频采集异常", e);
        } finally {
            if (line != null) line.close();
            handleRemainingAudio();
        }
    }

    // 选择音频设备 + 打开线路
    private void selectDevice(Mixer.Info[] mixers) throws LineUnavailableException {
        Mixer.Info selected = null;
        if (devName != null && !devName.trim().isEmpty()) {
            for (Mixer.Info m : mixers) {
                if (m.getName().toLowerCase().contains(devName.toLowerCase())) {
                    selected = m;
                    break;
                }
            }
        }
        if (selected == null) {
            for (Mixer.Info m : mixers) {
                String name = m.getName();
                if (name.contains("麦克") || name.toLowerCase().contains("microphone")) {
                    selected = m;
                    break;
                }
            }
        }
        if (selected == null && mixers.length > 12) selected = mixers[12];
        if (selected == null && mixers.length > 0) selected = mixers[0];
        if (selected == null) {
            logger.error("无音频设备");
            return;
        }

        AudioFormat format = new AudioFormat(16000f, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        Mixer mixer = AudioSystem.getMixer(selected);

        if (mixer.isLineSupported(info)) {
            line = (TargetDataLine) mixer.getLine(info);
        } else {
            line = (TargetDataLine) AudioSystem.getLine(info);
            logger.warn("设备不支持标准格式，使用原生格式");
        }
        line.open();
        line.start();
        logger.info("已打开设备：{}", selected.getName());
    }

    // 处理完整一句话：转码 + ASR + 翻译 + 推送
    private void processWholeSentence(AudioFormat format) {
        if (sessionBuf.size() < 100) {
            sessionBuf.reset();
            return;
        }
        byte[] raw = sessionBuf.toByteArray();
        sessionBuf.reset();

        byte[] standardPcm = convertTo16kMono(raw, format);
        if (standardPcm == null || standardPcm.length == 0) return;

        try {
            String text = baiduApiUtil.asrToText(standardPcm, TransWebSocket.currLang);
            if (text != null && !text.trim().isEmpty()) {
                logger.info("识别整句：{}", text);
                String result = baiduApiUtil.translate(text, "auto", "zh");
                System.out.println("【整句】" + result);
                TransWebSocket.sendMsg(result);
            }
        } catch (Exception e) {
            logger.error("ASR/翻译异常", e);
        }
    }

    // 程序退出处理残留音频
    private void handleRemainingAudio() {
        if (sessionBuf.size() > 0) {
            try {
                AudioFormat fmt = line != null ? line.getFormat() : new AudioFormat(16000f,16,1,true,false);
                processWholeSentence(fmt);
            } catch (Exception e) {
                logger.error("收尾音频处理失败", e);
            }
        }
    }

    // 重置计时状态
    private void resetStatus() {
        lastVoiceTime = 0;
        speechStart = 0;
    }

    // 音频能量计算（大端序）
    private double calculateEnergy(byte[] pcm) {
        if (pcm == null || pcm.length < 2) return 0;
        long sum = 0;
        int samples = pcm.length / 2;
        for (int i = 0; i < samples; i++) {
            int pos = i * 2;
            short s = (short) ((pcm[pos] << 8) | (pcm[pos + 1] & 0xFF));
            sum += Math.abs(s);
        }
        return (double) sum / samples;
    }

    // 转码：立体声→单声道 + 重采样 + 大端（百度标准）
    private byte[] convertTo16kMono(byte[] pcm, AudioFormat fmt) {
        if (fmt.getChannels() == 2) {
            int samples = pcm.length / 4;
            byte[] mono = new byte[samples * 2];
            for (int i = 0; i < samples; i++) {
                int base = i * 4;
                short left = (short) ((pcm[base] << 8) | (pcm[base+1] & 0xFF));
                short right = (short) ((pcm[base+2] << 8) | (pcm[base+3] & 0xFF));
                short avg = (short) ((left + right) / 2);
                mono[i*2] = (byte) (avg >> 8);
                mono[i*2+1] = (byte) avg;
            }
            pcm = mono;
        }
        if ((int)fmt.getSampleRate() != 16000) {
            pcm = resampleLinear(pcm, (int) fmt.getSampleRate(), 16000);
        }
        return pcm;
    }

    // 线性重采样（大端）
    private byte[] resampleLinear(byte[] input, int origRate, int targetRate) {
        if (origRate == targetRate) return input;
        int inSamples = input.length / 2;
        short[] inShort = new short[inSamples];
        for (int i = 0; i < inSamples; i++) {
            int pos = i * 2;
            inShort[i] = (short) ((input[pos] << 8) | (input[pos+1] & 0xFF));
        }

        int outSamples = (int) ((long) inSamples * targetRate / origRate);
        short[] outShort = new short[outSamples];
        double ratio = (double) origRate / targetRate;

        for (int i = 0; i < outSamples; i++) {
            double src = i * ratio;
            int idx = (int) src;
            double frac = src - idx;
            if (idx + 1 < inSamples) {
                outShort[i] = (short) (inShort[idx] * (1 - frac) + inShort[idx+1] * frac);
            } else {
                outShort[i] = idx < inSamples ? inShort[idx] : 0;
            }
        }

        byte[] out = new byte[outSamples * 2];
        for (int i = 0; i < outSamples; i++) {
            short val = outShort[i];
            out[i*2] = (byte) (val >> 8);
            out[i*2+1] = (byte) val;
        }
        return out;
    }
}