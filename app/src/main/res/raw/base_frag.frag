varying highp vec2 textureCoordinate;
uniform sampler2D inputImageTexture;//0图层
//片元      纹理坐标系  2
void main(){
    gl_FragColor = texture2D(inputImageTexture, textureCoordinate);
//    gl_FragColor = vec4(0,255,0,0);
}