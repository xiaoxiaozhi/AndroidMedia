package com.mymedia.opengl;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import com.mymedia.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 1.了解opengl坐标系
 * 世界坐标系，就是数学上笛卡尔坐标系，按照 左下、右下、左上、右上的顺序,取四个点(-1,-1  1,-1  -1,1   1,1)建立数组，才能得到正确的视图(为什么是4个点不知道，为什么按照这个顺序排列不知道)
 * 1.2 纹理坐标系，也是取四个点，按照 左下、右下、左上、右上的顺序,取四个点(0,0  1,0  0,1   1,1)建立数组这几个点一一对应就能正确渲染, 跟Android坐标无关
 * 2.准备工作
 * 2.1创建顶点着色器 2.2创建片元着色器 2.3创建渲染程序并把 这三步连接起来
 * 3.绘制
 * 引用着色器中的变量并赋值。再gpu中申请一个图层并与纹理绑定，开始绘制
 *     vec4 rgba = texture2D(vTexture,vec2(aCoord.x,aCoord.y));按我的理解，vTexture是gpu中的一个图层，先前图层和纹理绑定，可以理解为一幅画，texture2D作用是获取这幅画的每一个像素的颜色，
 *     gl_FragColor=vec4(rgba.r,rgba.g,rgba.b,rgba.a); 把颜色复制给gl_FragColor，然后输出给屏幕，只有给gl_FragColor赋值绘制才算完成
 */
public class CameraFilter {
    int program;

    float[] VERTEX = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f
    };
    float[] TEXTURE = {
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
    };
    //gpu    地址
    private int vPosition;
    private int vCoord;

    private int vTexture;
    private int vMatrix;
    FloatBuffer vertexBuffer;
    FloatBuffer textureBuffer; // 纹理坐标

    public CameraFilter(Context context) {
        //创建顶点着色器
        String vertexSharder = readRawTextFile(context, R.raw.camera_vert);
        int vShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);//创建着色器(顶点着色器或者片元着色器)
        GLES20.glShaderSource(vShader, vertexSharder);//加载着色器程序
        GLES20.glCompileShader(vShader);//编译着色器
        int[] status = new int[1];
        GLES20.glGetShaderiv(vShader, GLES20.GL_COMPILE_STATUS, status, 0);//获取编译结果
        if (status[0] != GLES20.GL_TRUE) {
            throw new IllegalStateException("load vertex shader:" + GLES20.glGetShaderInfoLog
                    (vShader));
        }
        //创建片元着色器
        String fragSharder = readRawTextFile(context, R.raw.camera_frag);
        int fShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fShader, fragSharder);
        GLES20.glCompileShader(fShader);
        status = new int[1];
        GLES20.glGetShaderiv(fShader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] != GLES20.GL_TRUE) {
            throw new IllegalStateException("load fragment shader:" + GLES20.glGetShaderInfoLog
                    (fShader));
        }
        //创建渲染程序
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vShader);//加载顶点着色器
        GLES20.glAttachShader(program, fShader);//加载片元着色器

        //链接着色器程序，准备工作完成
        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        Log.d("mmm", "链接程序" + GLES20.glGetProgramInfoLog(program));
        if (linkStatus[0] != GLES20.GL_TRUE) {
            GLES20.glDeleteProgram(program);
            throw new IllegalStateException("Link Program error:" + GLES20.glGetProgramInfoLog(program));
        }
        //因为已经编译到总程序，在这里释放掉顶点和片元
        GLES20.glDeleteShader(vShader);
        GLES20.glDeleteShader(fShader);

        vertexBuffer = ByteBuffer.allocateDirect(4 * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.clear();
        vertexBuffer.put(VERTEX);
        textureBuffer = ByteBuffer.allocateDirect(4 * 4 * 2).order(ByteOrder.nativeOrder()).asFloatBuffer();
        textureBuffer.clear();
        textureBuffer.put(TEXTURE);
    }

    //    渲染一次  N次
    public void onDraw(int mWidth, int mHeight, float[] mtx, int textures) {
        GLES20.glViewport(0, 0, mWidth, mHeight);
        GLES20.glUseProgram(program);

        vertexBuffer.position(0);
        textureBuffer.position(0);

        //获取着色器中变量
        vPosition = GLES20.glGetAttribLocation(program, "vPosition");
        vCoord = GLES20.glGetAttribLocation(program, "vCoord");
        vTexture = GLES20.glGetUniformLocation(program, "vTexture");
        vMatrix = GLES20.glGetUniformLocation(program, "vMatrix");

        //给顶点着色器中的顶点坐标赋值
        GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(vPosition);
        //给片元着色器中的坐标赋值
        GLES20.glVertexAttribPointer(vCoord, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);
        GLES20.glEnableVertexAttribArray(vCoord);

        GLES20.glUniformMatrix4fv(vMatrix, 1, false, mtx, 0);//给矩阵赋值

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); //申请一个图层
        GLES20.glBindTexture(GLES20.GL_TEXTURE0, textures); //图层和纹理绑定
        GLES20.glUniform1i(vTexture, 0);//给片元程序中的图层赋值，告诉他是几号图层

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);// 渲染0,4 是4个坐标的意思；0,3是3个坐标的意思

    }

    //    opengl  1  确定形状   栅格化    渲染     显示
    public String readRawTextFile(Context context, int rawId) {
        InputStream is = context.getResources().openRawResource(rawId);
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;
        StringBuilder sb = new StringBuilder();
        try {
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }


}
