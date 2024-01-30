attribute vec4 vPosition;
attribute vec4 vCoord;
varying vec2 textureCoordinate;
//
void main(){
    //内置变量： 把坐标点赋值给gl_position 就Ok了。  4 个元素  gl_Position  世界坐标系 为基础
//    opengl  矩形
    gl_Position = vPosition;
    textureCoordinate = vCoord.xy;
}