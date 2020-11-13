package com.ziguhonglan.testapp.giftool;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;

/**
 * @author Firo
 * time: 2020/11/12 09:32
 * email: firo94@foxmail.com
 */
class EncoderTask extends Thread {

    private ByteArrayOutputStream bos;
    private Bitmap frame;
    private boolean isFirstFrame;
    private int frameRatio = 1;
    private int delay;
    private int repeat;

    public EncoderTask(ByteArrayOutputStream bos, Bitmap frame, boolean isFirstFrame, int frameRatio, int delay, int repeat) {
        this("gif-encoder-thread", bos, frame, isFirstFrame, frameRatio, delay, repeat);
    }

    public EncoderTask(@NonNull String name, ByteArrayOutputStream bos, Bitmap frame, boolean isFirstFrame, int frameRatio, int delay, int repeat) {
        super(name);
        this.bos = bos;
        this.frame = frame;
        this.isFirstFrame = isFirstFrame;
        this.frameRatio = frameRatio;
        this.delay = delay;
        this.repeat = repeat;
    }

    public ByteArrayOutputStream getOutputStream() {
        return bos;
    }

    public void setOutputStream(ByteArrayOutputStream bos) {
        if (this.bos == null) {
            this.bos = bos;
        }
    }

    public Bitmap getFrame() {
        return frame;
    }

    public void setFrame(Bitmap frame) {
        this.frame = frame;
    }

    public boolean isFirstFrame() {
        return isFirstFrame;
    }

    public void setFirstFrame(boolean firstFrame) {
        isFirstFrame = firstFrame;
    }

    public int getFrameRatio() {
        return frameRatio;
    }

    public void setFrameRatio(int frameRatio) {
        this.frameRatio = frameRatio;
    }

    public int getDelay() {
        return delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    public int getRepeat() {
        return repeat;
    }

    public void setRepeat(int repeat) {
        this.repeat = repeat;
    }

    @Override
    public void run() {
        if (bos == null) throw new IllegalArgumentException("Setup outputStream first.");
        SimpleAnimatedGifEncoder ge = new SimpleAnimatedGifEncoder();
        ge.start(bos, isFirstFrame);
        ge.setRepeat(repeat);
        ge.setDelay(delay * frameRatio);
        ge.writeFrameData(frame);
        ge.finish();
    }
}
