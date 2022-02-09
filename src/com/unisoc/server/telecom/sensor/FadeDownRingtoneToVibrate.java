package com.unisoc.server.telecom.sensor;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import com.android.server.telecom.CallsManager;
import com.android.server.telecom.Ringer;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallState;
import com.android.server.telecom.CallsManagerListenerBase;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;

public class FadeDownRingtoneToVibrate extends CallsManagerListenerBase {

    private static final String TAG = "FadeDownRingtoneToVibrate";
    private Context mContext;
    private CallsManager mCallsManager;
    private Ringer mRinger;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private TriggerListener mListener;
    private static FadeDownRingtoneToVibrate sInstance;

    public FadeDownRingtoneToVibrate(Context context) {
        mContext = context;
    }

    public static FadeDownRingtoneToVibrate getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new FadeDownRingtoneToVibrate(context);
        }

        Log.d(TAG, "getInstance [" + sInstance + "]");
        return sInstance;
    }

    public void init(CallsManager callsManager, Ringer ringer) {
        mCallsManager = callsManager;
        mRinger = ringer;

        mListener = new TriggerListener(ringer);
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PICK_UP_GESTURE);

        callsManager.addListener(this);
    }

    private boolean isFadedownRingtoneToVibrateOn() {
        boolean isFadedownRingtoneToVibrateOn =
        Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.EASY_BELL, 0) != 0;
        return isFadedownRingtoneToVibrateOn;
    }

    @Override
    public void onCallAdded(Call call) {
        Call ringingCall = mCallsManager.getRingingCall();

        Log.i(TAG, "onCallAdded" + (mSensor != null));

        if (isFadedownRingtoneToVibrateOn() && mSensor != null && ringingCall != null) {
            Log.d(TAG, "requestTriggerSensor.");
            mSensorManager.requestTriggerSensor(mListener, mSensor);
        }
    }

    @Override
    public void onCallStateChanged(Call call, int oldState, int newState) {

        if (mSensor != null && oldState == CallState.RINGING && newState != CallState.RINGING) {
            Log.d(TAG, "cancelTriggerSensor.");
            mSensorManager.cancelTriggerSensor(mListener, mSensor);
        }
    }

}

class TriggerListener extends TriggerEventListener {
    private static final String TAG = "FadeDownRingtoneToVibrate";
    private Ringer mRinger;

    TriggerListener(Ringer ringer) {
        mRinger = ringer;
    }

    @Override
    public void onTrigger(TriggerEvent event) {
        // Sensor is auto disabled.
        Log.d(TAG, "onTrigger.");
        mRinger.fadeDownRingtone();
    }
}
