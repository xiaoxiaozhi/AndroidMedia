#extension GL_OES_EGL_image_external : require
precision lowp float;
//片元 main  坐标值
varying vec2 aCoord;
//如果与图层绑定的纹理来自于摄像头 用samplerExternalOES， 来自于GPU用sampler2D。
uniform samplerExternalOES  vTexture;
void main() {
    float x= aCoord.x;
//    float a = 1.0/3.0;
//    if(x<a)
//    {
//        x+=a;
//    }else if(x>2.0*a){
//        x -= 1.0/3.0;
//    }
    //    自带的采样器      图层 采样对应的像素值  坐标  （0,0）
    vec4 rgba =  texture2D(vTexture,vec2(x,aCoord.y));

//    4分屏   9 分屏 作业


    //    静止红色 gl_FragColor 输出的
    gl_FragColor=rgba;
}
