package com.mymedia.golomb;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ManiuMediaCodec {
    private  String path;

    public ManiuMediaCodec(String path) {
        this.path = path;
    }
//二进制位 的 值
    private static int nStartBit = 0;

    private static int Ue(byte[] pBuff)
    {
        int nZeroNum = 0;
        while (nStartBit < pBuff.length * 8)
        {
            if ((pBuff[nStartBit / 8] & (0x80 >> (nStartBit % 8))) != 0)
            {
                break;
            }
            nZeroNum++;
            nStartBit++;
        }
        nStartBit ++;

        int dwRet = 0;
        for (int i=0; i<nZeroNum; i++)
        {
            dwRet <<= 1;
            if ((pBuff[nStartBit / 8] & (0x80 >> (nStartBit % 8))) != 0)
            {
                dwRet += 1;
            }
            nStartBit++;
        }
        return (1 << nZeroNum) - 1 + dwRet;
    }
    private static int u(int bitIndex, byte[] h264)
    {
        int dwRet = 0;
        for (int i=0; i<bitIndex; i++)
        {
            dwRet <<= 1;
            if ((h264[nStartBit / 8] & (0x80 >> (nStartBit % 8))) != 0)
            {
                dwRet += 1;
            }
            nStartBit++;
        }
        return dwRet;
    }
// MediaCodec ---->   dsp芯片     模拟dsp   怎么解析h264码流
    public void startCodec() {
        byte[] h264 = null;
        try {
            h264 = geth264(path);
        } catch ( Exception e) {
            e.printStackTrace();
        }

        int totalSize = h264.length;
        int startIndex = 0;
        while (true) {
            if (totalSize == 0 || startIndex >= totalSize) {
                break;
            }
            int nextFrameStart = findByFrame(  h264, startIndex+2 , totalSize);

            nStartBit = 4*8;
            int forbidden_zero_bit=u(1, h264);
            if (forbidden_zero_bit != 0) {
                continue;
            }
            int nal_ref_idc       =u(2, h264);
//            排列
            int nal_unit_type     =u(5, h264);
            switch (nal_unit_type) {
                case 1:
                    break;
                case 2:
                    break;
                case 3:
                    break;
                case 4:
                    break;
                case 5:
                    break;
                case 6:
                    break;
                case 7:

                    parseSps(h264);

                    break;
            }

        }

    }

    private void parseSps(byte[] h264) {
        //            编码等级   Baseline 直播   Main Extended High   High 10   High 4:2:2
//0x64
        int profile_idc = u(8, h264);
//            当constrained_set0_flag值为1的时候，就说明码流应该遵循基线profile(Baseline profile)的所有约束.constrained_set0_flag值为0时，说明码流不一定要遵循基线profile的所有约束。
        int constraint_set0_flag = u(1, h264);//(h264[1] & 0x80)>>7;
        //            当constrained_set1_flag值为1的时候，就说明码流应该遵循主profile(Main profile)的所有约束.constrained_set1_flag值为0时，说明码流不一定要遵
        int constraint_set1_flag = u(1, h264);//(h264[1] & 0x40)>>6;
        //当constrained_set2_flag值为1的时候，就说明码流应该遵循扩展profile(Extended profile)的所有约束.constrained_set2_flag值为0时，说明码流不一定要遵循扩展profile的所有约束。
        int constraint_set2_flag = u(1, h264);//(h264[1] & 0x20)>>5;
//            注意：当constraint_set0_flag,constraint_set1_flag或constraint_set2_flag中不只一个值为1的话，那么码流必须满足所有相应指明的profile约束。
        int constraint_set3_flag = u(1, h264);//(h264[1] & 0x10)>>4;

//            4个零位
        int reserved_zero_4bits = u(4, h264);
//            它指的是码流对应的level级
        int level_idc = u(8, h264);
        //            是否是哥伦布编码  0 是 1 不是
        int seq_parameter_set_id = Ue(h264);
        if (profile_idc == 100||profile_idc == 110||profile_idc == 122||profile_idc == 144) {
            int chroma_format_idc=Ue(h264);
            int bit_depth_luma_minus8   =Ue(h264);
            int bit_depth_chroma_minus8  =Ue(h264);
            int qpprime_y_zero_transform_bypass_flag=u(1, h264);
            int seq_scaling_matrix_present_flag     =u(1, h264);

        }

        int log2_max_frame_num_minus4=Ue(h264);
        int pic_order_cnt_type       =Ue(h264);
        if (pic_order_cnt_type == 0) {
            int log2_max_pic_order_cnt_lsb_minus4=Ue(h264);
        }
        int num_ref_frames                      =Ue(h264);
        int gaps_in_frame_num_value_allowed_flag=u(1,     h264);
        int pic_width_in_mbs_minus1             =Ue(h264);
        int pic_height_in_map_units_minus1      =Ue(h264);
//        pic_width_in_mbs_minus1   16的整数倍   而且  视频 0  0    16*16    1 *1   32*32
        int width=(pic_width_in_mbs_minus1       +1)*16;
        int height=(pic_height_in_map_units_minus1+1)*16;
        int frame_mbs_only_flag = u(1, h264);
        Log.i("David", "parseSps: frame_mbs_only_flag"+frame_mbs_only_flag);
        if (frame_mbs_only_flag != 0) {
            int mb_adaptive_frame_field_flag = u(1, h264);
        }
//1840
        int direct_8x8_inference_flag = u(1, h264);
//        0  就代表 宽高是16的整数倍     1   有偏移
        int frame_cropping_flag = u(1, h264);
        if (frame_cropping_flag != 0) {
            int frame_crop_left_offset=Ue(h264);
            int frame_crop_right_offset=Ue(h264);
            int frame_crop_top_offset=Ue(h264);
            int frame_crop_bottom_offset=Ue(h264);

            Log.i("David", "frame_crop_left_offset: "+frame_crop_left_offset+"  frame_crop_right_offset: "+frame_crop_right_offset);
//1 4    8   16   2  01    1 *2
            width = (pic_width_in_mbs_minus1 + 1) * 16 - frame_crop_left_offset * 2
                    - frame_crop_right_offset * 2;
            height= ((2 - frame_mbs_only_flag)* (pic_height_in_map_units_minus1 +1) * 16) -
                    (frame_crop_top_offset * 2) - (frame_crop_bottom_offset * 2);
            Log.i("David", "width: "+width+"  height: "+height);
        }

    }
    private int findByFrame( byte[] h264, int start, int totalSize) {
        int j = 0;  // Number of chars matched in pattern
        for (int i = start; i < totalSize; i++) {
            if ((h264[i] == 0x00 && h264[i + 1] == 0x00 && h264[i + 2] == 0x00
                    && h264[i + 3] == 0x01)||(h264[i] == 0x00 && h264[i + 1] == 0x00
                    && h264[i + 2] == 0x01)) {
                return i;
            }
        }
        return -1;  // Not found
    }

    public   byte[] geth264(String path) throws IOException {
        InputStream is =   new DataInputStream(new FileInputStream(new File(path)));
        int len;
        int size = 1024*1024;
        byte[] buf;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        buf = new byte[size];
        len=is.read(buf, 0, size);
        bos.write(buf, 0, len);
        buf = bos.toByteArray();
        bos.close();
        return buf;
    }


}
