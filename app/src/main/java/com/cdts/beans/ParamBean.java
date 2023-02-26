package com.cdts.beans;

import java.io.Serializable;

public class ParamBean implements Serializable {
    private boolean mClearLocalCache = true;
    private long mExposure = 3;
    private int mIso;
    private float mFocusDistance;
    private int mFrameRate;
    private int mFrameType;
    private int mWhiteBalanceOffset;
    private boolean mForceAutoWhiteBalanceCheck;
    private String mNameOfUpcoming;

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

    public int getFrameType() {
        return mFrameType;
    }

    public void setFrameType(int frameType) {
        mFrameType = frameType;
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

    @Override
    public String toString() {
        return "ParamBean{" +
                "mClearLocalCache=" + mClearLocalCache +
                ", mExposure=" + mExposure +
                ", mIso=" + mIso +
                ", mFocusDistance=" + mFocusDistance +
                ", mFrameRate=" + mFrameRate +
                ", mFrameType=" + mFrameType +
                ", mWhiteBalanceOffset=" + mWhiteBalanceOffset +
                ", mForceAutoWhiteBalanceCheck=" + mForceAutoWhiteBalanceCheck +
                ", mNameOfUpcoming='" + mNameOfUpcoming + '\'' +
                '}';
    }
}
