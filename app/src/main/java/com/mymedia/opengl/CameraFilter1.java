package com.mymedia.opengl;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.mymedia.R;

/**
 * 第一层滤镜：camerax--->滤镜1--->滤镜2  (--->代表数据) 第一层滤镜通过离屏渲染技术，摄像头数据存储到FBO。具体操作如下：
 * 创建图层、创建纹理、创建FBO
 * 与直接渲染摄像头数据不一样，在这里没有给纹理坐标赋值，也没有 GLES20.glDrawArrays 绘制而是绑定了FBO。(是不是说把数据给FBO)
 */
public class CameraFilter1 extends AbstractFilter {

    private int[] mFrameBuffers;
    private int[] mFrameBufferTextures;
    private float[] matrix;

    public CameraFilter1(Context context) {
        super(context, R.raw.camera_vert, R.raw.camera_frag);
        textureBuffer.clear();
    }
//
//    @Override
//    public void release() {
//        super.release();
//        destroyFrameBuffers();
//    }

    public void destroyFrameBuffers() {
        //删除fbo的纹理
        if (mFrameBufferTextures != null) {
            GLES20.glDeleteTextures(1, mFrameBufferTextures, 0);
            mFrameBufferTextures = null;
        }
        //删除fbo
        if (mFrameBuffers != null) {
            GLES20.glDeleteFramebuffers(1, mFrameBuffers, 0);
            mFrameBuffers = null;
        }
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
        if (mFrameBuffers != null) {
            destroyFrameBuffers();
        }
        //1、创建fbo （离屏屏幕）
        mFrameBuffers = new int[1];
        // 创建几个fbo 2、保存fbo id的数据 3、从这个数组的第几个开始保存
        GLES20.glGenFramebuffers(mFrameBuffers.length, mFrameBuffers, 0);
        //2、创建属于fbo的纹理
        mFrameBufferTextures = new int[1]; //用来记录纹理id
        OpenGLUtils.glGenTextures(mFrameBufferTextures);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFrameBufferTextures[0]);//设置纹理为2F图形
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mWidth, mHeight,
                0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);//设置纹理长宽高等属性
        // 绑定纹理与FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, mFrameBufferTextures[0], 0);
        //调用完了要解绑，否则无法二次调用，(老实说这段基于过程的程序理解不了)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

    }

    @Override
    public int onDraw(int textureId) {
        //设置显示窗口
        GLES20.glViewport(0, 0, mWidth, mHeight);
        //默认情况下 GPU会把纹理会直到GlSurfaceView，调用这句就会把纹理会直到FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[0]);
        //使用着色器
        GLES20.glUseProgram(program);
        //传递坐标
        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(vPosition);
        GLES20.glUniformMatrix4fv(vMatrix, 1, false, matrix, 0);// 没有传递纹理坐标，顶点程序里面矩阵和谁相乘呢？？？？


        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        //因为这一层是摄像头后的第一层滤镜，所以需要使用扩展的  GL_TEXTURE_EXTERNAL_OES
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glUniform1i(vTexture, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        //返回fbo的纹理id
        return mFrameBufferTextures[0];
    }

    public void setMatrix(float[] matrix) {
        this.matrix = matrix;
    }
}
