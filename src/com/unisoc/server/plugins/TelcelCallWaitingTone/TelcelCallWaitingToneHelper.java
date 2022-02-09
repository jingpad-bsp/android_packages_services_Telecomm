package com.unisoc.server.plugins.TelcelCallWaitingTone;

import android.content.Context;
import android.media.AudioManager;

import com.android.server.telecom.R;
import android.telecom.Log;
import com.unisoc.server.plugins.TelcelCallWaitingTone.CallWaitingTonePlugin;

/**
 * Handle telcel  operator requirement: customized call waiting tone.
 */
public class TelcelCallWaitingToneHelper {
    private static final String LOG_TAG = "TelcelCallwaitingToneHelper";
    private static TelcelCallWaitingToneHelper sInstance;

    public TelcelCallWaitingToneHelper() {
    }

    public static TelcelCallWaitingToneHelper getInstance(Context context) {
        if (sInstance == null) {
            if(context.getResources().getBoolean(R.bool.config_is_support_dtmf_tone)) {
                sInstance = new CallWaitingTonePlugin(context);
            } else {
                sInstance = new TelcelCallWaitingToneHelper();
            }
        }
        Log.d(LOG_TAG, "getInstance : " + sInstance);
        return sInstance;

    }

    public void stop3rdCallWaitingTone() {
    }

    public void play3rdCallWaitingTone() {
    }

    public boolean is3rdCallWaitingToneSupport() {
        return false;
    }
}
