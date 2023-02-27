package com.cdts.beans;

public class AdbScripts {

    public final static String ADB_DEVICES = "adb devices";
    public final static String ADB_INSTALL = "adb -s %s install ";

    public final static String ADB_GRANT_CAMERA = "adb -s %s shell pm grant com.cdts.synccapture android.permission.CAMERA";
    public final static String ADB_GRANT_AUDIO = "adb -s %s shell pm grant com.cdts.synccapture android.permission.RECORD_AUDIO";

    public final static String ADB_PULL_IMAGES = "adb -s %s pull /sdcard/Android/data/com.cdts.synccapture/cache %s";

    public static void installApk(String apk) {

    }
}
