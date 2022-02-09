package com.unisoc.server.telecom.sensor;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import com.android.server.telecom.CallsManager;
import com.android.server.telecom.CallAudioManager;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallState;
import com.android.server.telecom.CallsManagerListenerBase;

import android.telephony.TelephonyManager;

import android.hardware.Sensor;
import android.hardware.SprdSensor;
import android.hardware.SensorManager;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

public class FlipToMute extends CallsManagerListenerBase
        implements SensorEventListener {

    private static final String TAG = "FlipToMute";
    private Context mContext;
    private CallsManager mCallsManager;
    private CallAudioManager mCallAudioManager;
    private SensorManager mSensorManager;
    private Sensor mSensor;

    private static FlipToMute sInstance;

    public FlipToMute(Context context) {
        mContext = context;
    }

    public static FlipToMute getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new FlipToMute(context);
        }

        Log.d(TAG, "getInstance [" + sInstance + "]");
        return sInstance;
    }

    public void init(CallsManager callsManager, CallAudioManager callAudioManager) {
        mCallsManager = callsManager;
        mCallAudioManager = callAudioManager;

        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(SprdSensor.TYPE_SPRDHUB_FLIP);

        callsManager.addListener(this);

    }

    private boolean isFlipToMuteOn() {
        boolean isFlipToMuteOn = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.MUTE_INCOMING_CALLS, 0) != 0;
        return isFlipToMuteOn;
    }

    @Override
    public void onCallAdded(Call call) {
        Call ringingCall = mCallsManager.getRingingCall();

        if (mSensor != null && ringingCall != null && isFlipToMuteOn()) {
            Log.d(TAG, "registerListener.");
            mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onCallStateChanged(Call call, int oldState, int newState) {

        if (mSensor != null && oldState == CallState.RINGING && newState != CallState.RINGING) {
            Log.d(TAG, "unregisterListener.");
            mSensorManager.unregisterListener(this, mSensor);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Log.d(TAG, "onSensorChanged.");
        if (isFlipToMuteOn()) {  //UNISOC:add for bug961707
            if ((int) (event.values[2]) == 2 || (event.values[2])== 1) {
                if (mCallsManager.getCallState() == TelephonyManager.CALL_STATE_RINGING) {
                    mCallAudioManager.silenceRingers();
                    if (mSensor != null) {
                        mSensorManager.unregisterListener(this, mSensor);
                    }
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
