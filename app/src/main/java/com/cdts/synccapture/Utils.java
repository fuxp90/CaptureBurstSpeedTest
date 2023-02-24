package com.cdts.synccapture;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;

public class Utils {

    public static final String KEY_DEVICE_NAME = "key_device_name";

    public static final String DEF_DEVICE_NAME = Build.BRAND + "_" + Build.MODEL + "_" + Build.BOARD;

    public static <E extends Enum<E>> String[] toStringArray(Enum<E>[] es) {
        String[] arr = new String[es.length];
        for (int i = 0; i < es.length; i++) {
            arr[i] = es[i].name();
        }
        return arr;
    }

    public static <E extends Enum<E>> int indexOf(Enum<E>[] es, String str) {
        for (int i = 0; i < es.length; i++) {
            if (TextUtils.equals(str, es[i].name())) {
                return i;
            }
        }
        return -1;
    }

    public static <E extends Enum<E>> int indexOf(Enum<E>[] es, Enum<E> e) {
        for (int i = 0; i < es.length; i++) {
            if (es[i] == e) {
                return i;
            }
        }
        return -1;
    }


    private static SharedPreferences mPreferences;

    public static void initSpf(Context context) {
        if (mPreferences == null) {
            mPreferences = context.getSharedPreferences("sync_config", Context.MODE_PRIVATE);
        }
    }

    public static String getSpf(String key, String val) {
        return mPreferences.getString(key, val);
    }

    public static void putSpf(String key, String val) {
        mPreferences.edit().putString(key, val).commit();

    }

}
