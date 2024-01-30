package com.mymedia.opengl;

import static android.opengl.GLES20.GL_TEXTURE_2D;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import com.mymedia.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 渲染 bitmap，与渲染视频最大的不同就是，opengl用的纹理是从camerax传递过来，渲染Bitmap的纹理是自己创建的，由于是静态图也没必要一定在onDrawFrame渲染，Render创建完成即可渲染
 * 由于没有摄像机的返回的矩阵 以及 纹理坐标系 和 Android屏幕坐标系是沿X轴对折，所以渲染bitmap的纹理坐标系 也要对折，仔细观察ScreeFilter和ImageFilter的纹理坐标系就能发现端倪
 */


public class ImageFilter {

    protected int mProgId;
    //世界坐标系
    static final float COORD1[] = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f
    };
    //Android 布局坐标系
    static final float TEXTURE_COORD1[] = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f,
    };

//    static final float TEXTURE_COORD1[] = {
//            0.0f, 0.0f,
//            1.0f, 0.0f,
//            0.0f, 1.0f,
//            1.0f, 1.0f,
//    };
//    视频

    protected int mInputTexture;
    private String mVertexShader;
    private String mFragmentShader;

    private FloatBuffer mPositionBuffer;
    private FloatBuffer mCoordBuffer;
    //CPU  GPU 才有意义
    protected int mPosition;
    //顶点程序
    protected int vCoord;

    public ImageFilter(Context context) {
        this(OpenGLUtils.readRawTextFile(context, R.raw.base_vert), OpenGLUtils.readRawTextFile(context, R.raw.base_frag));

    }

    public ImageFilter(String vertexShader, String fragmentShader) {
        mVertexShader = vertexShader;
        mFragmentShader = fragmentShader;
    }


    public void initShader() {
//加载程序    mProgId    总程序 在哪里
        mProgId = OpenGLUtils.loadProgram(mVertexShader, mFragmentShader);
        mPosition = GLES20.glGetAttribLocation(mProgId, "vPosition");
        vCoord = GLES20.glGetAttribLocation(mProgId, "vCoord");
        mInputTexture = GLES20.glGetUniformLocation(mProgId, "inputImageTexture");

//         gpu的变量   1  定位变量在GPU的位置
//         2  FLoteBuffer      COORD1  传给  FLoteBuffer
//         3   把FLoteBuffer 给到GPU的变量   1  初始化1     ondrawFrame2
    }

    //顶点程序
    public void loadVertex() {
        float[] coord = COORD1;
        float[] texture_coord = TEXTURE_COORD1;

        mPositionBuffer = ByteBuffer.allocateDirect(coord.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mPositionBuffer.put(coord).position(0);

        mCoordBuffer = ByteBuffer.allocateDirect(texture_coord.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mCoordBuffer.put(texture_coord).position(0);
    }

    public int init(Bitmap bitmap) {
        initShader();
        loadVertex();
        return initTexture(bitmap);
    }

    private int initTexture(Bitmap bitmap) {
        int[] textures = new int[1];
        //创建纹理
        GLES20.glGenTextures(1, textures, 0);//创建1个纹理，n等于几就是创建几个纹理
        GLES20.glBindTexture(GL_TEXTURE_2D, textures[0]);//按理说应该激活图层，再绑定纹理，把这句注释后结果不显示图片
        //放大和缩小时设置纹理处理马赛克的方式，模糊和不处理两种方式，GL_NEAREST 不处理，GL_LINEAR 处理。
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);//放大处理
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);//缩小不处理

        //当图片填不满，竖屏方向和竖直方向设置填充方式,有四种具体效果看以下
        //E:\BaiduNetdiskDownload\（5）音视频\VIP34-2022.7.11-Opengl基础03-Opengl加载图片以及Opengl重构\资料\Opengl纹理02.md
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        //传入bitmap
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        return textures[0];

    }

    public int drawFrame(int glTextureId) {
        GLES20.glUseProgram(mProgId);

        //传入顶点坐标
        mPositionBuffer.position(0);
        GLES20.glVertexAttribPointer(mPosition, 2, GLES20.GL_FLOAT, false, 0, mPositionBuffer);
        GLES20.glEnableVertexAttribArray(mPosition);

        //传入纹理坐标
        mCoordBuffer.position(0);
        GLES20.glVertexAttribPointer(vCoord, 2, GLES20.GL_FLOAT, false, 0, mCoordBuffer);
        GLES20.glEnableVertexAttribArray(vCoord);

        GLES20.glActiveTexture(0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, glTextureId);
        GLES20.glUniform1i(mInputTexture, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        // 把第0图层  解绑的意思
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return glTextureId;
    }


}
