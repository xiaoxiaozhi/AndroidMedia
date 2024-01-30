//顶点程序
//形状   cpu  定义好了
//  cpu  --- 传值过来


// 接受变量

//gpu 的变量
//    vec 坐标的意思      3个 立体    xyz    xy  4  个值
attribute  vec4 vPosition;

//    float[] VERTEX = {
//        -1.0f, -1.0f,
//        1.0f, -1.0f,
//        -1.0f, 1.0f,
//        1.0f, 1.0f
//    };
//纹理坐标系
attribute vec4 vCoord;

uniform mat4 vMatrix;


//    float[] TEXTURE = {
//            0.0f, 0.0f,
//            1.0f, 0.0f,
//            0.0f, 1.0f,
//            1.0f, 1.0f
//    };
//   顶点的坐标 传值给   片元程序
varying  vec2 aCoord;
void main() {
//    opengl  形状确定
    gl_Position=vPosition;
    aCoord= (vMatrix*vCoord).xy;
}
