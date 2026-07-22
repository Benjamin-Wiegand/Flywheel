package io.benwiegand.projection.geargrinder.crypto;

import static io.benwiegand.projection.geargrinder.util.CryptoUtil.decodePKCS8PrivateKey;
import static io.benwiegand.projection.geargrinder.util.CryptoUtil.decodeX509CertChain;
import static io.benwiegand.projection.geargrinder.util.CryptoUtil.encodeCertChain;
import static io.benwiegand.projection.geargrinder.util.CryptoUtil.encodePrivateKey;
import static io.benwiegand.projection.geargrinder.util.CryptoUtil.generateSelfSignedPhoneKeys;

import android.content.Context;
import android.util.Log;

import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.exception.CorruptedCertificateException;
import io.benwiegand.projection.geargrinder.exception.CorruptedCertificateKeyBundleException;
import io.benwiegand.projection.geargrinder.exception.CorruptedKeyException;
import io.benwiegand.projection.geargrinder.settings.SettingsManager;

public class CryptoManager {
    private static final String TAG = CryptoManager.class.getSimpleName();

    private static final String PHONE_KEY_ALIAS = "car_service";

    private final Context context;
    private final SettingsManager settingsManager;

    public CryptoManager(Context context) {
        this.context = context;
        settingsManager = new SettingsManager(context);
    }


    public PrivateKey getImportedPhonePKCS8PrivateKey() throws CorruptedKeyException {
        try {
            return decodePKCS8PrivateKey(settingsManager.getImportedPhonePKCS8PrivateKey());
        } catch (Throwable t) {
            throw new CorruptedKeyException(context, R.string.corrupted_key_error_imported_phone, t);
        }
    }

    public X509Certificate[] getImportedPhoneX509CertificateChain() throws CorruptedCertificateException {
        try {
            return decodeX509CertChain(settingsManager.getImportedPhoneX509CertificateChain());
        } catch (Throwable t) {
            throw new CorruptedCertificateException(context, R.string.corrupted_cert_error_imported_phone, t);
        }
    }

    public KeyWithChain<PrivateKey, X509Certificate> getImportedPhoneKeys() throws CorruptedKeyException, CorruptedCertificateException {
        PrivateKey privateKey = getImportedPhonePKCS8PrivateKey();
        X509Certificate[] certChain = getImportedPhoneX509CertificateChain();
        if (privateKey == null || certChain == null) return null;
        return new KeyWithChain<>(privateKey, certChain);
    }

    public PrivateKey getSelfSignedPhonePKCS8PrivateKey() throws CorruptedKeyException {
        try {
            return decodePKCS8PrivateKey(settingsManager.getSelfSignedPhonePKCS8PrivateKey());
        } catch (Throwable t) {
            throw new CorruptedKeyException(context, R.string.corrupted_key_error_self_signed_phone, t);
        }
    }

    public X509Certificate[] getSelfSignedPhoneX509CertificateChain() throws CorruptedCertificateException {
        try {
            return decodeX509CertChain(settingsManager.getSelfSignedPhoneX509CertificateChain());
        } catch (Throwable t) {
            throw new CorruptedCertificateException(context, R.string.corrupted_cert_error_self_signed_phone, t);
        }
    }

    public KeyWithChain<PrivateKey, X509Certificate> getSelfSignedPhoneKeys() throws CorruptedCertificateException, CorruptedKeyException {
        PrivateKey privateKey = getSelfSignedPhonePKCS8PrivateKey();
        X509Certificate[] certChain = getSelfSignedPhoneX509CertificateChain();
        if (privateKey == null || certChain == null) return null;
        return new KeyWithChain<>(privateKey, certChain);
    }

    public KeyWithChain<PrivateKey, X509Certificate> getOrGenerateSelfSignedPhoneKeys() {
        KeyWithChain<PrivateKey, X509Certificate> keys = null;
        try {
            keys = getSelfSignedPhoneKeys();
        } catch (CorruptedKeyException | CorruptedCertificateException e) {
            Log.wtf(TAG, "failed to load stored self-signed keys", e);
            assert false;
        }

        if (keys != null) return keys;
        keys = generateSelfSignedPhoneKeys();

        try {
            settingsManager.saveSelfSignedPhonePKCS8PrivateKey(encodePrivateKey(keys.key()));
            settingsManager.saveSelfSignedPhoneX509CertificateChain(encodeCertChain(keys.certChain()));
        } catch (Throwable t) {
            Log.wtf(TAG, "failed to encode and save self-signed keys", t);
            assert false;
        }
        return keys;
    }

    public KeystoreManager getKeystoreForImportedPhoneKeys() throws CorruptedKeyException, CorruptedCertificateException {
        KeyWithChain<PrivateKey, X509Certificate> keys = getImportedPhoneKeys();
        if (keys == null) return null;
        KeystoreManager km = new KeystoreManager();
        km.importKey(PHONE_KEY_ALIAS, keys);
        return km;
    }

    public KeystoreManager getKeystoreForSelfSignedPhoneKeys() {
        KeyWithChain<PrivateKey, X509Certificate> keys = getOrGenerateSelfSignedPhoneKeys();
        KeystoreManager km = new KeystoreManager();
        km.importKey(PHONE_KEY_ALIAS, keys);
        return km;
    }

    public KeystoreManager getKeystoreForCurrentConfiguration() throws CorruptedKeyException, CorruptedCertificateException {
        if (settingsManager.useImportedPhoneKeys()) {
            Log.i(TAG, "using imported phone keys");
            return getKeystoreForImportedPhoneKeys();
        } else {
            Log.i(TAG, "using self-signed phone keys");
            return getKeystoreForSelfSignedPhoneKeys();
        }
    }

    public boolean importPhonePKCS8PrivateKey(PrivateKey privateKey) {
        return settingsManager.saveImportedPhonePKCS8PrivateKey(encodePrivateKey(privateKey));
    }

    public boolean importPhoneX509CertificateChain(X509Certificate[] certChain) throws CertificateEncodingException {
        return settingsManager.saveImportedPhoneX509CertificateChain(encodeCertChain(certChain));
    }

    public boolean importPhoneX509CertificatePKCS8KeyBundle(KeyWithChain<PrivateKey, X509Certificate> keyWithChain) throws CertificateEncodingException {
        byte[] key = encodePrivateKey(keyWithChain.key());
        byte[][] chain = encodeCertChain(keyWithChain.certChain());
        return settingsManager.saveImportedPhonePKCS8PrivateKey(key)
                && settingsManager.saveImportedPhoneX509CertificateChain(chain);
    }

    public boolean testImportedKeys() throws CorruptedKeyException, CorruptedCertificateException, CorruptedCertificateKeyBundleException {
        KeyWithChain<PrivateKey, X509Certificate> keys = getImportedPhoneKeys();
        if (keys == null) return false;

        if (keys.certChain().length < 1) throw new AssertionError("missing certificate");
        X509Certificate primaryCert = keys.certChain()[0];

        if (!keys.key().getAlgorithm().equals(primaryCert.getPublicKey().getAlgorithm())) {
            Log.e(TAG, "mismatching key algorithms");
            Log.e(TAG, "public = " + primaryCert.getPublicKey().getAlgorithm());
            Log.e(TAG, "private = " + keys.key().getAlgorithm());
            throw new CorruptedCertificateKeyBundleException(context, R.string.corrupted_cert_key_bundle_error_mismatching_keys);
        }

        if (keys.key() instanceof RSAPrivateKey privateKey) {
            if (primaryCert.getPublicKey() instanceof RSAPublicKey publicKey) {
                if (!publicKey.getModulus().equals(privateKey.getModulus())) {
                    Log.e(TAG, "key modulus do not match");
                    throw new CorruptedCertificateKeyBundleException(context, R.string.corrupted_cert_key_bundle_error_mismatching_keys);
                }
            } else {
                Log.wtf(TAG, "failed to parse public key as rsa: " + primaryCert.getPublicKey());
            }
        } else {
            Log.w(TAG, "unknown key type: " + keys.key().getAlgorithm());
        }

        return getKeystoreForImportedPhoneKeys() != null;
    }
}
