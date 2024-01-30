package com.mymedia.opengl;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGLContext;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MediaRecorder {
    private Handler mHandler;
    private final Context mContext;
    private final String mPath;
    private final int mWidth;
    private final int mHeight;
    private EGLBase mEglBase;
    private Surface mInputSurface;
    private final EGLContext mEglContext;

    private MediaCodec mMediaCodec;
    private boolean isStart;
//    封装格式  zip   rar  7zip    编码格式
    private MediaMuxer mMediaMuxer;

    private int dataIndex;

    private float mSpeed;//5
    public MediaRecorder(Context context, String path, int width, int height, EGLContext eglContext) {
        mContext = context.getApplicationContext();
        mPath = path;
        mWidth = width;
        mHeight = height;
        mEglContext = eglContext;
    }

    //start  主线程
    public void start(float speed) throws IOException {
//        编码器的配置
        mSpeed = speed;

        /**
         * 配置MediaCodec 编码器
         */
        //视频格式
        // 类型（avc高级编码 h264） 编码出的宽、高
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, mWidth, mHeight);
        //参数配置
        // 1500kbs码率
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1500_000);
        //帧率
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 20);
        //关键帧间隔
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 20);
        //颜色格式（RGB\YUV）
        //从surface当中回去
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        //编码器
        mMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        //将参数配置给编码器
        mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        mInputSurface = mMediaCodec.createInputSurface();

        mMediaMuxer = new MediaMuxer(mPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        HandlerThread handlerThread = new HandlerThread("VideoCodec");
        handlerThread.start();
        Looper looper = handlerThread.getLooper();

        mHandler = new Handler(looper);

        mHandler.post(new Runnable() {
            @Override
            public void run() {
//opengl环境  得到   当前线程上下文
                mEglBase = new EGLBase(mContext, mWidth, mHeight, mInputSurface, mEglContext);

//                EGL14.eglBindAPI(1);
//                api  opengl 愉快的调用了
                mMediaCodec.start();
                isStart = true;

//主 1 子线程2   调用opengl  --》  数据           编码
            }
        });


    }
//摄像头发布的每一帧 会调用一次 FBO 的纹理ID
    public void encodeFrame(final int textureId, final long timestamp) {
        if (!isStart) {
            return;
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mEglBase.draw(textureId, timestamp);
                //从编码器的输出缓冲区获取编码后的数据就ok了
                getCodec(false);
            }
        });

//    把数据
//        int index = mMediaCodec.dequeueInputBuffer(10000);
//        if (index >= 0) {
//            ByteBuffer byteBuffer = mMediaCodec.getInputBuffer(index);
//            byteBuffer.put();
//        }
//视频编码写完    再去官opengl  的数据
        //从编码器的输出缓冲区获取编码后的数据就ok了

    }

//    人为 控制
    private void getCodec(boolean endOfStream) {
//            从surface 取到数据  并且输出到视频文件中
        if (endOfStream) {
//            编码出一个流结束符
            mMediaCodec.signalEndOfInputStream();
        }
        //输出缓冲区
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//if()     编码
        while (true) {
//<0 >=0  索引   1    -1   2
            int index = mMediaCodec.dequeueOutputBuffer(bufferInfo, 10_000);
            if (index ==  MediaCodec.INFO_TRY_AGAIN_LATER) {
                // 如果是停止 我继续循环
                // 继续循环 就表示不会接收到新的等待编码的图像
                // 相当于保证mediacodec中所有的待编码的数据都编码完成了，不断地重试 取出编码器中的编码好的数据
                // 标记不是停止 ，我们退出 ，下一轮接收到更多数据再来取输出编码后的数据

//                咱们的数据没有编码好
                if (!endOfStream) {
                    break;
                }



            } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
//直播    一路    直播了     添加了 直播  而

                //开始编码 就会调用一次
                MediaFormat outputFormat = mMediaCodec.getOutputFormat();
                //配置封装器
                // 增加一路指定格式的媒体流 视频
                dataIndex =  mMediaMuxer.addTrack(outputFormat);
                mMediaMuxer.start();
//编码  和 分装   H264  封装

            } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
//             少编码   一帧   大1  不大2


            } else {
//                index》=0
//成功 取出一个有效的输出
                ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(index);
                //如果获取的ByteBuffer 是配置信息 ,不需要写出到mp4
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0;
                }
                if (bufferInfo.size != 0) {
                    //写出 outputBuffer数据   ----》  封装格式中 mp4  极快
                    bufferInfo.presentationTimeUs = (long) (bufferInfo.presentationTimeUs / mSpeed);

                    //写到mp4
                    //根据偏移定位  bufferInfo.offset  ==0
                    outputBuffer.position(bufferInfo.offset);
                    //ByteBuffer 可读写总长度
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                    mMediaMuxer.writeSampleData(dataIndex, outputBuffer, bufferInfo);

                }

                //输出缓冲区 我们就使用完了，可以回收了，让mediacodec继续使用
                mMediaCodec.releaseOutputBuffer(index, false);


                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }

            }
        }

    }
//点击停止按钮的时候
    public void stop() {
        isStart = false;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                getCodec(true);
                mEglBase.release();
                mMediaCodec.release();
                mMediaCodec = null;
                mMediaMuxer.stop();
                mMediaMuxer.release();
                mMediaMuxer = null;
                mEglBase.release();
                mEglBase = null;
                mInputSurface = null;


            }
        });

    }

}
