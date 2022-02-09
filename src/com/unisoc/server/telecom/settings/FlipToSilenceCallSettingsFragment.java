package com.unisoc.server.settings;

import com.android.server.telecom.R;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.Settings;

public class FlipToSilenceCallSettingsFragment extends PreferenceFragment {

    private CheckBoxPreference mFlippingToSilence;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.silence_call_by_filpping_settings_ex);

        mFlippingToSilence = (CheckBoxPreference) findPreference(
                TelecommCallSettings.KEY_FLIP_TO_SLIENCE_CALL);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mFlippingToSilence) {
            Settings.Global.putInt(getActivity().getApplicationContext().getContentResolver(),
                    TelecommCallSettings.FLIPPING_SILENCE_DATA,
                    mFlippingToSilence.isChecked() ? 1 : 0);
            return true;
        }
        return true;
    }
}
