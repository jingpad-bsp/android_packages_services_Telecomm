package com.unisoc.server.settings;

import com.android.server.telecom.R;

import android.app.ActionBar;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

public class TelecommCallSettings extends PreferenceActivity {

    public static final String SHARED_PREFERENCES_NAME = "com.android.server.telecom_preferences";
    public static final String KEY_FLIP_TO_SLIENCE_CALL = "silent_call_by_flipping_key";

    public static final String FLIPPING_SILENCE_DATA = "flipping_silence_data";
    public static final String KEY_FADE_IN_RINGER = "fade_in_key";
    public static final String FADE_IN_ON = "fade_in_on";
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            if (getIntent().getFlags() == 0) {
                actionBar.setTitle(R.string.incomingcall_flipping_silence_title);
                getFragmentManager()
                        .beginTransaction()
                        .replace(android.R.id.content,
                                new FlipToSilenceCallSettingsFragment()).commit();
            } else {
                actionBar.setTitle(R.string.fade_in_title);
                getFragmentManager()
                        .beginTransaction()
                        .replace(android.R.id.content,
                                new FadeInSettingsFragment()).commit();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
