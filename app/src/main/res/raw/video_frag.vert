#extension GL_OES_EGL_image_external : require
//必须 写的 固定的  意思   用采样器
//所有float类型数据的精度是lowp
precision lowp float;
varying vec2 aCoord;
/*samplerExternalOES*/
//采样器 不是从android的surfaceTexure中的纹理 采数据了，所以不再需要android的扩展纹理采样器了
//使用正常的 sampler2D  图层采样  要1  不要2
uniform sampler2D vTexture;
void main(){
    //Opengl 自带函数
    vec4 rgba = texture2D(vTexture,vec2(aCoord.x,aCoord.y));
    gl_FragColor=vec4(rgba.r,rgba.g,rgba.b,rgba.a);
}