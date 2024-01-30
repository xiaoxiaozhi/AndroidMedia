package com.mymedia.audiomixing;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;

import com.mymedia.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class MusicProcess {
    private static final long TIMEOUT = 1000;
    static String TAG = "MusicProcess";

    /**
     * @param videoInput
     * @param audioInput
     * @param output
     * @param startTimeUs 剪辑开始时间 单位微秒  1秒乘以10的6次方
     * @param endTimeUs   剪辑结束时间
     * @param videoVolume 视频音量大小0-100
     * @param aacVolume   音频音量大小0-100
     * @throws Exception
     */
    public static void mixAudioTrack(Context context, final String videoInput,//
                                     final String audioInput,
                                     final String output,
                                     final Integer startTimeUs,//剪辑开始时间
                                     final Integer endTimeUs,//剪辑结束时间
                                     int videoVolume,//视频音量大小0-100
                                     int aacVolume//音频音量大小0-100
    ) throws Exception {

//mp3  混音     压缩  数据    pcm
//还没生成
        final File videoPcmFile = new File(context.getFilesDir(), "video" + ".pcm");
//
        decodeToPCM(videoInput, videoPcmFile.getAbsolutePath(), startTimeUs, endTimeUs);


//        下载下来的音乐转换城pcm
        File aacPcmFile = new File(context.getFilesDir(), "audio" + ".pcm");
        decodeToPCM(audioInput,
                aacPcmFile.getAbsolutePath(), startTimeUs, endTimeUs);


//        混音


        File adjustedPcm = new File(context.getFilesDir(), "混合后的" + ".pcm");
        mixPcm(videoPcmFile.getAbsolutePath(), aacPcmFile.getAbsolutePath(),
                adjustedPcm.getAbsolutePath()
                , videoVolume, aacVolume);

        File wavFile = new File(context.getFilesDir(), adjustedPcm.getName()
                + ".wav");
        new PcmToWavUtil(44100, AudioFormat.CHANNEL_IN_STEREO,
                2, AudioFormat.ENCODING_PCM_16BIT).pcmToWav(adjustedPcm.getAbsolutePath()
                , wavFile.getAbsolutePath());
        mixVideoAndMusic(videoInput, output, startTimeUs, endTimeUs, wavFile);
    }

    @SuppressLint("WrongConstant")
    private static void mixVideoAndMusic(String videoInput, String output, Integer startTimeUs, Integer endTimeUs, File wavFile) throws IOException {
//        视频容器  输出
        MediaMuxer mediaMuxer = new MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

//        读取视频的工具类
        MediaExtractor mediaExtractor = new MediaExtractor();
        mediaExtractor.setDataSource(videoInput);
//        N个轨道  可以
        //            拿到视频轨道的索引
        int videoIndex = selectTrack(mediaExtractor, false);
        int audioIndex = selectTrack(mediaExtractor, true);


// 轨道    添加轨道的时候必须是先添加视频轨道再添加音频轨道，编码的时候是先写音频再写视频，可以反过来编码吗？？？？多线程同时可行？？？
//        pcm  mp4
//        添加轨道  视频  索引     音频索引
        MediaFormat videoFormat = mediaExtractor.getTrackFormat(videoIndex);
//        新的视频  的视频轨
        mediaMuxer.addTrack(videoFormat);

//        mediaMuxer 拥有添加视频流的能力
        MediaFormat audioFormat = mediaExtractor.getTrackFormat(audioIndex);
        int audioBitrate = audioFormat.getInteger(MediaFormat.KEY_BIT_RATE);
        audioFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
//        添加的轨道  索引
        int muxerAudioIndex = mediaMuxer.addTrack(audioFormat);
//开始输出视频任务  2min  50M  *30=1.5G音频
        mediaMuxer.start();
//        音频要编码  wav 无损  大的，   视频   要1 不要2
//        视频 wav  --》视频 建议编码
//        解码 MediaFormat 1
//        编码 MediaFormat 2  音乐   变

        //音频的wav  音频文件   一个轨道  原始音频数据的 MediaFormat
        MediaExtractor pcmExtrator = new MediaExtractor();
        pcmExtrator.setDataSource(wavFile.getAbsolutePath());
        int audioTrack = selectTrack(pcmExtrator, true);
        pcmExtrator.selectTrack(audioTrack);
        MediaFormat pcmTrackFormat = pcmExtrator.getTrackFormat(audioTrack);

        //最大一帧的 大小
        int maxBufferSize = 0;
        if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            maxBufferSize = pcmTrackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        } else {
            maxBufferSize = 100 * 1000;
        }


        MediaFormat encodeFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                44100, 2);//参数对应-> mime type、采样率、声道数
        encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, audioBitrate);//比特率

        //            音质等级
        encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        //            解码  那段
        encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxBufferSize);


        MediaCodec encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        encoder.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//        开始编码
        encoder.start();
//         自己读取   从 MediaExtractor
        ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
//是否编码完成
        boolean encodeDone = false;
        while (!encodeDone) {
            int inputBufferIndex = encoder.dequeueInputBuffer(10000);
            if (inputBufferIndex >= 0) {
//                返回值 是 时间戳   <0文件读到了末尾
                long sampleTime = pcmExtrator.getSampleTime();
                if (sampleTime < 0) {
                    encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    int flags = pcmExtrator.getSampleFlags();
                    int size = pcmExtrator.readSampleData(buffer, 0);
                    ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferIndex);
                    inputBuffer.clear();
                    inputBuffer.put(buffer);
                    inputBuffer.position(0);
//                    通知编码
                    encoder.queueInputBuffer(inputBufferIndex, 0, size, sampleTime, flags);
//                    放弃内存，  一定要写  不写不能督导新的数据
                    pcmExtrator.advance();
                }
            }
//             输出的容器的索引 1
            int outIndex = encoder.dequeueOutputBuffer(info, TIMEOUT);

            while (outIndex >= 0) {
                if (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                    encodeDone = true;
                    break;
                }
//                通过索引 得到 编码好的数据在哪个容器
                ByteBuffer encodeOutputBuffer = encoder.getOutputBuffer(outIndex);
//数据写进去了
                mediaMuxer.writeSampleData(muxerAudioIndex, encodeOutputBuffer, info);
//                清空容器数据  方便下次读
                encodeOutputBuffer.clear();
//                把编码器的数据释放 ，方便dsp 下一帧存
                encoder.releaseOutputBuffer(outIndex, false);
                outIndex = encoder.dequeueOutputBuffer(info, TIMEOUT);
            }
        }


//       接下来要在视频轨道上操作，切换轨道
        if (audioTrack >= 0) {
            mediaExtractor.unselectTrack(audioTrack);
        }
//视频
        mediaExtractor.selectTrack(videoIndex);
//        seek到 startTimeUs 时间戳的  前一个I帧
        mediaExtractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
//视频 最大帧 最大的那一个帧   大小
        maxBufferSize = videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        buffer = ByteBuffer.allocateDirect(maxBufferSize);
// 加水印   1   先解码  --后编码  原始数据搞事情        2  不需要
        while (true) {

            long sampleTimeUs = mediaExtractor.getSampleTime();
            if (sampleTimeUs == -1) {
                break;
            }
            if (sampleTimeUs < startTimeUs) {
                mediaExtractor.advance();
                continue;
            }
            if (endTimeUs != null && sampleTimeUs > endTimeUs) {
                break;
            }
            info.presentationTimeUs = sampleTimeUs - startTimeUs + 600;
            info.flags = mediaExtractor.getSampleFlags();
            info.size = mediaExtractor.readSampleData(buffer, 0);
            if (info.size < 0) {
                break;
            }
//            写入视频数据
            mediaMuxer.writeSampleData(videoIndex, buffer, info);
//            advance
            mediaExtractor.advance();
        }

        pcmExtrator.release();
        mediaExtractor.release();
        encoder.stop();
        encoder.release();
        mediaMuxer.release();
    }

    /**
     * 一个字节代表一个采样点，采样点字节与java字节不太一样，java是大字节，采样点是小字节
     *
     * @param pcm1Path
     * @param pcm2Path
     * @param toPath
     * @param vol1
     * @param vol2
     * @throws IOException
     */
    public static void mixPcm(String pcm1Path, String pcm2Path, String toPath
            , int vol1, int vol2) throws IOException {

        float volume1 = vol1 / 100f * 1;
        float volume2 = vol2 / 100f * 1;
//待混音的两条数据流 还原   傅里叶  复杂
        FileInputStream is1 = new FileInputStream(pcm1Path);
        FileInputStream is2 = new FileInputStream(pcm2Path);
        boolean end1 = false, end2 = false;
//        输出的数据流
        FileOutputStream fileOutputStream = new FileOutputStream(toPath);
        byte[] buffer1 = new byte[2048];
        byte[] buffer2 = new byte[2048];
        byte[] buffer3 = new byte[2048];
        short temp2, temp1;
        while (!end1 || !end2) {

            if (!end2) {
                end2 = (is2.read(buffer2) == -1);
            }
            if (!end1) {
                end1 = (is1.read(buffer1) == -1);
            }
            int voice = 0;
//2个字节
            for (int i = 0; i < buffer2.length; i += 2) {
//前 低字节  1  后面低字节 2  声量值
//                32767         -32768
                temp1 = (short) ((buffer1[i] & 0xff) | (buffer1[i + 1] & 0xff) << 8);
                temp2 = (short) ((buffer2[i] & 0xff) | (buffer2[i + 1] & 0xff) << 8);

                voice = (int) (temp1 * volume1 + temp2 * volume2);
                if (voice > 32767) {
                    voice = 32767;
                } else if (voice < -32768) {
                    voice = -32768;
                }
//
                buffer3[i] = (byte) (voice & 0xFF);
                buffer3[i + 1] = (byte) ((voice >>> 8) & 0xFF);
            }
            fileOutputStream.write(buffer3);
        }
        is1.close();
        is2.close();
        fileOutputStream.close();
    }

    @SuppressLint("WrongConstant")
    public static void decodeToPCM(String musicPath,
                                   String outPath, int startTime, int endTime) throws Exception {

        if (endTime < startTime) {
            return;
        }
        MediaExtractor mediaExtractor = new MediaExtractor();
//        设值路径
        mediaExtractor.setDataSource(musicPath);
//        音频索引
        int audioTrack = selectTrack(mediaExtractor);
//       剪辑
        //选择轨道
        mediaExtractor.selectTrack(audioTrack);
//        耗费内存 和    cpu
//        seek   UI优化     缓存优化  加载视频       200ms   一帧    缓存图片
//         会长多 肯定 剪影  500  800M
        //SEEK_TO_CLOSEST_SYNC  最近I帧
        //SEEK_TO_NEXT_SYNC 下一个I帧
        //SEEK_TO_PREVIOUS_SYNC 上一个I帧   音频会seek到对应I帧的那一个点，seek以视频为主。seek到一个时间点，实际上是从0开始解码到那个点比较好内存
        mediaExtractor.seekTo(startTime, MediaExtractor.SEEK_TO_NEXT_SYNC);
        MediaFormat audioFormat = mediaExtractor.getTrackFormat(audioTrack);
        MediaCodec mediaCodec = MediaCodec.createDecoderByType(audioFormat.getString((MediaFormat.KEY_MIME)));
        mediaCodec.configure(audioFormat, null, null, 0);
        mediaCodec.start();
        int maxBufferSize = 100 * 1000;
        if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            maxBufferSize = audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        } else {
            maxBufferSize = 100 * 1000;
        }
        File pcmFile = new File(outPath);
        FileChannel writeChannel = new FileOutputStream(pcmFile).getChannel();
//        10M   造成内存浪费     10k   异常
        ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {

            int inIndex = mediaCodec.dequeueInputBuffer(1000);

            if (inIndex >= 0) {
//                获取到       视频容器  里面读取的当前时间戳
                long sampleTimeUs = mediaExtractor.getSampleTime();
                if (sampleTimeUs == -1) {
                    break;
                } else if (sampleTimeUs < startTime) {
//                    丢弃的意思
                    mediaExtractor.advance();
                } else if (sampleTimeUs > endTime) {
                    break;
                }
//                mediaExtractor
                info.size = mediaExtractor.readSampleData(buffer, 0);
                info.presentationTimeUs = sampleTimeUs;
                info.flags = mediaExtractor.getSampleFlags();
//                压缩1   原始数据 2

                byte[] content = new byte[buffer.remaining()];
                buffer.get(content);
//                压缩1   未压缩 原始数据2
//                FileUtils.INSTANCE.writeContent(content,"");
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inIndex);
                inputBuffer.put(content);
                mediaCodec.queueInputBuffer(inIndex, 0, info.size, info.presentationTimeUs, info.flags);
//                释放上一帧的压缩数据
                mediaExtractor.advance();
            }

            int outIndex = -1;
            outIndex = mediaCodec.dequeueOutputBuffer(info, 1_000);
            if (outIndex >= 0) {
                ByteBuffer decodeOutputBuffer = mediaCodec.getOutputBuffer(outIndex);
//数据 音频数据     压缩1   原始数据2
                writeChannel.write(decodeOutputBuffer);
                mediaCodec.releaseOutputBuffer(outIndex, false);
            }
        }

        writeChannel.close();
        mediaExtractor.release();
        mediaCodec.stop();
        mediaCodec.release();

    }

    //    寻找音频轨
    public static int selectTrack(MediaExtractor extractor) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
//            轨道配置信息， 码流读取 ，sps  pps 解析
            MediaFormat format = extractor.getTrackFormat(i);
//轨道类型
            //音频 {encoder-delay=576, sample-rate=44100, track-id=1, durationUs=320496326, mime=audio/mpeg, channel-count=2, bitrate=128000, encoder-padding=696}
            String mime = format.getString(MediaFormat.KEY_MIME);
            Log.i(TAG, "MediaFormat---" + format.toString());
            if (mime.startsWith("audio")) {
                return i;
            }
        }
        return -1;
    }

    //    寻找音频轨
    public static int selectTrack(MediaExtractor extractor, boolean audio) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
//            轨道配置信息， 码流读取 ，sps  pps 解析
            MediaFormat format = extractor.getTrackFormat(i);
//轨道类型
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (audio) {
                if (mime.startsWith("audio")) {
                    return i;
                }
            } else {
                if (mime.startsWith("video")) {
                    return i;
                }
            }

        }
        return -1;
    }
}