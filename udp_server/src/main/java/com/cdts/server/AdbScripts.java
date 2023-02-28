package com.cdts.server;

import com.cdts.beans.DeviceBean;

import java.util.ArrayList;
import java.util.List;

public class AdbScripts {

    public final static String ADB_DEVICES = "adb devices";
    public final static String ADB_INSTALL = "adb -s %s install -r -t -d %s";

    public final static String ADB_LAUNCH_CAMERA = "adb -s %s shell am start com.cdts.synccapture/com.cdts.synccapture.SpeedTestActivity";

    public final static String ADB_GRANT_CAMERA = "adb -s %s shell pm grant com.cdts.synccapture android.permission.CAMERA";
    public final static String ADB_GRANT_AUDIO = "adb -s %s shell pm grant com.cdts.synccapture android.permission.RECORD_AUDIO";

    public final static String ADB_PULL_IMAGES = "adb -s %s pull /sdcard/Android/data/com.cdts.synccapture/cache %s";

    public static List<DeviceBean> parseDeviceBean(List<String> list) {
        List<DeviceBean> deviceBeans = new ArrayList<>();
        if (list != null && list.size() > 1) {
            for (int i = 2; i < list.size(); i++) {
                String line = list.get(i).trim();
                System.out.println("line:" + line);
                DeviceBean deviceBean = new DeviceBean();
                deviceBean.setSerial(line.replace("device", "").trim());
                deviceBeans.add(deviceBean);
            }
        }
        return deviceBeans;
    }

    public static List<String> getDevices() {
        return AShell.exec(ADB_DEVICES);
    }

    public static List<String> installApk(DeviceBean bean, String apk) {
        return AShell.exec(bean.getAdbCmd(ADB_INSTALL, apk));
    }

    public static List<String> launchApk(DeviceBean bean) {
        return AShell.exec(bean.getAdbCmd(ADB_LAUNCH_CAMERA));
    }

    public static List<String> grantPermission(DeviceBean bean) {
        List<String> list1 = AShell.exec(bean.getAdbCmd(ADB_GRANT_CAMERA));
        List<String> list2 = AShell.exec(bean.getAdbCmd(ADB_GRANT_AUDIO));
        List<String> ret = new ArrayList<>();
        assert list1 != null;
        ret.addAll(list1);
        assert list2 != null;
        ret.addAll(list2);
        return ret;
    }

    public static List<String> pullImages(DeviceBean bean) {
        return AShell.exec(bean.getAdbCmd(ADB_PULL_IMAGES, "images"));
    }
}
