package com.unisoc.server.plugins.WakeupScreen;

import android.content.Context;

import android.telecom.Log;

import com.android.server.telecom.R;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.CallsManagerListenerBase;
import com.unisoc.server.plugins.WakeupScreen.WakeupScreenPlugin;

/**
 * Wakeup screen when call disconnected.
 */

public class WakeupScreenHelper extends CallsManagerListenerBase {

    private static final String TAG = "WakeupScreenHelper";
    private Context mContext;
    static WakeupScreenHelper sInstance;

    public WakeupScreenHelper() {
    }

    public static WakeupScreenHelper getInstance(Context context) {
        if (sInstance == null) {
            if(context.getResources().getBoolean(R.bool.config_is_support_wakeup_screen)) {
                sInstance = new WakeupScreenPlugin();
            } else {
                sInstance = new WakeupScreenHelper();
            }
        }
        Log.d(TAG, "getInstance [" + sInstance + "]");
        return sInstance;
    }

    public void init(Context context, CallsManager callsManager) {
    }

}