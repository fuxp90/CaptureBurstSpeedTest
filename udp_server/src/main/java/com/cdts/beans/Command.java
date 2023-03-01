package com.cdts.beans;

import com.formdev.flatlaf.json.Json;
import com.google.gson.Gson;

import java.io.*;

public class Command implements Serializable {
    public static final int audio_sync_start = 0;
    public static final int audio_sync_stop = 1;
    public static final int set_param = 2;
    public static final int start_capture = 3;
    public static final int stop_capture = 4;
    public static final Command AUDIO_SYNC_START = new Command(audio_sync_start);
    public static final Command AUDIO_SYNC_STOP = new Command(audio_sync_stop);
    public static final Command SET_PARAM = new Command(set_param);
    public static final Command CAPTURE_START = new Command(start_capture);
    public static final Command CAPTURE_STOP = new Command(stop_capture);
    private final int mCmd;
    private ParamBean mParamBean;

    private Command(int cmd) {
        mCmd = cmd;
    }

    public void setParamBean(ParamBean paramBean) {
        mParamBean = paramBean;
    }

    public int getCmd() {
        return mCmd;
    }

    public ParamBean getParamBean() {
        return mParamBean;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }

    String getCmd(int cmd) {
        switch (cmd) {
            case audio_sync_start:
                return "audio_sync_start";
            case audio_sync_stop:
                return "audio_sync_stop";
            case set_param:
                return "set_param";
            case start_capture:
                return "start_capture";
            case stop_capture:
                return "stop_capture";
            default:
                return "unknown";
        }
    }

    public byte[] getObjectBytes() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(this);
            objectOutputStream.flush();
            objectOutputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return byteArrayOutputStream.toByteArray();
    }

    public byte[] getJsonByte() {
        return new Gson().toJson(this).getBytes();
    }
}
