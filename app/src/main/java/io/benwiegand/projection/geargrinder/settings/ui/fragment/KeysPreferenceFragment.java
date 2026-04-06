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
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.crypto.CryptoManager;
import io.benwiegand.projection.geargrinder.exception.FileImportException;
import io.benwiegand.projection.geargrinder.exception.UserFriendlyException;
import io.benwiegand.projection.geargrinder.settings.ui.preference.FileImportPreference;
import io.benwiegand.projection.geargrinder.settings.ui.preference.PKCS8PrivateKeyImportPreference;
import io.benwiegand.projection.geargrinder.settings.ui.preference.X509CertificateChainImportPreference;
import io.benwiegand.projection.geargrinder.settings.ui.summary.PKCS8PrivateKeySummaryProvider;
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
        Preference selfSignedCertPref = findPreference(getString(R.string.key_self_signed_phone_x509_certificate_chain));
        Preference selfSignedKeyPref = findPreference(getString(R.string.key_self_signed_phone_pkcs8_private_key));
        X509CertificateChainImportPreference importedCertPref = findPreference(getString(R.string.key_imported_phone_x509_certificate_chain));
        PKCS8PrivateKeyImportPreference importedKeyPref = findPreference(getString(R.string.key_imported_phone_pkcs8_private_key));
        assert selfSignedCertPref != null;
        assert selfSignedKeyPref != null;
        assert importedCertPref != null;
        assert importedKeyPref != null;

        selfSignedCertPref.setSummaryProvider(new X509CertificateChainSummaryProvider(BASIC_CHECKS, null));
        importedCertPref.setSummaryProvider(new X509CertificateChainSummaryProvider(BASIC_CHECKS, List.of(CAR_SERVICE_CHECK)));

        Preference.SummaryProvider<Preference> privateKeySummaryProvider = new PKCS8PrivateKeySummaryProvider();
        selfSignedKeyPref.setSummaryProvider(privateKeySummaryProvider);
        importedKeyPref.setSummaryProvider(privateKeySummaryProvider);

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

        if (!importedKeysValid && useImportedKeysValue) {
            useImportedKeysPref.setIconSpaceReserved(true);
            useImportedKeysPref.setIcon(android.R.drawable.stat_notify_error);
            useImportedKeysPref.setEnabled(true);
        } else {
            useImportedKeysPref.setIconSpaceReserved(false);
            useImportedKeysPref.setIcon(null);
            useImportedKeysPref.setEnabled(importedKeysValid);
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

    private void onFileImport(FileImportPreference<?> basePreference, Uri uri) {
        try {
            String prefKey = basePreference.getKey();
            CryptoManager cryptoManager = new CryptoManager(requireContext());

            switch (basePreference) {
                case X509CertificateChainImportPreference preference -> {
                    X509Certificate[] certChain = preference.onFileSelected(uri);

                    try {
                        Log.i(TAG, "importing certificate chain: " + Arrays.deepToString(certChain));
                    } catch (Throwable t) {
                        Log.w(TAG, "failed to log certificate chain to import (certs = " + certChain.length + ")", t);
                    }

                    try {
                        if (requireContext().getString(R.string.key_imported_phone_x509_certificate_chain).equals(prefKey)) {
                            cryptoManager.importPhoneX509CertificateChain(certChain);
                        } else {
                            Log.wtf(TAG, "certificate import not handled for " + prefKey);
                            throw new AssertionError("certificate import not handled for preference: " + prefKey);
                        }
                    } catch (Throwable t) {
                        throw new FileImportException(requireContext(), R.string.file_import_error_invalid_cert, t);
                    }
                }
                case PKCS8PrivateKeyImportPreference preference -> {
                    PrivateKey privateKey = preference.onFileSelected(uri);

                    try {
                        Log.i(TAG, "importing private key: " + privateKey);
                    } catch (Throwable t) {
                        Log.w(TAG, "failed to log private key to import", t);
                    }

                    try {
                        if (requireContext().getString(R.string.key_imported_phone_pkcs8_private_key).equals(prefKey)) {
                            cryptoManager.importPhonePKCS8PrivateKey(privateKey);
                        } else {
                            Log.wtf(TAG, "private key import not handled for " + prefKey);
                            throw new AssertionError("private key import not handled for preference: " + prefKey);
                        }
                    } catch (Throwable t) {
                        throw new FileImportException(requireContext(), R.string.file_import_error_invalid_key, t);
                    }
                }
                case null, default -> throw new AssertionError("unhandled file import preference type");
            }

            basePreference.notifyChanged();

        } catch (UserFriendlyException e) {
            Log.e(TAG, "failed to import selected certificate chain: ", e);
            errorDialog(requireContext(), e).show();
        }
    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        if (preference instanceof FileImportPreference<?> fileImportPreference) {
            filePickerCallback = uri -> onFileImport(fileImportPreference, uri);
            filePickerLauncher.launch(fileImportPreference.getAcceptedMimeTypes());
            return;
        }

        super.onDisplayPreferenceDialog(preference);
    }

}
