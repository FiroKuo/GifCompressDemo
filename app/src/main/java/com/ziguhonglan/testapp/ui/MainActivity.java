package com.ziguhonglan.testapp.ui;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.ziguhonglan.testapp.R;
import com.ziguhonglan.testapp.ui.giftool.GifUtil;
import com.ziguhonglan.testapp.ui.giftool.IResizeGifCallback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Firo
 * time: 2020/10/29 18:25
 * email: firo94@foxmail.com
 */
public class MainActivity extends Activity {

    private ImageView originImageView;
    private ImageView compressView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        originImageView = findViewById(R.id.originView);
        compressView = findViewById(R.id.compressView);

        Glide.with(this).load(R.drawable.test3).into(originImageView);
    }

    public void compress(View view) {
        compressASync();
    }

    public void clear(View view) {
        Glide.with(this).clear(compressView);
    }

    private void compressASync() {
        try {
            String originPath = getFilesDir() + "/test.gif";
            String destPath = getFilesDir().getAbsolutePath();
            GifUtil.resizeGifWithMultiThreadsAsync(originPath, destPath, new IResizeGifCallback() {
                @Override
                public void onSuccess(String path) {
                    Glide.with(MainActivity.this).load(path).into(compressView);
                }

                @Override
                public void onFailed(String path) {
                    Toast.makeText(MainActivity.this, "failed: path=" + path, Toast.LENGTH_SHORT).show();
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void compressSync() {
        Log.w("GifUtil", "=====================================");
        try {
            InputStream is = getAssets().open("test3.gif");
            int originSize = is.available();
            Log.w("GifUtil", "origin size: " + originSize * 1.0 / (1024 * 1024) + " MB");
            String fileName = "test.gif";
            FileOutputStream fos = openFileOutput(fileName, MODE_PRIVATE);
            long startTime = System.currentTimeMillis();
            boolean result = GifUtil.resizeGifWithMultiThreadsSync(is, fos);
            Log.w("gsx", "resize result: " + result);
            Log.w("GifUtil", "use time in second: " + (System.currentTimeMillis() - startTime) / 1000 + "s");
            final String path = getFilesDir().getAbsolutePath() + "/" + fileName;
            File compressFile = new File(path);
            long compressSize = compressFile.length();
            Log.w("GifUtil", "compress size: " + compressSize * 1.0 / (1024 * 1024) + " MB");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Glide.with(MainActivity.this).load(path).into(compressView);
                }
            });
            Log.w("GifUtil", "compress ratio: " + (1 - compressSize * 1.0 / originSize) * 100 + "%");
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.w("GifUtil", "=====================================");
    }
}
