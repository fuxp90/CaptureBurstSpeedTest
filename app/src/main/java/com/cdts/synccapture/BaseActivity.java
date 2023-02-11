package com.cdts.synccapture;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

public class BaseActivity extends AppCompatActivity {

    private final static String TAG = "BaseActivity";
    private final static long MemMB = 1024 * 1024;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: " + this);
    }

    public void setActionBarTitle(int title) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(title);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: " + this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: " + this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: " + this);
    }

    public String getMaxMemoryInfo() {
        StringBuilder stringBuilder = new StringBuilder();
        Runtime rt = Runtime.getRuntime();
        long freeMemory = rt.freeMemory();
        long totalMemory = rt.totalMemory();
        long maxMemory = rt.maxMemory();
        //stringBuilder.append("Dalvik MaxMemory:").append(maxMemory / MemMB).append(" MB\n");
        stringBuilder.append("Dalvik UsedMemory:").append((totalMemory - freeMemory) / MemMB).append(" MB\n");
        stringBuilder.append("Dalvik TotalMemory:").append(maxMemory / MemMB).append(" MB\n");

        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(info);


        stringBuilder.append("Device Used:").append((info.totalMem - info.availMem) / MemMB).append(" MB\n");
        stringBuilder.append("Device RAM:").append(info.totalMem / MemMB).append(" MB\n");
        Log.e(TAG, stringBuilder.toString());
        return stringBuilder.toString();
    }
}
