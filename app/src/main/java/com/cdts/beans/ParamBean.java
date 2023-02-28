package com.cdts.beans;

import java.io.Serializable;

public class ParamBean implements Serializable {

    private boolean mClearLocalCache = true;
    private long mExposure = 10000000;
    private int mIso = 444;
    private float mFocusDistance = 0;
    private int mFrameRate = 8;
    private int mImageFormat = 0;/*JPEG-0,RAW10-2,RAW_SENOR-3,YUV_420_888-3*/
    private int mWhiteBalanceOffset = 0;
    private boolean mForceAutoWhiteBalanceCheck = false;
    private String mNameOfUpcoming = "TestA";
    private int mSaveType = 0; /*FLASH-0, RAM-1*/
    private boolean isAuto3a;
    private String mImageSize = "4000*3000";
    // CaptureOneByOne(true), CaptureBurst(false), CaptureRepeating(false), CaptureFixRate(true), CaptureMultiThread(true), CaptureOnAhead(true);
    private int mCaptureMode = 3;/*0-5*/


    public void setCaptureMode(int captureMode) {
        mCaptureMode = captureMode;
    }

    public int getCaptureMode() {
        return mCaptureMode;
    }

    public boolean isClearLocalCache() {
        return mClearLocalCache;
    }

    public void setClearLocalCache(boolean clearLocalCache) {
        mClearLocalCache = clearLocalCache;
    }

    public long getExposure() {
        return mExposure;
    }

    public void setExposure(long exposure) {
        mExposure = exposure;
    }

    public int getIso() {
        return mIso;
    }

    public void setIso(int iso) {
        mIso = iso;
    }

    public float getFocusDistance() {
        return mFocusDistance;
    }

    public void setFocusDistance(float focusDistance) {
        mFocusDistance = focusDistance;
    }

    public int getFrameRate() {
        return mFrameRate;
    }

    public void setFrameRate(int frameRate) {
        mFrameRate = frameRate;
    }

    public int getImageFormat() {
        return mImageFormat;
    }

    public void setImageFormat(int frameType) {
        mImageFormat = frameType;
    }

    public int getWhiteBalanceOffset() {
        return mWhiteBalanceOffset;
    }

    public void setWhiteBalanceOffset(int whiteBalanceOffset) {
        mWhiteBalanceOffset = whiteBalanceOffset;
    }

    public boolean isForceAutoWhiteBalanceCheck() {
        return mForceAutoWhiteBalanceCheck;
    }

    public void setForceAutoWhiteBalanceCheck(boolean forceAutoWhiteBalanceCheck) {
        mForceAutoWhiteBalanceCheck = forceAutoWhiteBalanceCheck;
    }

    public String getNameOfUpcoming() {
        return mNameOfUpcoming;
    }

    public void setNameOfUpcoming(String nameOfUpcoming) {
        mNameOfUpcoming = nameOfUpcoming;
    }

    public void setSaveType(int i) {
        mSaveType = i;
    }

    public int getSaveType() {
        return mSaveType;
    }

    public void setAuto3a(boolean auto3a) {
        isAuto3a = auto3a;
    }

    public boolean isAuto3a() {
        return isAuto3a;
    }

    public void setAuto3A(boolean i) {
        isAuto3a = i;
    }

    public void setImageSize(String text) {
        mImageSize = text;
    }

    public String getImageSize() {
        return mImageSize;
    }


    @Override
    public String toString() {
        return "ParamBean{" +
            "mClearLocalCache=" + mClearLocalCache +
            ", mExposure=" + mExposure +
            ", mIso=" + mIso +
            ", mFocusDistance=" + mFocusDistance +
            ", mFrameRate=" + mFrameRate +
            ", mImageFormat=" + mImageFormat +
            ", mWhiteBalanceOffset=" + mWhiteBalanceOffset +
            ", mForceAutoWhiteBalanceCheck=" + mForceAutoWhiteBalanceCheck +
            ", mNameOfUpcoming='" + mNameOfUpcoming + '\'' +
            ", mSaveType=" + mSaveType +
            ", isAuto3a=" + isAuto3a +
            ", mImageSize='" + mImageSize + '\'' +
            '}';
    }
}
