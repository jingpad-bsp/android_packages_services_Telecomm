/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.telecom;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.VolumeShaper;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.Settings;//add for bug1154641
import android.telecom.Log;
import android.content.SharedPreferences;
import com.unisoc.server.settings.TelecommCallSettings;
/* Unisoc FL0108020016: MaxRingingVolume and Vibrate. @{ */
import android.os.SystemVibrator;
import android.os.Vibrator;
import android.media.AudioManager;
import android.content.Context;
/* @} */

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.SomeArgs;
import com.android.internal.util.Preconditions;

import java.util.concurrent.CompletableFuture;

/**
 * Plays the default ringtone. Uses {@link Ringtone} in a separate thread so that this class can be
 * used from the main thread.
 */
@VisibleForTesting
public class AsyncRingtonePlayer {
    // Message codes used with the ringtone thread.
    private static final int EVENT_PLAY = 1;
    private static final int EVENT_STOP = 2;
    private static final int EVENT_REPEAT = 3;
    // UNISOC: Add for fade-in feature.
    private static final int EVENT_FADE_IN = 4;
    // Unisoc FL0108020015: Fade down ringtone to vibrate.
    private static final int EVENT_FADEDOWN_RINGTONE = 5;

    // The interval in which to restart the ringer.
    private static final int RESTART_RINGER_MILLIS = 3000;

    /** Handler running on the ringtone thread. */
    private Handler mHandler;

    /** The current ringtone. Only used by the ringtone thread. */
    private Ringtone mRingtone;

    /**
     * CompletableFuture which signals a caller when we know whether a ringtone will play haptics
     * or not.
     */
    private CompletableFuture<Boolean> mHapticsFuture = null;

    /**
     * Determines if the {@link AsyncRingtonePlayer} should pause between repeats of the ringtone.
     * When {@code true}, the system will check if the ringtone has stopped every
     * {@link #RESTART_RINGER_MILLIS} and restart the ringtone if it has stopped.  This does not
     * guarantee that there is {@link #RESTART_RINGER_MILLIS} between each repeat of the ringtone,
     * rather it ensures that for short ringtones, or ringtones which are not a multiple of
     * {@link #RESTART_RINGER_MILLIS} in duration that there will be some pause between repetitions.
     * When {@code false}, the ringtone will be looped continually with no attempt to pause between
     * repeats.
     */
    private boolean mShouldPauseBetweenRepeat = true;

    /* Unisoc FL0108020016: MaxRingingVolume and Vibrate. @{ */
    private boolean mIsMaxRingingVolumeOn = false;
    private boolean mIsVibrating = false;
    private Vibrator mVibrator;
    private SystemSettingsUtil mSystemSettingsUtil;
    private int mCanVibrateWhenRinging = -1;
    private int mVolumeIndex;

    // Indicate that we want the pattern to repeat at the step which turns on vibration.
    private static final int VIBRATION_PATTERN_REPEAT = 1;
    private static final long[] VIBRATION_PATTERN = new long[]{
            0,    // No delay before starting
            1000, // How long to vibrate
            1000, // How long to wait before vibrating again
    };
    // UNISOC Feature Porting: Fade in ringer volume when incoming calls.
    private float mVolume = 0f;

    public AsyncRingtonePlayer(Context context) {
        mContext = context;
    }
    private static final AudioAttributes VIBRATION_ATTRIBUTES = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .build();
    /* @} */

    /* Unisoc FL0108020015: Fade down ringtone to vibrate. */
    private Context mContext;
    /* @} */

    public AsyncRingtonePlayer() {
        // Empty
    }

    public AsyncRingtonePlayer(boolean shouldPauseBetweenRepeat, Context context) {
        mContext = context;
        mShouldPauseBetweenRepeat = shouldPauseBetweenRepeat;
        // UNISOC: add for bug937935
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mSystemSettingsUtil = new SystemSettingsUtil();
    }

    /**
     * Plays the appropriate ringtone for the specified call.
     * If {@link VolumeShaper.Configuration} is specified, it is applied to the ringtone to change
     * the volume of the ringtone as it plays.
     *
     * @param factory The {@link RingtoneFactory}.
     * @param incomingCall The ringing {@link Call}.
     * @param volumeShaperConfig An optional {@link VolumeShaper.Configuration} which is applied to
     *                           the ringtone to change its volume while it rings.
     * @param isVibrationEnabled {@code true} if the settings and DND configuration of the device
     *                           is such that the vibrator should be used, {@code false} otherwise.
     * @return A {@link CompletableFuture} which on completion indicates whether or not the ringtone
     *         has a haptic track.  {@code True} indicates that a haptic track is present on the
     *         ringtone; in this case the default vibration in {@link Ringer} should not be played.
     *         {@code False} indicates that a haptic track is NOT present on the ringtone;
     *         in this case the default vibration in {@link Ringer} should be trigger if needed.
     */
    public @NonNull CompletableFuture<Boolean> play(RingtoneFactory factory, Call incomingCall,
            @Nullable VolumeShaper.Configuration volumeShaperConfig, boolean isVibrationEnabled) {
        Log.d(this, "Posting play.");
        if (mHapticsFuture == null) {
            mHapticsFuture = new CompletableFuture<>();
        }
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = factory;
        args.arg2 = incomingCall;
        args.arg3 = volumeShaperConfig;
        args.arg4 = isVibrationEnabled;
        postMessage(EVENT_PLAY, true /* shouldCreateHandler */, args);
        return mHapticsFuture;
    }

    /** Stops playing the ringtone. */
    public void stop() {
        Log.d(this, "Posting stop.");
        postMessage(EVENT_STOP, false /* shouldCreateHandler */, null);
    }

    /* Unisoc FL0108020016: MaxRingingVolume and Vibrate. @{ */
    void handleMaxRingingVolume(Context context) {
        Log.i(this, "maxRingingVolume.");
        mIsMaxRingingVolumeOn = true;
        if (!mIsVibrating) { //UNISOC:add for bug1283273 1376609
            if (!mSystemSettingsUtil.canVibrateWhenRinging(context)) {
                mSystemSettingsUtil.setVibrateWhenRinging(context,1);
                mCanVibrateWhenRinging = 0;
            }
            mVibrator.vibrate(VIBRATION_PATTERN, VIBRATION_PATTERN_REPEAT,
                    VIBRATION_ATTRIBUTES);
            mIsVibrating = true;
        }
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mVolumeIndex = audioManager.getStreamVolume(AudioManager.STREAM_RING);
        audioManager.setStreamVolume(AudioManager.STREAM_RING, 7,0);
    }
    /* @} */

    // UNISOC: add for bug937206
    void stopMaxRingingVolume(Context context) {
        Log.i(this, "stopMaxRingingVolume.");
        if (mIsMaxRingingVolumeOn) {
            AudioManager audioManager = (AudioManager) context.getSystemService(
                    Context.AUDIO_SERVICE);
            audioManager.setStreamVolume(AudioManager.STREAM_RING, mVolumeIndex, 0);
            mIsMaxRingingVolumeOn = false;
        }
    }

    /**
     * Posts a message to the ringtone-thread handler. Creates the handler if specified by the
     * parameter shouldCreateHandler.
     *
     * @param messageCode The message to post.
     * @param shouldCreateHandler True when a handler should be created to handle this message.
     */
    private void postMessage(int messageCode, boolean shouldCreateHandler, SomeArgs args) {
        synchronized(this) {
            if (mHandler == null && shouldCreateHandler) {
                mHandler = getNewHandler();
            }

            if (mHandler == null) {
                Log.d(this, "Message %d skipped because there is no handler.", messageCode);
                /*UNISOC: add for bug1283273 */
                if (mIsVibrating && messageCode == EVENT_STOP) {
                    Log.i(this, "handler is null, stop the vibration.");
                    mVibrator.cancel();
                    mIsVibrating = false;
                    if (mCanVibrateWhenRinging > -1) {
                        mSystemSettingsUtil.setVibrateWhenRinging(mContext, mCanVibrateWhenRinging);
                        mCanVibrateWhenRinging = -1;
                    }
                }
            } else {
                mHandler.obtainMessage(messageCode, args).sendToTarget();
            }
        }
    }

    /**
     * Creates a new ringtone Handler running in its own thread.
     */
    private Handler getNewHandler() {
        Preconditions.checkState(mHandler == null);

        HandlerThread thread = new HandlerThread("ringtone-player");
        thread.start();

        return new Handler(thread.getLooper(), null /*callback*/, true /*async*/) {
            // Unisoc FL0108020015: Fade down ringtone to vibrate.
            float mCurrentVolume = 1.0f;
            @Override
            public void handleMessage(Message msg) {
                switch(msg.what) {
                    case EVENT_PLAY:
                        handlePlay((SomeArgs) msg.obj);
                        break;
                    case EVENT_REPEAT:
                        handleRepeat();
                        break;
                    case EVENT_STOP:
                        handleStop();
                        break;
                    /* UNISOC Feature Porting: Fade in ringer volume when incoming calls. */
                    case EVENT_FADE_IN:
                        mVolume += 0.05f;
                        if (mVolume < 1f && mRingtone != null) {
                            mRingtone.setVolume(mVolume);
                            synchronized (AsyncRingtonePlayer.this){//add for bug1158476
                                mHandler.sendEmptyMessageDelayed(EVENT_FADE_IN, 600);// UNISOC: modify for bug1154641
                            }
                        }
                        break;
                    /* @} */
                    /* @} */
                    /* Unisoc FL0108020015: Fade down ringtone to vibrate. */
                    case EVENT_FADEDOWN_RINGTONE:
                        mCurrentVolume -= .05f;
                        if (mCurrentVolume > .05f) {
                            mHandler.sendEmptyMessageDelayed(EVENT_FADEDOWN_RINGTONE, 500);
                        } else if (!mIsVibrating) {
                            mCurrentVolume = 0f;
                            if (!mSystemSettingsUtil.canVibrateWhenRinging(mContext)) {
                                mCanVibrateWhenRinging = 0;
                                mSystemSettingsUtil.setVibrateWhenRinging(mContext, 1);
                            }
                            mVibrator.vibrate(VIBRATION_PATTERN, VIBRATION_PATTERN_REPEAT,
                                    VIBRATION_ATTRIBUTES);
                            mIsVibrating = true;
                        }
                        if (mRingtone != null) {
                            mRingtone.setVolume(mCurrentVolume);
                        }
                        break;
                    /* @} */
                }
            }
        };
    }

    /**
     * Starts the actual playback of the ringtone. Executes on ringtone-thread.
     */
    private void handlePlay(SomeArgs args) {
        RingtoneFactory factory = (RingtoneFactory) args.arg1;
        Call incomingCall = (Call) args.arg2;
        VolumeShaper.Configuration volumeShaperConfig = (VolumeShaper.Configuration) args.arg3;
        boolean isVibrationEnabled = (boolean) args.arg4;
        args.recycle();
        // don't bother with any of this if there is an EVENT_STOP waiting.
        if (mHandler.hasMessages(EVENT_STOP)) {
            mHapticsFuture.complete(false /* ringtoneHasHaptics */);
            mHapticsFuture = null;
            return;
        }

        // If the Ringtone Uri is EMPTY, then the "None" Ringtone has been selected. Do not play
        // anything.
        if(Uri.EMPTY.equals(incomingCall.getRingtone())) {
            mRingtone = null;
            mHapticsFuture.complete(false /* ringtoneHasHaptics */);
            mHapticsFuture = null;
            return;
        }

        ThreadUtil.checkNotOnMainThread();
        Log.i(this, "handlePlay: Play ringtone.");

        if (mRingtone == null) {
            mRingtone = factory.getRingtone(incomingCall, volumeShaperConfig);
            if (mRingtone == null) {
                Uri ringtoneUri = incomingCall.getRingtone();
                String ringtoneUriString = (ringtoneUri == null) ? "null" :
                        ringtoneUri.toSafeString();
                Log.addEvent(null, LogUtils.Events.ERROR_LOG, "Failed to get ringtone from " +
                        "factory. Skipping ringing. Uri was: " + ringtoneUriString);
                mHapticsFuture.complete(false /* ringtoneHasHaptics */);
                mHapticsFuture = null;
                return;
            }

            // With the ringtone to play now known, we can determine if it has haptic channels or
            // not; we will complete the haptics future so the default vibration code in Ringer
            // can know whether to trigger the vibrator.
            if (mHapticsFuture != null && !mHapticsFuture.isDone()) {
                boolean hasHaptics = factory.hasHapticChannels(mRingtone);

                Log.i(this, "handlePlay: hasHaptics=%b, isVibrationEnabled=%b", hasHaptics,
                        isVibrationEnabled);
                if (hasHaptics) {
                    AudioAttributes attributes = mRingtone.getAudioAttributes();
                    Log.d(this, "handlePlay: %s haptic channel",
                            (isVibrationEnabled ? "unmuting" : "muting"));
                    mRingtone.setAudioAttributes(
                            new AudioAttributes.Builder(attributes)
                                    .setHapticChannelsMuted(!isVibrationEnabled)
                                    .build());
                }
                mHapticsFuture.complete(hasHaptics);
                mHapticsFuture = null;
            }
        }

        if (mShouldPauseBetweenRepeat) {
            // We're trying to pause between repeats, so the ringtone will not intentionally loop.
            // Instead, we'll use a handler message to perform repeats.
            handleRepeat();
        } else {
            mRingtone.setLooping(true);
            if (mRingtone.isPlaying()) {
                Log.d(this, "Ringtone already playing.");
                return;
            }
            mRingtone.play();
            Log.i(this, "Play ringtone, looping.");
        }
        /*UNISOC Feature Porting: Fade in ringer volume when incoming calls. @{ */
        if (mRingtone != null) {
            handleFadeIn();
        }
        /* @} */
    }

    private void handleRepeat() {
        if (mRingtone == null) {
            return;
        }
        if (mRingtone.isPlaying()) {
            Log.d(this, "Ringtone already playing.");
        } else {
            mRingtone.play();
            Log.i(this, "Repeat ringtone.");
        }

        // Repost event to restart ringer in {@link RESTART_RINGER_MILLIS}.
        synchronized(this) {
            if (!mHandler.hasMessages(EVENT_REPEAT)) {
                mHandler.sendEmptyMessageDelayed(EVENT_REPEAT, RESTART_RINGER_MILLIS);
            }
        }
    }

    /**
     * Stops the playback of the ringtone. Executes on the ringtone-thread.
     */
    private void handleStop() {
        ThreadUtil.checkNotOnMainThread();
        Log.i(this, "Stop ringtone.");

        if (mRingtone != null) {
            Log.d(this, "Ringtone.stop() invoked.");
            mRingtone.stop();
            mRingtone = null;
        }
        // UNISOC Feature Porting: Fade in ringer volume when incoming calls.
        mVolume = 0f;
        /* Unisoc FL0108020015: Fade down ringtone to vibrate. @{ */
        if (mIsVibrating) {
            mVibrator.cancel();
            mIsVibrating = false;
            if (mCanVibrateWhenRinging > -1) {
                mSystemSettingsUtil.setVibrateWhenRinging(mContext, mCanVibrateWhenRinging);
                mCanVibrateWhenRinging = -1;
            }
        }
        /* @} */

        synchronized(this) {
            // At the time that STOP is handled, there should be no need for repeat messages in the
            // queue.
            mHandler.removeMessages(EVENT_REPEAT);

            if (mHandler.hasMessages(EVENT_PLAY)) {
                Log.v(this, "Keeping alive ringtone thread for subsequent play request.");
            } else {
                // Unisoc FL0108020015: Fade down ringtone to vibrate.
                mHandler.removeMessages(EVENT_FADEDOWN_RINGTONE);
               // UNISOC Feature Porting: Fade in ringer volume when incoming calls.
                mHandler.removeMessages(EVENT_FADE_IN);
                mHandler.removeMessages(EVENT_STOP);
                mHandler.getLooper().quitSafely();
                mHandler = null;
                Log.v(this, "Handler cleared.");
            }
        }
    }
    /* Unisoc FL0108020015: Fade down ringtone to vibrate. @{ */
    void fadeDownRingtone(Context context) {
        Log.d(this, "fadeDownRingtone.");
        postMessage(EVENT_FADEDOWN_RINGTONE, false /* shouldCreateHandler */, null);
    }
    /* @} */
    /* UNISOC Feature Porting: Fade in ringer volume when incoming calls. @{ */
    private void handleFadeIn() {
        if (isFeatrueFlipToSilenceEnabled() && mRingtone != null) {
            Log.i(this, "fade-in.");
            mRingtone.setVolume(0.05f);
            postMessage(EVENT_FADE_IN, false /* shouldCreateHandler */, null);
        }
    }

    private boolean isFeatrueFlipToSilenceEnabled() {
        // UNISOC: modify for bug1154641
        boolean isFadeInEnabled = Settings.Global.getInt(mContext.getContentResolver(),
                TelecommCallSettings.FADE_IN_ON, 0) != 0;
        return isFadeInEnabled;
    }
    /* @} */
}
