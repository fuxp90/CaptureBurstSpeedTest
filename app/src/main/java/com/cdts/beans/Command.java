package com.cdts.beans;

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
        return "Command{" +
                "mCmd=" + mCmd +
                ", mParamBean=" + mParamBean +
                '}';
    }

    public byte[] getBytes() {
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
}
