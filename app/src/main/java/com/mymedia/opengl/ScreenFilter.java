package com.mymedia.opengl;

import android.content.Context;

import com.mymedia.R;

public class ScreenFilter extends  AbstractFilter{
    public ScreenFilter(Context context) {
        super(context, R.raw.video_vert, R.raw.video_frag);
    }
}
