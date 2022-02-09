package com.unisoc.server.plugins.LocalTone;

import android.content.Context;
import android.util.Log;

import com.unisoc.server.plugins.LocalTone.LocalTonePlugin;
import com.android.server.telecom.R;

import com.android.server.telecom.CallsManagerListenerBase;
import com.android.server.telecom.CallsManager;


/**
 * Volte Local tone feature helper.
 */
public class LocalToneHelper extends CallsManagerListenerBase {
    private static final String TAG = "LocalToneHelper";

    static LocalToneHelper sInstance;

    public static LocalToneHelper getInstance(Context context) {
        if (sInstance == null) {
            if(context.getResources().getBoolean(R.bool.config_is_support_local_tone)) {
                sInstance = new LocalTonePlugin(context);
            } else {
                sInstance = new LocalToneHelper();
            }
        }
        Log.d(TAG, "getInstance [" + sInstance + "]");
        return sInstance;
    }

    public LocalToneHelper() {
    }

    public void init(Context context, CallsManager callsManager) {
        Log.i(TAG, "LocalToneHelper init.");
    }
    public synchronized void playRingBackTone() {
        Log.d(TAG, "playRingBackTone.");
    }
}
