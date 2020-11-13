package com.ziguhonglan.testapp.ui.giftool;

import android.graphics.Bitmap;

/**
 * Created by jianglixuan on 2020/5/7.
 * Describe:  各帧静态图对象
 *
 *
 */
public class GifFrame {
    public Bitmap image;//静态图Bitmap
    public int delay;//图像延迟时间

    public GifFrame(Bitmap im, int del) {
        image = im;
        delay = del;
    }
}
