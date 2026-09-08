package com.qxt.yuexiang.file.util;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/**
 * MP3 时长解析（零依赖，纯 Java 读帧头）：
 * 1. 优先解析 Xing/Info VBR 头（含总帧数，一次读取即得精确时长）
 * 2. 无 VBR 头（CBR）按「文件大小 ÷ 比特率」估算
 * 3. 非常规文件返回 0，调用方按"未知时长"兜底
 *
 * 参考规范：MPEG Audio Frame Header（www.mp3-tech.org）
 */
public final class Mp3DurationParser {

    private Mp3DurationParser() {
    }

    /** MPEG1 Layer3 比特率表（kbps），索引 1~14 有效 */
    private static final int[] BITRATES_V1_L3 = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0};
    /** MPEG2/2.5 Layer3 比特率表（kbps） */
    private static final int[] BITRATES_V2_L3 = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0};
    /** MPEG1 Layer3 采样率表（Hz），索引 0~2 对应 MPEG1/MPEG2/MPEG2.5 */
    private static final int[][] SAMPLE_RATES = {
            {44100, 48000, 32000},   // MPEG1
            {22050, 24000, 16000},   // MPEG2
            {11025, 12000, 8000},    // MPEG2.5
    };

    /** 解析时长，失败返回 0 */
    public static int parseSeconds(Path path) {
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            long fileSize = raf.length();
            if (fileSize < 1024) {
                return 0;
            }
            // 跳过 ID3v2 标签找第一个音频帧
            long frameOffset = skipId3v2(raf);
            raf.seek(frameOffset);
            FrameHeader header = readFrameHeader(raf, frameOffset, fileSize);
            if (header == null) {
                return 0;
            }
            // Xing/Info 头：精确帧数 → 精确时长
            Integer vbrSeconds = tryParseXing(raf, frameOffset, header);
            if (vbrSeconds != null && vbrSeconds > 0) {
                return vbrSeconds;
            }
            // CBR 兜底：文件大小 ÷ 比特率
            long audioBytes = fileSize - frameOffset;
            return (int) (audioBytes * 8L / header.bitrateKbps / 1000L);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 跳过 ID3v2 标签，返回音频数据起始偏移 */
    private static long skipId3v2(RandomAccessFile raf) throws IOException {
        raf.seek(0);
        byte[] tag = new byte[10];
        if (raf.read(tag) == 10 && tag[0] == 'I' && tag[1] == 'D' && tag[2] == '3') {
            // syncsafe 整数（每字节只含 7 位有效）
            long size = ((tag[6] & 0x7F) << 21) | ((tag[7] & 0x7F) << 14) | ((tag[8] & 0x7F) << 7) | (tag[9] & 0x7F);
            return 10 + size;
        }
        return 0;
    }

    /** 从 offset 起搜索帧同步字并解析头部（最多往前找 64KB） */
    private static FrameHeader readFrameHeader(RandomAccessFile raf, long start, long fileSize) throws IOException {
        byte[] buf = new byte[4];
        long searchEnd = Math.min(start + 64 * 1024, fileSize - 4);
        for (long pos = start; pos < searchEnd; pos++) {
            raf.seek(pos);
            if (raf.read(buf) != 4) {
                return null;
            }
            if ((buf[0] & 0xFF) != 0xFF || (buf[1] & 0xE0) != 0xE0) {
                continue; // 11 位帧同步
            }
            int versionBits = (buf[1] >> 3) & 0x03;  // 00=MPEG2.5 10=MPEG2 11=MPEG1
            int layerBits = (buf[1] >> 1) & 0x03;    // 01=Layer3
            int bitrateIdx = (buf[2] >> 4) & 0x0F;
            int sampleIdx = (buf[2] >> 2) & 0x03;
            int channelMode = (buf[3] >> 6) & 0x03;  // 11=单声道
            if (versionBits == 1 || layerBits != 1 || bitrateIdx == 0 || bitrateIdx == 15 || sampleIdx == 3) {
                continue; // 保留值，非有效帧
            }
            int versionIdx = versionBits == 3 ? 0 : (versionBits == 2 ? 1 : 2);
            int[] bitrateTable = versionIdx == 0 ? BITRATES_V1_L3 : BITRATES_V2_L3;
            int bitrateKbps = bitrateTable[bitrateIdx];
            int sampleRate = SAMPLE_RATES[versionIdx][sampleIdx];
            if (bitrateKbps == 0 || sampleRate == 0) {
                continue;
            }
            int samplesPerFrame = versionIdx == 0 ? 1152 : 576; // Layer3
            return new FrameHeader(bitrateKbps, sampleRate, samplesPerFrame, versionIdx, channelMode);
        }
        return null;
    }

    /** 尝试解析 Xing/Info 头（首帧侧信息内），返回总秒数；无则 null */
    private static Integer tryParseXing(RandomAccessFile raf, long frameOffset, FrameHeader h) throws IOException {
        // 侧信息长度：MPEG1 单声道 17 / 立体声 32；MPEG2/2.5 单声道 9 / 立体声 17
        boolean mono = h.channelMode() == 3;
        long sideInfo = h.versionIdx() == 0 ? (mono ? 17 : 32) : (mono ? 9 : 17);
        long xingPos = frameOffset + 4 + sideInfo;
        byte[] tag = new byte[4];
        if (xingPos + 16 > raf.length()) {
            return null;
        }
        raf.seek(xingPos);
        if (raf.read(tag) != 4) {
            return null;
        }
        boolean isXing = tag[0] == 'X' && tag[1] == 'i' && tag[2] == 'n' && tag[3] == 'g';
        boolean isInfo = tag[0] == 'I' && tag[1] == 'n' && tag[2] == 'f' && tag[3] == 'o';
        if (!isXing && !isInfo) {
            return null;
        }
        byte[] flags = new byte[4];
        if (raf.read(flags) != 4) {
            return null;
        }
        boolean hasFrames = (flags[3] & 0x01) != 0;
        if (!hasFrames) {
            return null;
        }
        byte[] num = new byte[4];
        if (raf.read(num) != 4) {
            return null;
        }
        long totalFrames = ((num[0] & 0xFFL) << 24) | ((num[1] & 0xFFL) << 16) | ((num[2] & 0xFFL) << 8) | (num[3] & 0xFFL);
        double seconds = totalFrames * h.samplesPerFrame() / (double) h.sampleRate();
        return (int) Math.round(seconds);
    }

    /** 解析后的帧头关键字段 */
    private record FrameHeader(int bitrateKbps, int sampleRate, int samplesPerFrame,
                               int versionIdx, int channelMode) {
    }
}
