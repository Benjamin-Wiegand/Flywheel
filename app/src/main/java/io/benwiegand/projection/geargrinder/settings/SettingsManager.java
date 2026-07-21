package io.benwiegand.projection.geargrinder.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.StringRes;

import java.util.List;

import io.benwiegand.projection.geargrinder.R;

public class SettingsManager {
    private static final String TAG = SettingsManager.class.getSimpleName();
    private static final String PREFERENCE_NAME = "io.benwiegand.projection.geargrinder_preferences";
    private static final String RAW_BYTES_MULTI_SEPARATOR = ",";

    public static final int LIABILITY_AGREEMENT_VERSION_CURRENT = 1;
    public static final int LIABILITY_AGREEMENT_VERSION_NONE = 0;

    public static final int SETUP_VERSION_CURRENT = 1;
    public static final int SETUP_VERSION_NONE = 0;

    private final Context context;
    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        this.context = context;
        prefs = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    public OperationalMode getOperationalMode() {
        return OperationalMode.parse(context, getString(R.string.key_operational_mode));
    }

    public PrivilegeMode getPrivilegeMode() {
        return PrivilegeMode.parse(context, getString(R.string.key_privilege_mode));
    }

    public boolean savePrivilegeMode(PrivilegeMode privilegeMode) {
        return saveString(R.string.key_privilege_mode, context.getString(privilegeMode.preferenceValueRes()));
    }

    public boolean allowsStartProjectionWhenLocked() {
        return getBool(R.string.key_start_projection_when_locked, R.string.start_projection_when_locked_default);
    }

    public int getProjectionResumeGracePeriod() {
        return castInt(R.string.key_projection_resume_grace_period, R.string.projection_grace_period_default);
    }

    public int getVideoBitrateMode() {
        return castInt(R.string.key_video_bitrate_mode, R.string.video_bitrate_mode_default);
    }

    public int getVideoBitrateCustom() {
        return castInt(R.string.key_video_bitrate_custom, R.string.video_bitrate_custom_default);
    }

    public boolean isVideoFrameRateOptimizationEnabled() {
        return getBool(R.string.key_video_frame_rate_optimization, R.string.video_frame_rate_optimization_default);
    }

    public boolean allowVirtualActivityLauncher() {
        return getBool(R.string.key_allow_virtual_activity_launcher, R.string.allow_virtual_activity_launcher_default);
    }

    public boolean isKeepScreenAwakeEnabled() {
        return getBool(R.string.key_keep_screen_awake, R.string.keep_screen_awake_default);
    }

    public MediaAudioCaptureMode getMediaAudioCaptureMode() {
        return MediaAudioCaptureMode.parse(context, getString(R.string.key_media_audio_capture_mode));
    }

    public boolean allowCapturePhoneAudio() {
        return getBool(R.string.key_phone_audio_capture, R.string.phone_audio_capture_default);
    }

    public boolean useImportedPhoneKeys() {
        return getBool(R.string.key_use_imported_phone_keys, R.string.start_projection_when_locked_default);
    }

    public byte[][] getX509CertificateChain(String key) {
        return getRawBytesMulti(key);
    }

    public byte[] getPKCS8PrivateKey(String prefKey) {
        return getRawBytes(prefKey);
    }

    public boolean saveSelfSignedPhoneX509CertificateChain(byte[][] encodedCertChain) {
        return saveRawBytes(R.string.key_self_signed_phone_x509_certificate_chain, encodedCertChain);
    }

    public byte[][] getSelfSignedPhoneX509CertificateChain() {
        return getRawBytesMulti(R.string.key_self_signed_phone_x509_certificate_chain);
    }

    public boolean saveSelfSignedPhonePKCS8PrivateKey(byte[] encodedPrivateKey) {
        return saveRawBytes(R.string.key_self_signed_phone_pkcs8_private_key, encodedPrivateKey);
    }

    public byte[] getSelfSignedPhonePKCS8PrivateKey() {
        return getRawBytes(R.string.key_self_signed_phone_pkcs8_private_key);
    }

    public boolean saveImportedPhoneX509CertificateChain(byte[][] encodedCertChain) {
        return saveRawBytes(R.string.key_imported_phone_x509_certificate_chain, encodedCertChain);
    }

    public byte[][] getImportedPhoneX509CertificateChain() {
        return getRawBytesMulti(R.string.key_imported_phone_x509_certificate_chain);
    }

    public boolean saveImportedPhonePKCS8PrivateKey(byte[] encodedPrivateKey) {
        return saveRawBytes(R.string.key_imported_phone_pkcs8_private_key, encodedPrivateKey);
    }

    public byte[] getImportedPhonePKCS8PrivateKey() {
        return getRawBytes(R.string.key_imported_phone_pkcs8_private_key);
    }

    public boolean saveDockPinned(String config) {
        return saveString(R.string.key_dock_pinned, config);
    }

    public String getDockPinned() {
        return getString(R.string.key_dock_pinned);
    }

    public boolean saveLiabilityAgreementVersion(int version) {
        return saveInt(R.string.key_liability_agreement_version, version);
    }

    public int getLiabilityAgreementVersion() {
        return getInt(R.string.key_liability_agreement_version, LIABILITY_AGREEMENT_VERSION_NONE);
    }

    public boolean saveSetupVersion(int version) {
        return saveInt(R.string.key_setup_version, version);
    }

    public int getSetupVersion() {
        return getInt(R.string.key_setup_version, SETUP_VERSION_NONE);
    }


    private int castInt(@StringRes int key, @StringRes int defaultRes) {
        String stringValue = prefs.getString(context.getString(key), null);
        if (stringValue == null) stringValue = context.getString(defaultRes);

        try {
            return Integer.parseInt(stringValue);
        } catch (NumberFormatException e) {
            Log.wtf(TAG, "failed to cast preference value to integer", e);
            assert false;

            try {
                return Integer.parseInt(context.getString(defaultRes));
            } catch (NumberFormatException ee) {
                Log.wtf(TAG, "default value failed to parse to int", ee);
                throw new AssertionError(e);
            }
        }
    }

    private boolean getBool(@StringRes int key, @StringRes int defaultRes) {
        boolean defaultValue = Boolean.parseBoolean(context.getString(defaultRes));
        return prefs.getBoolean(context.getString(key), defaultValue);
    }

    private boolean saveString(@StringRes int key, String value) {
        return prefs.edit()
                .putString(context.getString(key), value)
                .commit();
    }

    private String getString(@StringRes int key) {
        return prefs.getString(context.getString(key), null);
    }

    private boolean saveInt(@StringRes int key, int value) {
        return prefs.edit()
                .putInt(context.getString(key), value)
                .commit();
    }

    private int getInt(@StringRes int key, int defaultValue) {
        return prefs.getInt(context.getString(key), defaultValue);
    }

    private boolean saveRawBytes(@StringRes int key, byte[]... multiData) {
        String[] encodedMultiData = new String[multiData.length];
        for (int i = 0; i < multiData.length; i++)
            encodedMultiData[i] = Base64.encodeToString(multiData[i], Base64.NO_WRAP);

        String value = String.join(RAW_BYTES_MULTI_SEPARATOR, encodedMultiData);
        return saveString(key, value);
    }

    private byte[][] getRawBytesMulti(String key) {
        String value = prefs.getString(key, null);
        if (value == null) return null;

        String[] encodedMultiData = value.split(RAW_BYTES_MULTI_SEPARATOR);
        byte[][] multiData = new byte[encodedMultiData.length][];
        for (int i = 0; i < multiData.length; i++)
            multiData[i] = Base64.decode(encodedMultiData[i], Base64.NO_WRAP);

        return multiData;
    }

    private byte[][] getRawBytesMulti(@StringRes int key) {
        return getRawBytesMulti(context.getString(key));
    }

    private byte[] getRawBytes(String key) {
        byte[][] multiData = getRawBytesMulti(key);
        if (multiData == null) return null;
        if (multiData.length == 0) return new byte[0];
        if (multiData.length > 1) throw new AssertionError("expected 1 or 0 byte arrays for preference \"" + key + "\", but found " + multiData.length);
        return multiData[0];
    }

    private byte[] getRawBytes(@StringRes int key) {
        return getRawBytes(context.getString(key));
    }

    public static <T> T enumForPref(Context context, String value, @StringRes int key, @StringRes int defaultRes, List<Pair<Integer, T>> mapping) {
        String defaultValue = context.getString(defaultRes);
        if (value == null) value = defaultValue;

        T defaultMapping = null;
        for (Pair<Integer, T> entry : mapping) {
            String entryValue = context.getString(entry.first);
            if (entryValue.equals(defaultValue)) defaultMapping = entry.second;
            if (entryValue.equals(value)) return entry.second;
        }

        if (defaultMapping == null)
            Log.wtf(TAG, "default value not present in mappings", new AssertionError());

        Log.wtf(TAG, "unhandled value for pref " + context.getString(key) + ": " + value);
        assert false;
        return defaultMapping;
    }

}
