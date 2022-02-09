package com.unisoc.server.telecom;

import com.android.server.telecom.Call;
import com.android.server.telecom.CallState;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.CallsManagerListenerBase;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.telecom.Log;
import android.telephony.CarrierConfigManager;
import android.telephony.CarrierConfigManagerEx;

public class InCallScreenOffControllerEx extends CallsManagerListenerBase {

    private Context mContext;
    private CallsManager mCallsManager;
    private CarrierConfigManager mConfigManager;
    private static final int MSG_SCREEN_OFF_WHEN_CALL_IS_ACTIVE = 9;
    private static final int DELAY_BEFORE_SENDING_MSEC = 5000;

    static InCallScreenOffControllerEx sInstance;
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SCREEN_OFF_WHEN_CALL_IS_ACTIVE:
                    goToSleepForActiveCall();
                    break;
            }
        }
    };

    InCallScreenOffControllerEx() {
    }

    public static InCallScreenOffControllerEx getInstance() {
        if (sInstance == null) {
            sInstance = new InCallScreenOffControllerEx();
        }
        return sInstance;
    }

    public void init(Context context, CallsManager callsManager) {
        mContext = context;
        mCallsManager = callsManager;

        mConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);

        if (!isProximitySensorAvailable() && mConfigManager.getConfigForDefaultPhone() != null
                && mConfigManager.getConfigForDefaultPhone().getBoolean(
                        CarrierConfigManagerEx.KEY_SCREEN_OFF_IN_ACTIVE_CALL_STATE_BOOL)) {
            mCallsManager.addListener(this);
        }
    }

    @Override
    public void onCallStateChanged(Call call, int oldState, int newState) {
        Call activeCall = mCallsManager.getActiveCall();
        if (((oldState == CallState.RINGING || oldState == CallState.DIALING
                || oldState == CallState.ON_HOLD) && newState == CallState.ACTIVE)
                || (activeCall != null && oldState == CallState.RINGING
                && newState == CallState.DISCONNECTED)) {
            handleScreenOff();
        }
    }

    private boolean isProximitySensorAvailable() {
        SensorManager sensorManager = (SensorManager) mContext
                .getSystemService(Context.SENSOR_SERVICE);
        Sensor proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        return proximitySensor != null;
    }

    private void goToSleepForActiveCall() {
        PowerManager powerManager = (PowerManager) mContext
                .getSystemService(Context.POWER_SERVICE);
        powerManager.goToSleep(SystemClock.uptimeMillis());
    }

    private void handleScreenOff() {
        Log.i(this, "turn off the screen 5s after the call in active state");
        mHandler.removeMessages(MSG_SCREEN_OFF_WHEN_CALL_IS_ACTIVE);
        Message msg = mHandler.obtainMessage(MSG_SCREEN_OFF_WHEN_CALL_IS_ACTIVE);
        mHandler.sendMessageDelayed(msg, DELAY_BEFORE_SENDING_MSEC);
    }
}
