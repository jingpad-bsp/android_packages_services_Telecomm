package com.unisoc.server.plugins.TelcelCallWaitingTone;

import android.content.Context;
import android.media.SoundPool;
import android.media.AudioManager;

import com.android.server.telecom.R;

import com.unisoc.server.plugins.TelcelCallWaitingTone.TelcelCallWaitingToneHelper;


public class CallWaitingTonePlugin extends TelcelCallWaitingToneHelper {

    private Context mContext;
    private SoundPool mCallWaitingToneSoundPool;

    public CallWaitingTonePlugin(Context context) {
        mContext = context;
    }

    public void stop3rdCallWaitingTone() {
        if (mCallWaitingToneSoundPool != null) {
            mCallWaitingToneSoundPool.stop(1);
            mCallWaitingToneSoundPool.unload(1);
            mCallWaitingToneSoundPool.release();
            mCallWaitingToneSoundPool = null;
        }
    }

    public void play3rdCallWaitingTone() {
        if (mCallWaitingToneSoundPool == null) {
            int stream = AudioManager.STREAM_VOICE_CALL;
            mCallWaitingToneSoundPool = new SoundPool(10, stream, 0);

            mCallWaitingToneSoundPool.load(mContext, R.raw.call_waiting_tone, 1);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
            mCallWaitingToneSoundPool.play(1, 1, 1, 0, -1, 1);
        }
    }


    public boolean is3rdCallWaitingToneSupport() {
        return true;
    }
}
