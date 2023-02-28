package com.cdts.beans;

import java.util.ArrayList;
import java.util.List;

public class DeviceBean {
    private String mSerial;


    public void setSerial(String serial) {
        mSerial = serial;
    }

    public String getSerial() {
        return mSerial;
    }

    public String getAdbCmd(String adb, String... args) {
        List<String> list = new ArrayList<>();
        for (String a : args)
            list.add(a);
        list.add(0, mSerial);
        return String.format(adb, list.toArray());
    }

    @Override
    public String toString() {
        return "Serial=" + mSerial;
    }
}
