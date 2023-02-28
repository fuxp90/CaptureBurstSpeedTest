package com.cdts.synccapture;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Size;

import com.cdts.beans.ParamBean;

public class Utils {

    public static final String KEY_RECORD_NAME = "key_record_name";
    public static final String DEF_RECORD_NAME = "Record1";
    private static final String SPLIT = "*";

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

    public static Size parseParam(ParamBean bean) {
        try {
            String[] arr = bean.getImageSize().split(SPLIT);
            int w = Integer.parseInt(arr[0].trim());
            int h = Integer.parseInt(arr[1].trim());
            return new Size(w, h);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return new Size(4000, 300);
        }
    }

    public static CameraController.Fmt parseFmt(ParamBean paramBean) {
        switch (paramBean.getImageFormat()) {
            case 0:
                return CameraController.Fmt.JPEG;
            case 1:
                return CameraController.Fmt.RAW10;
            case 2:
                return CameraController.Fmt.RAW_SENSOR;
            case 3:
                return CameraController.Fmt.YUV;
        }
        return CameraController.Fmt.JPEG;
    }

    public static Storage.SaveType parseSaveType(ParamBean paramBean) {
        return paramBean.getSaveType() == 0 ? Storage.SaveType.Flash : Storage.SaveType.RAM;
    }

    public static CameraController.CaptureMode parseCaptureMode(ParamBean paramBean) {
        for (CameraController.CaptureMode captureMode : CameraController.CaptureMode.values()) {
            if (captureMode.ordinal() == paramBean.getCaptureMode()) {
                return captureMode;
            }
        }
        return CameraController.CaptureMode.CaptureFixRate;
    }
}
