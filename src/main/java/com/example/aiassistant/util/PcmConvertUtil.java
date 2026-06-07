package com.example.aiassistant.util;

public class PcmConvertUtil {

    /**
     * 48000 双声道16bit → 16000 单声道16bit（百度ASR标准PCM）
     * @param srcPcm 原始48k双声道pcm
     * @return 16k单声道pcm
     */
    public static byte[] convert48k2To16k1(byte[] srcPcm) {
        // 双声道：每帧4字节(L2+R2)；单声道：每帧2字节
        int srcFrameByte = 4;
        int dstFrameByte = 2;
        // 48k→16k 3取1
        int step = 3;

        int totalSrcFrame = srcPcm.length / srcFrameByte;
        int totalDstFrame = totalSrcFrame / step;

        byte[] dst = new byte[totalDstFrame * dstFrameByte];
        int dstIdx = 0;

        for (int i = 0; i < totalSrcFrame; i++) {
            // 只取第0、3、6...帧
            if (i % step != 0) continue;

            // 取出左声道 short
            int lLow = srcPcm[i*4] & 0xFF;
            int lHigh = srcPcm[i*4+1] & 0xFF;
            short left = (short)((lHigh <<8) | lLow);

            // 取出右声道 short
            int rLow = srcPcm[i*4+2] & 0xFF;
            int rHigh = srcPcm[i*4+3] & 0xFF;
            short right = (short)((rHigh <<8) | rLow);

            // 左右平均合并成单声道
            short mono = (short)((left + right)/2);

            // 写入小端字节
            dst[dstIdx++] = (byte)(mono & 0xFF);
            dst[dstIdx++] = (byte)((mono >>8) &0xFF);
        }
        return dst;
    }
}
