package com.unisoc.server.plugins.WakeupScreen;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import com.android.server.telecom.R;
import com.unisoc.server.plugins.WakeupScreen.WakeupScreenHelper;

import com.android.server.telecom.CallsManager;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallState;

public class WakeupScreenPlugin extends WakeupScreenHelper {

    private static final String TAG = "WakeupScreenPlugin";
    private Context mContext;

    public WakeupScreenPlugin() {

    }

    public void init(Context context, CallsManager callsManager) {
        mContext = context;
        callsManager.addListener(this);
    }
    //UNISOC:add for bug1037823
    private boolean isZENMODE(){
        final int zen = Settings.Global.getInt(mContext.getContentResolver(),Settings.Global.ZEN_MODE, Settings.Global.ZEN_MODE_OFF);
        switch (zen) {
            case Settings.Global.ZEN_MODE_NO_INTERRUPTIONS:
            case Settings.Global.ZEN_MODE_ALARMS:
            case Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onCallStateChanged(Call call, int oldState, int newState) {
        if (newState == CallState.DISCONNECTED && oldState != CallState.DISCONNECTED) {
            PowerManager powerManager = (PowerManager) mContext
                    .getSystemService(Context.POWER_SERVICE);
            //UNISOC:add for bug1037823
            Log.d(TAG, "isZENMODE:"+isZENMODE());
            if (!powerManager.isScreenOn() && !isZENMODE()) {
                Log.d(TAG, "onCallStateChanged, wake up screen.");
                powerManager.wakeUp(SystemClock.uptimeMillis());
            }
        }
    }
}
