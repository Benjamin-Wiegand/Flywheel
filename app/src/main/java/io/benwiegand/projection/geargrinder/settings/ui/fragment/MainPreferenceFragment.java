package io.benwiegand.projection.geargrinder.settings.ui.fragment;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.permission.PermissionRequirements;
import io.benwiegand.projection.geargrinder.settings.PrivilegeMode;

public class MainPreferenceFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_settings, rootKey);

        ListPreference privilegeModePreference = findPreference(getString(R.string.key_privilege_mode));
        if (privilegeModePreference != null) privilegeModePreference
                .setOnPreferenceChangeListener((pref, newValue) -> {
                    PrivilegeMode newMode = PrivilegeMode.parse(requireContext(), (String) newValue);
                    switch (newMode) {
                        case NO_ROOT -> {}
                        case SHIZUKU -> PermissionRequirements.SHIZUKU_PERMISSION_ENTRY.request().accept(requireActivity());
                        case ROOT -> PermissionRequirements.ROOT_PERMISSION_ENTRY.request().accept(requireActivity());
                    }
                    return true;
                });
    }
}
