package com.cdts.synccapture;

import android.text.TextUtils;

public class Utils {

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
}
