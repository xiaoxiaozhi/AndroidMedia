# AndroidMedia

Android音视频开发
[yuv420 421 444 等这些数字的由来](https://blog.csdn.net/Xoxo_x/article/details/80308556)

#### 音视频通话

从零实现手写音视频通话(音频通话版本) 这节课讲解了 录音(AudioRecord)和播放(AudioTrack)

#### 直播推流

要实现直播秒开，最主要手段是在服务端缓存一个gop，客户端接入时候先拉缓存然后再接收推流；每一两秒一个I帧这样会导致码流特别大，为了降低码流，就要降低帧率，直播的帧率在15帧左右
RTMP协议：推流端推送h264到服务器，如果还要让服务器解码，负担太沉重，所以约定好了一种协议就算不解码也知道是什么帧  
![rtmp数据包解析](/rtmp.png) 0X17 0X01 代表I帧 、0X17 0X00代表 SPS和PPS 、X027 0X01 代表P帧和B帧 (后面统一跟0X00 0X00 0X00)。4字节代表长度，整形的高8位在最左边
手写哔哩哔哩硬编码推流40分钟开始  
可用的rtmp协议包 [官网](http://rtmpdump.mplayerhq.hu/)   
rtmp包在cmake中的配置比较麻烦，直接使用这节课的代码手写哔哩哔哩硬编码推流

#### 为什么用yuv不用RGB(硬编,包含答疑课)

native代码出现问题会直接杀死进程，由另一个进程打印错误日志，这时候要log切换到所有进程查看backtrace关键字
![查找backtrace关键字下面是报错的库](/backtrack.png)
扎到这个工具D:
\AndroidSdk\ndk-bundle\toolchains\arm-linux-androideabi-4.9\prebuilt\windows-x86_64\bin\arm-linux-androideabi-addr2line.exe
添加到系统变量
![按照这个图的操作就可以知道第几行](/error.png)
在这个文件夹下面找到so库app\build\intermediates\merged_native_libs\debug\out\lib\根据cpu平台找
在native代码中解析好rtmp头，发送I帧的时候仍然要发送Sps和PPS

#### 硬编推流03 音频推流

```C
//RTMP rtmp.png所述是 packet->m_body内容 还有以下配置，发送的时候要填好
typedef struct RTMPPacket
  {
    uint8_t m_headerType;
    uint8_t m_packetType; 帧类型，音频、视频、字幕.....
    uint8_t m_hasAbsTimestamp;	/* timestamp absolute or relative? */ 指直播开始了多久，该例是在java代码中计算开始时间减去编码时间得到
    int m_nChannel; 发送视频或音频时的标识符，之后到按照这个值发即可，感觉和帧类型重复了
    uint32_t m_nTimeStamp;	/* timestamp */传0 由系统赋值，就是推流端的时间，
    int32_t m_nInfoField2;	/* last 4 bytes in a long header */
    uint32_t m_nBodySize;
    uint32_t m_nBytesRead;
    RTMPChunk *m_chunk;
    char *m_body;
  } RTMPPacket;
``` 

rtmp和rtsp:前者不安全，但是支持多人，后者安全但是不适合太多人
音频编码使用audio/mp4a-latm ，它是aac的一种，aac有版权
在推送音频包之前先推送 {0x12, 0x08};这两个字节
![RTMP音频包头](/rtmp_audio.png)音频包有两种 0XAF 0x00 (0x12 0x08推送音频之前要先推这个)   和 0xAF 0X01 编码后的音频数据
这节课代码录音有问题mediaCodec.getInputBuffer(index).put(buffer, 0, len)
溢出，这是因为音频编码等级选低了MediaFormat.KEY_AAC_PROFILE，设置为AACObjectMain即可，该问题在下一节课解决
AudioFormat.CHANNEL_IN_MONO 单通道录音 CHANNEL_IN_STEREO双通道录音
[音频包头详解](https://www.jianshu.com/p/952295c4fdfc) 0XAF=1010 1111 高四位代表格式 再两位代表采样率 再一位采样位数 再一位音频类型

#### 直播推流(软编)03X264集成与Camera推流

解决了上一节课音频inputBuffer溢出的问题，软件码有两种途径：ffmpeg 和X264 前者体积大，功能多；后者体积小，只能视频编码
少量cpp可以放在项目中编译，大量文件会造成项目编译缓慢，这时候就要交叉编译后导入动态库实现。在Linux中用ndk才能实现交叉编译

#### 软编推流02-x264编码机制详解

动态库与静态库区别：动态库没有被编译进目标代码中；程序执行到相关函数时才会加载动态库，所以调用速度慢；动态库体积要远小于静态库。静态库正好相反  
无论是动态库还是静态库还需要一个头文件，才能引入android项目，
include_directories(${CMAKE_SOURCE_DIR}/x264/${ANDROID_ABI}/include)
//引入头文件,${CMAKE_SOURCE_DIR}是cpp文件夹的路径，${ANDROID_ABI}返回armeabi-v7a，因为本例只编译armeabi-v7a
如果想变异其它架构，只需创建X86 或者armeabi-v8a文件夹然后把对应库放进去即可
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -L${CMAKE_SOURCE_DIR}/x264/${ANDROID_ABI}/lib") 把该文件夹下的so包添加到系统环境目录(涂老师这样说)
target_link_libraries(x264 ) 添加包,在cpp导入了头文件，在这里还要添加一下才能正常运行
add_library() 好像是把自己写的cpp添加成库
经过这三部该项目引用了X264静态库，在build.gradle中设置好只编译armeabi-v7a平台，file(GLOB cpp_source  *.cpp)如果引用不到x264文件把所有cpp文件添加到全局变量
aux_source_directory(./soundtouch/SoundTouch SOURCE1) 把这个路径下所有的cpp文件都添加到变量SOURCE1
aux_source_directory(. SOURCE) 把cpp文件夹下所有的cpp文件都添加到变量SOURCE
方便执行add_library(${SOURCE} ${SOURCE1})

#### 直播推流(软编)03-CameraX详解与摄像头推流

native 和java处在不同线程，他们是无法互相访问变量的，需要通过在JNI_Onload(JavaVM *vm ...)回调函数中获取到JVM对象
本例用X264编码后用RTMP推送
TODO 推流时候计算时间是在哪节课？？？

#### 直播推流(软编)04-音频编码与推流(实际是opengl 第一节课)

确定形状、像素化(栅格化)、着色、根据颜色进行显示。程序员操作第一和第三步，前者叫顶点程序，后者叫片元程序
继承GLSurfaceView ，设置OpenGL版本，一般是2。一下两行代码在自定义GLSurfaceView中
Preview.OnPreviewOutputUpdateListener{ onUpdated{ output.getSurfaceTexture().setOnFrameAvailableListener(this){
requestRender()手动渲染 } }}  
public void onDrawFrame(GL10 gl10) { mCameraTexure.updateTexImage(); }
对以上两行代码的解释， cameraX 经过两层回调，每次有新的数据就执行GLSurfaceView.requestRender()手动渲染，GLSurfaceView再次回调GLSurfaceView.onDrawFrame(),
世界坐标系：以屏幕中心为原点(0,0)，当你面对屏幕时，右边是X正轴，上方是Y轴正轴，左上(-1,1)右上(1,1)左下(-1,-1)右下(1,-1)只考虑二维
纹理坐标系：即android屏幕的坐标系，左上角是0,0 右上角是1,0 所以输出坐标，要与世界坐标做个对应。 图片想要实现旋转换个坐标系即可，比用矩阵操作yuv快多了。
世界坐标系是概念上的，我们最终目的是要把世界坐标系转变成屏幕坐标系
下载插件GLSL Support,右键 new 发现可以创建 GLSL shader文件，res/raw/创建到这里 该文件运行在gpu。 要创建两个一个是顶点程序，一个是片元程序。

```
  float[] VERTEX = {
        -1.0f, -1.0f,// -1.0f, -1.0f,00f 如果是3个值代表立体坐标
        1.0f, -1.0f,
        -1.0f, 1.0f,
        1.0f, 1.0f 
    };//世界坐标系中两个三角形拼成一个矩形，这一步就是确定形状。1-3行是一个点；2到4行是一个点；3到5行是一个点。重复的行是公用点，如果不按照这个规则将会报错
```

在cpu中定义好形状程序传递给顶点程序
attribute vec4 vPosition;//定义gpu变量，vec4代表4顶点变量，vec1代表1顶点变量 vec2 vec3
内置变量 E:\BaiduNetdiskDownload\VIP32-2022.7.8-你离30k的Android高级工程师就差一个Opengl-David\资料\OpenGl学习\Opengl基础1.md

```C++
attribute  vec4 vPosition;//四个点世界坐标系
attribute vec4 vCoord;//纹理坐标系
//   顶点的坐标 传值给   片元程序
varying  vec2 aCoord;//varying创建具有传递功能的变量，片元程序也有一个一样的，这样我们把值传递给顶点程序之后，会通过这个值自动传递给片元程序
void main() {
//    opengl  形状确定
    gl_Position=vPosition;
    aCoord= vCoord.xy;
}
```

写好的顶点程序和片元程序要加载编译，具体情况查看AndroidMedia/ScreenFilter.java

#### opengl基础02，用opengl实现摄像头灰色滤镜

给坐标系创建容器，给gpu传数组不能用 byt[] 只能用ByteBuffer.allocateDirect()在底层申明了空间的ByteBuffer

```
vertexBuffer = ByteBuffer.allocateDirect(4 * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();//4个点，2个坐标组成，每个值4字节，必须用allocateDirect创建才可以
vertexBuffer.clear();
vertexBuffer.put(VERTEX);
```

GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
;//vPosition是gpu变量，2是每个坐标由两个值表示，vertexBuffer的值传递给vPosition
GLES20.glEnableVertexAttribArray(vPosition);启动变量
mCameraTexure.attachToGLContext(textures);新创建一个纹理，参数是纹理名称不能重复，数据就放在这个纹理里面，纹理在gpu中
GLES20.glActiveTexture(GLES20.GL_TEXTURE0);申请一个图层，总共有32个，图层类似与画布，涂老师在这里直接赋值0，但是GL_TEXTURE0值不是0，这样做运行没问题，但是逻辑上不同
GLES20.glBindTexture(GLES20.GL_TEXTURE0, textures);涂层和纹理绑定 这里讲的和Opengl基础05 opengl抖音录制滤镜视不一样，05需要使用FBO参数用的是
GL_TEXTURE_EXTERNAL_OES或GL_TEXTURE_2D 前者在使用摄像头时使用，后者是GPU内部纹理对象。到底用哪个呢？？？   
GLES20.glUniform1i(vTexture, 0);//给片元程序中的图层赋值，告诉他是几号图层
uniform samplerExternalOES vTexture; 片元程序设置一个变量接收纹理
vec4 rgba = texture2D(vTexture,vec2(x,aCoord.y));//texture2D 采样器，根据vec2(x,y)的坐标规则，vTexture是图层表示从vTexture中采样
gl_FragColor=rgba; 将图像输出
#extension GL_OES_EGL_image_external : require//使用samplerExternalOES 要先导入这个
precision lowp float;// 声明float的精度类型
GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);//渲染0,4 是4个坐标的意思；0,3是3个坐标的意思
以上创建的画面方向不对，这时候要传健一个翻转矩阵
float[] mtx = new float[16];
mCameraTexure.getTransformMatrix(mtx);//surfaceTexure已经提供了这个矩阵，传到对应的gpu变量
color = (rab.r+rgb.g+rgb.b)/3
gl_FragColor=vec4(color,color,color,rgb.a);这样就是灰色滤镜，原理是什么
分屏没听懂
该例实现了摄像头预览，以及方向偏转，分屏

#### Opengl基础03-Opengl抖音录制滤镜视频，重构opengl代码

int program = GLES20.glCreateProgram();//顶点程序和片元程序编译完，要在这里创建一个总程序
GLES20.glDeleteShader(vShader);//创建完总程序之后就可以释放之顶点和片元程序
GLES20.glDeleteShader(fShader);
int program = OpenGLUtils.loadProgram(mVertexShader, mFragmentShader)
;//参数是顶点程序和片元程序的字符串。返回的是程序id，是否与glCreateProgram功能重复？？？？
然后要创建一个纹理来渲染bitmap

```
        int[] textures = new int[1];//纹理ID，上一例是通过mCameraTexure.attachToGLContext(textures);创建纹理
        GLES20.glGenTextures(1, textures, 0);//        创建纹理
        GLES20.glBindTexture(GL_TEXTURE_2D, textures[0]);// 使用纹理 ；GLES20.glBindTexture(GL_TEXTURE_2D, 0); 传0就是解绑
        然后给创建的纹理设置参数，参考 E:\BaiduNetdiskDownload\VIP34-2022.7.11-Opengl基础03-Opengl加载图片以及Opengl重构\资料\Opengl纹理02.md
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);  激活图层，在这里涂老师直接传了个0，但是GL_TEXTURE0值不为0.所以传0对吗？？？？
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0); 把bitmap装载到纹理中，配置参数和装载bitmap都没有对纹理操作，这是应为opengl是一个面向过程的程序，glBindTexture之后所有操作都是对纹理的操作
```

uniform sampler2D inputImageTexture;//在片元程序中创建一个图层
该例重构了opengl代码，然后用opengl渲染了bitmap

#### Opengl基础04 EGL环境与答疑

opengl 传递的是坐标系，四个顶点。
解码的时候mediaformat没用，sps和pps会覆盖这些内容
比特率以固定帧率和分辨率录制的视频宏块越大越模糊，比特率越小。反之越大。
摄像头--->gpu(纹理)--->FBO(Frame Buffer object帧缓冲对象)--->虚拟屏幕--->虚拟屏幕自带的surface得到数据--->mediacodec--->推送   (--->指数据流)
、使用eglSwapBuffer从surface中得到数据
多个滤镜(纹理)可以叠加 后一个滤镜的内容是前一个滤镜的结果，例如第一个滤镜功能是翻转，第二个滤镜就不需要再反转
第一个滤镜的片元程序的纹理使用samplerExternalOES 第二个滤镜的片元程序的纹理使用 sampler2D 前者意思是从外部摄像头采样，后者从gpu内部采样
从FBO到得到数据 直接查看D:\androidSample\OpenglRecord\app\src\main\java\com\maniu\openglrecord\CameraFilter.java public void
setSize(int width, int height) 在这里面
FBO：当需要对纹理进行多次渲染采样时，而这些渲染采样是不需要展示给用户看的，可以用一个单独的缓冲对象（离屏渲染）来存储这几次渲染采样的结果，等处理完后才显示到窗口上。
GLSurfaceView
的所有方法都运行在名为GlThread的子线程中，该线程含有opengl运行的上下文所以opengl只有在这个线程中调用api才会运行，从FBO得到数据是一个耗时操作，如果放在GLThread会阻塞渲染，但是创建新线程opengl又无法运行
所以我们需要在新建的子线程中创建opengl上下文 D:\androidSample\OpenglRecord\app\src\main\java\com\maniu\openglrecord\EGLBase.java
EGL14.eglSwapBuffers(mEglDisplay, mEglSurface);得到数据
未完待续

#### Opengl基础05 opengl抖音录制滤镜视

创建opengl上下文步骤：1.创建egldisplay 2.创建egl上下文，从GLSurfaceView获取上下文以此创建新的上下文 3. 绑定surface
EGL14.eglCreateWindowSurface(mEglDisplay, mEglConfig, surface, attrib_list, 0);//surface由MediaCodec.createInputSurface()
;提供这里的surface作用是接收gpu数据，给mediacodec编码
mMediaCodec.signalEndOfInputStream(); 编码结尾要加一个流终止符
index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED 涂老师说，再添加一个摄像头时编码流会改变 index =
mMediaCodec.dequeueOutputBuffer，为什么会改变？？？一个mediacodec不应该对应一个摄像头吗
官网解释说输出格式改变，时候会调用此方法。 不晓得什么场景会用到这个标记
MediaMuxer只能录制 MUXER_OUTPUT_MPEG_4、MUXER_OUTPUT_WEBM、MUXER_OUTPUT_3GPP 这三种格式的视频
mMediaMuxer.writeSampleData(dataIndex, outputBuffer, bufferInfo);录制倍速 慢速实现方法，bufferInfo的时间戳 乘以或除以响应倍率
创建FBO位置直接查看D:\androidSample\OpenglRecord\app\src\main\java\com\maniu\openglrecord\CameraFilter.java public void setSize(
int width, int height) 在这里面
从GLThread传递FBO到新创建的线程，然后EglDisplay绘制到虚拟屏
CameraFilter1是错误代码，以CameraFilter为准
该例实现了 预览，倍速播放，从opengl录制视频 opengl--->mediacodec--->MediaMuxer 慢动作需要插针该例未实现

#### 抖音视频滤镜特效06 -美颜滤镜

#### 抖音视频滤镜特效07 -美颜直播 抖音灵魂出窍

#### 从零实现IJKPlayer万能播放器

没有讲交叉编译，直接使用打包好的ffmpeg动态库，展示了简单例子
UnsatisfiedLinkError XXX.so not found 报这种错误，直接在AS中打开apk 看看有没有这个动态库，通常是没有的
这时候在app/build.gradle 添加sourceSets 标签，指定so库路径

```groovy
android {
    sourceSets {
        main {
            jniLibs.srcDirs = ['src/main/cpp/libs']
        }
    }
}
```

avformat_network_init();// 初始化ffmpeg的网络模块，播放网络视频的能力
avformat_alloc_context();// 初始化总上下文
avFormatContext->nb_streams;// 流的个数，类似于java 的轨道数
遍历流个数
avcodec_alloc_context3//视频上下文
avcodec_find_decoder(AV_CODEC_ID_H264);//视频解码器
pthread_t linux线程，java的Thread最终也是调用pthread

#### 交叉编译原理与CI测试

wsl不能实现交叉编译，需要用虚拟机，暂时不搞这个了

#### FFmpeg软解之视频软解

能播放rtmp协议流、m3u8连接、mp4、avi......视频
parameters->codec_id 视频格式不能写死，从这里获取
dts时间戳，SurfaceView不支持yuv只支持rgb，ffmpeg返回的数据不能直接渲染。所以要通过SurfaceView--->NativeWindow---->缓冲区 把yuv转成rgb放到缓冲区
yuv转rgb需要libswscale.so提供的转换器
rgbFrame = av_frame_alloc() 转换后的rgb帧的data是一个二维数组，要有专门的方法填充 av_image_fill_arrays
ANativeWindow_fromSurface是android里面的库，需要引入target_link_libraries( android-lib)

#### FFmpeg软解之音频软解

音频倍速原理重新整理波形，利用soundtouch实现
swr_ctx 对音频进行重采样，有必要吗？涂老师意思是多个音频采样率不一样，为了兼容重采样。可是示例是播放一个连接
解析后的音频数据传递给AudioTrack播放，由于不在一个线程，使用了之前将jni获取不同线程对象的技术
该节课结果音视频不同步，音频有杂音

#### FFmpeg同步原理机制 与 Opensl es 播放器流程

AudioTrack支持音频少，mp3、pcm、wav。不能播放一些高清音频，一般都是用opensl es ，AudioTrack底层也是调用的opensl es。上一节解码后的音频还要传递到java层用AudioTrack播放影响性能
opensl es也能录音
初始ffmpeg的过程比如avformat_network_init访问网络、获取上下文是一个耗时操作放在子线程中执行
C++调用C函数的时候，C函数式无法调用native-lib.cpp或者类外的对象这时候，要给c函数传进去一个this。

```C++
pthread_create(&decodeThread, NULL, decodeFFmpeg, this); 该方法在类MNFFmpeg中
void *decodeFFmpeg(void *data){ MNFFmpeg *mnFFmpeg = (MNFFmpeg *) data;}
```

#### 网易云播放器实战02- FFmpeg解码音频播放

ndk-bundle中自带libOpenSLES.so 所以只需要在cMakeList引入即可，opensles创建流程查看 OpenSL ES.pdf，在AndroidMedia文件夹下
opensles的方法要通过接口调用，类似Java的设计模式， (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineEngine)
;通过引擎接口engineEngine调用方法
混音器也要通过接口调用方法
涂老师说创建opensles的过程是固定的，只要关注解码后把pcm数据传递给opensles这段是怎么实现的就可以。真是这样？？？ pcmBufferCallBack{该方法是一个回调函数，在这里把pcm传递给opensles}
buffer = (uint8_t *) av_malloc(sample_rate * 2 * 2);音频缓冲区， 采样频率*双通道*两字节
int nb = swr_convert(....) nb是1S钟重采样后得到的单通道数据大小
pcmBufferCallBack塞pcm的回调函数，执行非常快，如果在这里更新界面会导致卡死，所以每隔0.1S更新一次
显示时间：总时间 pFormatCtx->duration/ AV_TIME_BASE; 结果以秒为单位
时间基pFormatCtx->streams[i]->time_base; 例如{1,25}。 解码时间=pts*(1/25) 单位秒
mnAudio->clock += buffersize / ((double) (mnAudio->sample_rate * 2 * 2))
;//涂老师说这事根据数据量计算显示时间，由于会出现配置帧，所以加上pts显示时间校正，说实在我没看懂这个数据量计算是怎么回事，每一帧的数据buffersize不是固定的吗？这个值在哪里增加了？
seek:当拖动停止时再调用seek，在这个回调函数onStopTrackingTouch，ffmpeg有seek方法 avformat_seek_file(pFormatCtx, -1, INT64_MIN, rel,
INT64_MAX, 0);rel当前时间 INT64_MIN和INT64_MAX什么意思
seek的时候要加个锁否则报错pthread_mutex_unlock(&seek_mutex);
暂停、恢复、音量、左右通道静音 都是opensles的功能

#### 网易云音乐实战03-变速播放

视频通过丢帧达到倍速，音频要是用soundtouch整理波形，实现倍速效果，fmod实现变音效果
[soundTouch源码来自b站开源项目](https://github.com/bilibili/soundtouch/tree/master/source/SoundTouch)
sampleBuffer 波形整理之前先把小字节换成大字节
soundTouch->putSamples(sampleBuffer, nb);
num = soundTouch->receiveSamples(sampleBuffer, data_size / 4);先put后receiveSamples。一次处理多个波形，处理到一定程度开始输出size
soundTouch->setTempo(speed);倍速api
soundTouch->setPitch(pitch);变调 1.0 正常 大于1偏女性，小于1偏男性
倍速有杂音

#### 万能播放器之手写倍速效果(1.5倍，2.0倍，3.0倍)
JNI 线程只能在C中开启，C++是没有线程的，涂老师这样说对吗待议
做音视频同步要以音频为准，所以要在MNVideo 中持有一个MNAudio 引用
使用前要初始化锁
pthread_mutex_init(&seek_mutex, NULL);
pthread_mutex_init(&init_mutex, NULL);
判断解码出的视频格式，该例中统一转换成yuv420，转换之后把数据会调给java层。 avFrame->data[0];//y avFrame->data[1];//u avFrame->data[2];//v
非yuv420 转换成yuv420 使用了ffmpeg提供的方法sws_scale
java层得到yuv数据后用opengl渲染
attribute vec2 vCoord;早起的例子纹理坐标类型是vec4 怎么这里改成vec2 了？？？
yuv用3个纹理(采样器)   GLES20.glGenTextures(3, textureId_yuv, 0);
在片元程序中创建接收y u v纹理的变量 uniform sampler2D sampler_y; uniform sampler2D sampler_u; uniform sampler2D sampler_v;
```
GLES20.glActiveTexture(GLES20.GL_TEXTURE1);要创建三个图层，一个图层绑定一个纹理
GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId_yuv[1]);
GLES20.glTexImage2D()//绑定土层之后开始往这个方法塞数据
```
如果吧yuv转成rgb再放到GPU渲染，yuv到rgb这一步会消耗过多cpu资源，效率低
#### 实现倍数播放 Opengl绘制视频  

#### 记录

wav格式音频，由头+pcm组成，AudioTrack播放wav格式的时候，解析头获取信息，然后播放pcm  
pcm经过mediacodec编码后是aac格式
采样频率44100是指1S钟采集到的个数。所以AudioRecord读取一次数据是1S钟的数据
DTS（Decoding Time Stamp）：即解码时间戳，这个时间戳的意义在于告诉播放器该在什么时候解码这一帧的数据。
PTS（Presentation Time Stamp）：即显示时间戳，这个时间戳用来告诉播放器该在什么时候显示这一帧的数据。
DTS 告诉我们该按什么顺序解码这几帧图像，PTS 告诉我们该按什么顺序显示这几帧图像。

```agsl
   PTS: 1 4 2 3
   DTS: 1 2 3 4
Stream: I P B B
```

如果把1秒分为25等份，你可以理解就是一把尺，那么每一格表示的就是1/25秒。此时的time_base={1，25}
如果你是把1秒分成90000份，每一个刻度就是1/90000秒，此时的time_base={1，90000}。
所谓时间基表示的就是每个刻度是多少秒
pts里面存的不是显示时间而是刻度，显示时间计算公式为，加入pts=20、time_base={1，25} 显示时间=pts*time_base=20*1/25；在ffmpeg中pts*av_q2d(time_base)
，在mediacodec pts*帧率倒数
[根据pts显示时间计算方法](https://zhuanlan.zhihu.com/p/101480401)
编码时pts计算方式

```kotlin
private fun computePresentationTime(frameIndex: Long): Long {//frameIndex 帧索引
    return frameIndex * 1000_000 / 15//15是帧率
}
```