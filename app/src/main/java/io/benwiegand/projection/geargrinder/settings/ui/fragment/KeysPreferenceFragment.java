package io.benwiegand.projection.geargrinder.settings.ui.fragment;

import static io.benwiegand.projection.geargrinder.settings.ui.summary.X509CertificateChainSummaryProvider.BASIC_CHECKS;
import static io.benwiegand.projection.geargrinder.settings.ui.summary.X509CertificateChainSummaryProvider.CAR_SERVICE_CHECK;
import static io.benwiegand.projection.geargrinder.util.UiUtil.errorDialog;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.function.Consumer;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.crypto.CryptoManager;
import io.benwiegand.projection.geargrinder.crypto.KeyWithChain;
import io.benwiegand.projection.geargrinder.exception.FileImportException;
import io.benwiegand.projection.geargrinder.exception.UserFriendlyException;
import io.benwiegand.projection.geargrinder.settings.ui.preference.LinkPreference;
import io.benwiegand.projection.geargrinder.settings.ui.preference.PemCertificateKeyBundleImportPreference;
import io.benwiegand.projection.geargrinder.settings.ui.summary.X509CertificateChainSummaryProvider;

public class KeysPreferenceFragment extends PreferenceFragmentCompat {
    private static final String TAG = KeysPreferenceFragment.class.getSimpleName();

    private SwitchPreferenceCompat useImportedKeysPref;

    private Consumer<Uri> filePickerCallback = ignored -> { throw new AssertionError("no callback"); };
    private final ActivityResultLauncher<String[]> filePickerLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), t -> filePickerCallback.accept(t));

    private SharedPreferences prefs;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_keys, rootKey);

        prefs = getPreferenceManager().getSharedPreferences();
        assert prefs != null;

        useImportedKeysPref = findPreference(getString(R.string.key_use_imported_phone_keys));
        Preference selfSignedKeysStatusPref = findPreference(getString(R.string.key_self_signed_phone_x509_certificate_chain));
        Preference importedKeysStatusPref = findPreference(getString(R.string.key_imported_phone_x509_certificate_chain));
        assert selfSignedKeysStatusPref != null;
        assert importedKeysStatusPref != null;

        selfSignedKeysStatusPref.setSummaryProvider(new X509CertificateChainSummaryProvider(BASIC_CHECKS, null));
        importedKeysStatusPref.setSummaryProvider(new X509CertificateChainSummaryProvider(BASIC_CHECKS, List.of(CAR_SERVICE_CHECK)));

        useImportedKeysPref.setOnPreferenceChangeListener((p, value) -> {
            updateUseImportedKeysPrefGuard((Boolean) value);
            return true;
        });
        updateUseImportedKeysPrefGuard();
    }

    private void updateUseImportedKeysPrefGuard(boolean useImportedKeysValue) {
        boolean importedKeysValid;
        try {
            CryptoManager cryptoManager = new CryptoManager(requireContext());
            importedKeysValid = cryptoManager.testImportedKeys();
            if (importedKeysValid) {
                useImportedKeysPref.setSummary(null);
            } else {
                useImportedKeysPref.setSummary(R.string.import_key_and_cert_first);
            }
        } catch (UserFriendlyException e) {
            Log.e(TAG, "imported keys don't work", e);
            importedKeysValid = false;
            useImportedKeysPref.setSummary(e.getFriendlyMessage());
        }

        if (!importedKeysValid) {
            useImportedKeysPref.setIconSpaceReserved(true);
            useImportedKeysPref.setIcon(android.R.drawable.stat_notify_error);
            useImportedKeysPref.setEnabled(false);
            useImportedKeysPref.setChecked(false);
        } else {
            useImportedKeysPref.setIconSpaceReserved(false);
            useImportedKeysPref.setIcon(null);
            useImportedKeysPref.setEnabled(true);
        }
    }

    private void updateUseImportedKeysPrefGuard() {
        updateUseImportedKeysPrefGuard(prefs.getBoolean(useImportedKeysPref.getKey(), false));
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUseImportedKeysPrefGuard();
    }

    private void onImportPemBundle(PemCertificateKeyBundleImportPreference preference, Uri uri) {
        try {
            KeyWithChain<PrivateKey, X509Certificate> keyWithChain = preference.onFileSelected(uri);
            Log.i(TAG, "importing certificates and keys: " + keyWithChain);

            CryptoManager cryptoManager = new CryptoManager(requireContext());

            try {
                cryptoManager.importPhoneX509CertificatePKCS8KeyBundle(keyWithChain);
            } catch (Throwable t) {
                throw new FileImportException(requireContext(), R.string.file_import_error_unexpected, t);
            }

            useImportedKeysPref.setChecked(true);

            preference.notifyChanged();
        } catch (UserFriendlyException e) {
            Log.e(TAG, "failed to import selected certificate chain: ", e);
            errorDialog(requireContext(), e).show();
        }
    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        switch (preference) {
            case PemCertificateKeyBundleImportPreference fileImportPreference -> {
                filePickerCallback = uri -> onImportPemBundle(fileImportPreference, uri);
                filePickerLauncher.launch(fileImportPreference.getAcceptedMimeTypes());
            }
            case LinkPreference linkPreference -> linkPreference.open(requireActivity());
            default -> super.onDisplayPreferenceDialog(preference);
        }

    }

}
