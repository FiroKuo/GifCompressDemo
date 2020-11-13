package com.ziguhonglan.testapp.ui.giftool;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@SuppressWarnings({"SameParameterValue", "ResultOfMethodCallIgnored"})
public class GifUtil {

    public static boolean isGif(String str) {
        return isEndWid(str, "gif");
    }

    private static boolean isEndWid(String str, String ext) {
        if (str == null || "".equals(str.trim())) {
            return false;
        }

        int position = str.lastIndexOf(".");
        if (position == -1 || (position == str.length() - 1)) {
            return false;
        }
        String suffix = str.substring(position + 1);
        return ext.equalsIgnoreCase(suffix);
    }

    public static boolean isGif(InputStream in) throws IOException {
        if (!in.markSupported()) {
            throw new IllegalArgumentException("Input stream must support mark");
        }
        byte[] b = new byte[6];
        try {
            in.mark(30);
            in.read(b);
        } finally {
            in.reset();
        }
        return b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8' && (b[4] == '7' || b[4] == '9') && b[5] == 'a';
    }

    public boolean resizeGif(InputStream in, OutputStream out, int maxWidth, int maxHeight) throws IOException {
        checkParams(in, out, maxWidth, maxHeight);
        GifFrame[] frameList = null;
        try {
            GifDecoder gifDecoder = new GifDecoder();
            int code = gifDecoder.read(in);
            if (code == GifDecoder.STATUS_OK) {//解码成功
                frameList = gifDecoder.getFrames();
            } else {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (frameList == null) {
            return false;
        }
        AnimatedGifEncoder ge = new AnimatedGifEncoder();
        ge.start(out);
        ge.setRepeat(0);
        int ratio = getFrameRatio(frameList);
        for (int i = 0; i < frameList.length; i++) {
            if (i % ratio == 0) {
                Bitmap frame = frameList[i].image;
                int delay = frameList[i].delay;
                ge.setDelay(delay * ratio);
                ge.addFrame(frame, true, maxWidth);
            }
        }
        ge.finish();
        return true;
    }

    public static void resizeGifWithMultiThreadsAsync(final String originPath, final String destPath, @NotNull final IResizeGifCallback callback) throws IOException {
        if (originPath == null || originPath.isEmpty() || destPath == null || destPath.isEmpty()) {
            callback.onFailed(originPath);
            return;
        }
        final File originFile = new File(originPath);
        if (!originFile.exists()) {
            callback.onFailed(originPath);
            return;
        }
        if (!originFile.isFile()) {
            callback.onFailed(originPath);
            return;
        }
        File destFile = new File(destPath);
        if (destFile.isDirectory()) {
            destFile = new File(destFile, System.currentTimeMillis() + ".gif");
        }
        if (!destFile.exists()) {
            destFile.createNewFile();
        }
        final File finalDestFile = destFile;
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                boolean result;
                try {
                    result = resizeGifWithMultiThreadsSync(new FileInputStream(originFile), new FileOutputStream(finalDestFile));
                } catch (IOException e) {
                    e.printStackTrace();
                    result = false;
                }
                final boolean finalResult = result;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (finalResult) {
                            callback.onSuccess(finalDestFile.getAbsolutePath());
                        } else {
                            callback.onFailed(originPath);
                        }
                    }
                });
            }
        });
    }


    public static boolean resizeGifWithMultiThreadsSync(InputStream in, OutputStream out) throws IOException {
        if (in == null || out == null)
            throw new IOException("InputStream or OutputStream must be not null.");
        GifFrame[] frameList = null;
        try {
            GifDecoder gifDecoder = new GifDecoder();
            int code = gifDecoder.read(in);
            if (code == GifDecoder.STATUS_OK) {//解码成功
                frameList = gifDecoder.getFrames();
            } else {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (frameList == null) {
            return false;
        }
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        Log.w("GifUtil", "thread count: " + 5);
        ArrayList<ByteArrayOutputStream> outputStreams = new ArrayList<>();
        ArrayList<Runnable> tasks = new ArrayList<>();
        ArrayList<Future<?>> futures = new ArrayList<>();
        int ratio = getFrameRatio(frameList);
        for (int i = 0; i < frameList.length; i++) {
            if (i % ratio == 0) {
                Bitmap frame = frameList[i].image;
                int delay = frameList[i].delay;
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                outputStreams.add(bos);
                EncoderTask task = new EncoderTask(bos, frame, i == 0, ratio, delay, 0);
                tasks.add(task);
            }
        }
        for (Runnable task : tasks) {
            Future<?> future = executorService.submit(task);
            futures.add(future);
        }
        boolean isComplete = true;
        //等待任务执行完成
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                e.printStackTrace();
                isComplete = false;
            } catch (InterruptedException e) {
                e.printStackTrace();
                isComplete = false;
            }
        }
        //将各个bos写入output
        for (int i = 0; i < outputStreams.size(); i++) {
            ByteArrayOutputStream byteArrayOutputStream = outputStreams.get(i);
            out.write(byteArrayOutputStream.toByteArray());
            byteArrayOutputStream.close();
        }
        //写尾标志
        out.write(0x3b); // gif trailer
        out.flush();
        out.close();
        outputStreams.clear();
        tasks.clear();
        executorService.shutdownNow();
        return isComplete;
    }

    private static int getFrameRatio(GifFrame[] frameList) {
        Log.w("GifUtil", "frame count: " + frameList.length);
        Log.w("GifUtil", "frame in sample: " + 2);
        return 2;
    }

    private static void checkParams(InputStream in, OutputStream out, int maxWidth, int maxHeight)
            throws IOException {
        if (in == null) {
            throw new IOException("InputStream can not be null ");
        }
        if (out == null) {
            throw new IOException("OutputStream can not be null ");
        }
        if (maxWidth < 1 || maxHeight < 1) {
            throw new IOException("maxWidth or maxHeight can not be less than 1 ");
        }
    }

}
