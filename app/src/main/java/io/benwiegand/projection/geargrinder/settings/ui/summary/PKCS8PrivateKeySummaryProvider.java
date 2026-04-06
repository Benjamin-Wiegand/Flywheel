package io.benwiegand.projection.geargrinder.settings.ui.summary;

import static io.benwiegand.projection.geargrinder.util.CryptoUtil.decodePKCS8PrivateKey;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import java.security.PrivateKey;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.settings.SettingsManager;

public class PKCS8PrivateKeySummaryProvider implements Preference.SummaryProvider<Preference> {
    @Nullable
    @Override
    public CharSequence provideSummary(@NonNull Preference preference) {
        Context context = preference.getContext();
        SettingsManager settingsManager = new SettingsManager(context);

        PrivateKey key;
        try {
            key = decodePKCS8PrivateKey(settingsManager.getPKCS8PrivateKey(preference.getKey()));
            if (key == null) return context.getString(R.string.no_item_pref_summary);
        } catch (Throwable t) {
            return context.getString(R.string.corrupted_item_pref_summary_format, t.toString());
        }

        return key.getAlgorithm();
    }
}
