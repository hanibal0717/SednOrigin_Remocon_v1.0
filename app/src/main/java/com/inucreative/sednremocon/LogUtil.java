package com.inucreative.sednremocon;

import android.content.Context;
import android.util.Log;

/**
 * Created by jinsupkim on 2016. 5. 17..
 */
public class LogUtil {
    /** Log Level Error **/
    public static final void e(Context context, String message) {
        if (BuildConfig.DEBUG) Log.e(context.getClass().getSimpleName(), message);
    }

    /** Log Level Warning **/
    public static final void w(Context context, String message) {
        if (BuildConfig.DEBUG) Log.w(context.getClass().getSimpleName(), message);
    }

    /** Log Level Information **/
    public static final void i(Context context, String message) {
        if (BuildConfig.DEBUG) Log.i(context.getClass().getSimpleName(), message);
    }

    /** Log Level Debug **/
    public static final void d(Context context, String message) {
        if (BuildConfig.DEBUG) Log.d(context.getClass().getSimpleName(), message);
    }

    /** Log Level Verbose **/
    public static final void v(Context context, String message) {
        if (BuildConfig.DEBUG) Log.v(context.getClass().getSimpleName(), message);
    }

    /** Log Level Error **/
    public static final void e(String TAG, String message) {
        if (BuildConfig.DEBUG) Log.e(TAG, message);
    }

    /** Log Level Warning **/
    public static final void w(String TAG, String message) {
        if (BuildConfig.DEBUG) Log.w(TAG, message);
    }

    /** Log Level Information **/
    public static final void i(String TAG, String message) {
        if (BuildConfig.DEBUG) Log.i(TAG, message);
    }

    /** Log Level Debug **/
    public static final void d(String TAG, String message) {
        if (BuildConfig.DEBUG) Log.d(TAG, message);
    }

    /** Log Level Verbose **/
    public static final void v(String TAG, String message) {
        if (BuildConfig.DEBUG) Log.v(TAG, message);
    }

    public static final void d(String message) {
        d("SEDNRemoconClient", message);
    }
}
