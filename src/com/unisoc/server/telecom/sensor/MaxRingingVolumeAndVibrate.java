package com.unisoc.server.telecom.sensor;

import android.content.Context;
import android.provider.Settings;
import android.media.AudioManager;
import android.util.Log;

import com.android.server.telecom.CallsManager;
import com.android.server.telecom.Ringer;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallState;
import com.android.server.telecom.CallsManagerListenerBase;

import android.hardware.Sensor;
import android.hardware.SprdSensor;
import android.hardware.SensorManager;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

public class MaxRingingVolumeAndVibrate extends CallsManagerListenerBase
        implements SensorEventListener {

    private static final String TAG = "MaxRingingVolumeAndVibrate";
    private Context mContext;
    private CallsManager mCallsManager;
    private Ringer mRinger;
    private SensorManager mSensorManager;
    private Sensor mSensor;

    private static MaxRingingVolumeAndVibrate sInstance;

    public MaxRingingVolumeAndVibrate(Context context) {
        mContext = context;
    }

    public static MaxRingingVolumeAndVibrate getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new MaxRingingVolumeAndVibrate(context);
        }

        Log.d(TAG, "getInstance [" + sInstance + "]");
        return sInstance;
    }

    public void init(CallsManager callsManager, Ringer ringer) {
        Log.i(TAG, " MaxRingingVolumeAndVibratePlugin _init");
        mCallsManager = callsManager;
        mRinger = ringer;

        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(SprdSensor.TYPE_SPRDHUB_POCKET_MODE);

        callsManager.addListener(this);
    }

    private boolean isMaxRingingVolumeAndVibrateOn() {
        boolean isMaxRingingVolumeAndVibrateOn = Settings.Global.getInt(  //modify for bug961699
                mContext.getContentResolver(), Settings.Global.SMART_BELL, 0) != 0;
        Log.i(TAG, " isMaxRingingVolumeAndVibrateOn :"+isMaxRingingVolumeAndVibrateOn);
        return isMaxRingingVolumeAndVibrateOn;
    }

    @Override
    public void onCallAdded(Call call) {
        Call ringingCall = mCallsManager.getRingingCall();
        Call activeCall = mCallsManager.getActiveCall();
        Call heldCall = mCallsManager.getHeldCall();
        if (mSensor != null && ringingCall != null
                && activeCall == null && heldCall == null && isMaxRingingVolumeAndVibrateOn()) {
            Log.d(TAG, "registerListener.");
            mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onCallStateChanged(Call call, int oldState, int newState) {

        if (mSensor != null && oldState == CallState.RINGING && newState != CallState.RINGING) {
            Log.d(TAG, "unregisterListener.");
            mSensorManager.unregisterListener(this, mSensor);
            //add for bug1376609
            mRinger.stopMaxRingingVolume();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Log.d(TAG, "onSensorChanged.");
        if (event != null && event.values[0] == 1) {
            AudioManager audioManager =
                    (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager.getStreamVolume(AudioManager.STREAM_RING) > 0) {
                mRinger.maxRingingVolume();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

}