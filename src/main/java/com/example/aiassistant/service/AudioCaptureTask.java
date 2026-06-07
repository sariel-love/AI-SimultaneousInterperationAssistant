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

    private TargetDataLine line;
    private volatile boolean running = true;

    @Autowired
    private BaiduApiUtil baiduApiUtil;

    @PostConstruct
    public void startCapture() {
        Thread t = new Thread(this::captureLoop);
        t.setDaemon(true);
        t.start();
    }

    @PreDestroy
    public void stopCapture() {
        running = false;
        if (line != null) line.close();
    }

    private void captureLoop() {
        try {
            Mixer.Info[] mixers = AudioSystem.getMixerInfo();
            logger.info("=== 音频设备列表 ===");
            for (int i = 0; i < mixers.length; i++) {
                logger.info("{}: {}", i, mixers[i].getName());
            }

            Mixer.Info selected = null;
            // 优先使用配置的设备名
            if (devName != null && !devName.isEmpty()) {
                for (Mixer.Info m : mixers) {
                    if (m.getName().toLowerCase().contains(devName.toLowerCase())) {
                        selected = m;
                        logger.info("通过配置名匹配: {}", m.getName());
                        break;
                    }
                }
            }
            // 否则尝试匹配中文麦克风关键字
            if (selected == null) {
                for (Mixer.Info m : mixers) {
                    String name = m.getName();
                    if (name.contains("麦克") || name.toLowerCase().contains("microphone")) {
                        selected = m;
                        logger.info("通过关键字匹配到麦克风: {}", name);
                        break;
                    }
                }
            }
            // 最后回退到索引12的设备（你的日志中真正的麦克风）
            if (selected == null && mixers.length > 12) {
                selected = mixers[12];
                logger.info("回退使用设备索引12: {}", selected.getName());
            }
            if (selected == null && mixers.length > 0) {
                selected = mixers[0];
                logger.warn("未找到麦克风，使用第一个设备: {}", selected.getName());
            }
            if (selected == null) {
                logger.error("没有可用的音频设备");
                return;
            }

            logger.info("最终使用设备: {}", selected.getName());

            // 强制使用 16kHz 单声道 16bit PCM
            AudioFormat format = new AudioFormat(16000f, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            Mixer mixer = AudioSystem.getMixer(selected);

            if (!mixer.isLineSupported(info)) {
                logger.warn("设备不支持目标格式，使用默认格式");
                line = (TargetDataLine) AudioSystem.getLine(info);
                line.open();
                format = line.getFormat();
                logger.info("实际格式: {}Hz, {}bit, {}ch",
                        format.getSampleRate(), format.getSampleSizeInBits(), format.getChannels());
            } else {
                line = (TargetDataLine) mixer.getLine(info);
                line.open(format);
                logger.info("成功打开设备，格式: {}Hz, {}bit, {}ch",
                        format.getSampleRate(), format.getSampleSizeInBits(), format.getChannels());
            }

            line.start();

            int frameSize = format.getFrameSize();
            int oneSecBytes = (int) (format.getSampleRate() * frameSize);
            byte[] buffer = new byte[4096];
            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            logger.info("开始录音... 每1秒发送识别请求。请大声对着麦克风说话，并观察能量值。");

            while (running) {
                int len = line.read(buffer, 0, buffer.length);
                if (len > 0) {
                    bos.write(buffer, 0, len);
                    if (bos.size() >= oneSecBytes) {
                        byte[] pcmData = bos.toByteArray();
                        bos.reset();

                        double energy = calculateEnergy(pcmData);
                        // 打印前几个样本值（调试用）
                        if (pcmData.length >= 10) {
                            short sample1 = (short) ((pcmData[0] & 0xFF) | (pcmData[1] << 8));
                            short sample2 = (short) ((pcmData[2] & 0xFF) | (pcmData[3] << 8));
                            logger.info("样本示例: {}, {}, 平均能量: {:.2f}", sample1, sample2, energy);
                        } else {
                            logger.info("捕获 {} 字节，平均能量: {}", pcmData.length, energy);
                        }

                        if (energy < 100) {
                            logger.warn("能量极低！请检查：1) 系统麦克风音量是否为100% 2) 麦克风是否被静音 3) 是否对着麦克风说话");
                        }

                        // 转换为 16kHz 单声道（如需）
                        byte[] pcm16k = convertTo16kMono(pcmData, format);
                        if (pcm16k != null && pcm16k.length > 0) {
                            try {
                                String result = baiduApiUtil.asrToText(pcm16k, TransWebSocket.currLang);
                                logger.info("ASR结果: {}", result);
                            } catch (Exception e) {
                                logger.error("ASR异常", e);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("录音失败", e);
        } finally {
            if (line != null) line.close();
        }
    }

    private double calculateEnergy(byte[] pcm) {
        if (pcm == null || pcm.length < 2) return 0;
        long sum = 0;
        int samples = pcm.length / 2;
        for (int i = 0; i < samples; i++) {
            short s = (short) ((pcm[i*2] & 0xFF) | (pcm[i*2+1] << 8));
            sum += Math.abs(s);
        }
        return (double) sum / samples;
    }

    private byte[] convertTo16kMono(byte[] pcm, AudioFormat fmt) {
        if (fmt.getSampleRate() == 16000 && fmt.getChannels() == 1 && fmt.getSampleSizeInBits() == 16) {
            return pcm;
        }
        // 立体声转单声道
        if (fmt.getChannels() == 2) {
            int samples = pcm.length / 4;
            byte[] mono = new byte[samples * 2];
            for (int i = 0; i < samples; i++) {
                int left = (pcm[i*4] & 0xFF) | (pcm[i*4+1] << 8);
                int right = (pcm[i*4+2] & 0xFF) | (pcm[i*4+3] << 8);
                int avg = (left + right) / 2;
                mono[i*2] = (byte) avg;
                mono[i*2+1] = (byte) (avg >> 8);
            }
            pcm = mono;
        }
        // 重采样
        if (fmt.getSampleRate() != 16000) {
            return resampleLinear(pcm, (int) fmt.getSampleRate(), 16000);
        }
        return pcm;
    }

    private byte[] resampleLinear(byte[] input, int origRate, int targetRate) {
        if (origRate == targetRate) return input;
        int inSamples = input.length / 2;
        short[] inShort = new short[inSamples];
        for (int i = 0; i < inSamples; i++) {
            inShort[i] = (short) ((input[i*2] & 0xFF) | (input[i*2+1] << 8));
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
            } else if (idx < inSamples) {
                outShort[i] = inShort[idx];
            } else {
                outShort[i] = 0;
            }
        }
        byte[] out = new byte[outSamples * 2];
        for (int i = 0; i < outSamples; i++) {
            out[i*2] = (byte) outShort[i];
            out[i*2+1] = (byte) (outShort[i] >> 8);
        }
        return out;
    }
}