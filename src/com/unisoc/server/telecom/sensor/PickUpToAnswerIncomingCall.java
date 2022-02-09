package com.unisoc.server.telecom.sensor;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import com.android.server.telecom.CallsManager;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallState;
import com.android.server.telecom.CallsManagerListenerBase;

import android.telecom.VideoProfile;

import android.hardware.Sensor;
import android.hardware.SprdSensor;
import android.hardware.SensorManager;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

public class PickUpToAnswerIncomingCall extends CallsManagerListenerBase
        implements SensorEventListener {

    private static final String TAG = "PickUpToAnswerIncomingCall";
    private Context mContext;
    private CallsManager mCallsManager;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private static PickUpToAnswerIncomingCall sInstance;

    public PickUpToAnswerIncomingCall(Context context) {
        mContext = context;
    }

    public static PickUpToAnswerIncomingCall getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PickUpToAnswerIncomingCall(context);
        }

        Log.d(TAG, "getInstance [" + sInstance + "]");
        return sInstance;
    }

    public void init(CallsManager callsManager) {
        mCallsManager = callsManager;

        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(SprdSensor.TYPE_SPRDHUB_HAND_UP);

        mCallsManager.addListener(this);
    }

    private boolean isPickUpToAnswerIncomingCallOn() {
        boolean isPickUpToAnswerIncomingCallOn = Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.EASY_ANSWER, 0) != 0;
        return isPickUpToAnswerIncomingCallOn;
    }

    @Override
    public void onCallAdded(Call call) {
        Call ringingCall = mCallsManager.getRingingCall();

        if (mSensor != null && ringingCall != null && isPickUpToAnswerIncomingCallOn()) {
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
        Call ringingCall = mCallsManager.getRingingCall();
        Call activeOrBackgroundCall = mCallsManager.getActiveOrBackgroundCall();

        if (activeOrBackgroundCall == null && ringingCall != null) {
            if (VideoProfile.isVideo(ringingCall.getVideoState())) {
                mCallsManager.answerCall(ringingCall, VideoProfile.STATE_BIDIRECTIONAL);
            } else {
                mCallsManager.answerCall(ringingCall, VideoProfile.STATE_AUDIO_ONLY);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

}