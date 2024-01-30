#extension GL_OES_EGL_image_external : require
precision lowp float;
//片元 main  坐标值
varying vec2 aCoord;
//变量  第几个   0
uniform samplerExternalOES  vTexture;
void main() {
    float x= aCoord.x;
//    if(x<0.5)
//    {
//        x+=0.25;
//
//    }else{
//        x -= 0.25;
//    }
    //    自带的采样器      图层 采样对应的像素值  坐标  （0,0）
    vec4 rgba =  texture2D(vTexture,vec2(x,aCoord.y));
//    静止红色 gl_FragColor 输出的
    gl_FragColor=rgba;
}
