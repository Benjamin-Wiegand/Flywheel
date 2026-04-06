package io.benwiegand.projection.geargrinder.crypto;

import android.util.Log;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class KeystoreManager {

    private static final String TAG = KeystoreManager.class.getSimpleName();

    // try to preserve forward-compatibility if possible
    private static final String KEYSTORE_TYPE = "BKS";
    private static final String KEYSTORE_TYPE_FALLBACK = KeyStore.getDefaultType();

    // the password serves no purpose in this case, it's not part of the threat model
    private static final char[] KEYSTORE_PASSWORD = "hunter2".toCharArray();


    private final KeyStore keystore;


    private static KeyStore getKeystoreInstance() {
        try {
            return KeyStore.getInstance(KEYSTORE_TYPE);
        } catch (KeyStoreException e) {
            if (KEYSTORE_TYPE.equals(KEYSTORE_TYPE_FALLBACK)) {
                Log.wtf(TAG, "failed to create keystore", e);
                throw new UnsupportedOperationException("Cannot create keystore of type " + KEYSTORE_TYPE);
            }

            Log.d(TAG, "failed to create keystore instance for " + KEYSTORE_TYPE + ", falling back to " + KEYSTORE_TYPE_FALLBACK, e);
            try {
                return KeyStore.getInstance(KEYSTORE_TYPE_FALLBACK);
            } catch (KeyStoreException ex) {
                Log.wtf(TAG, "couldn't create fallback keystore type either");
                throw new UnsupportedOperationException("Cannot create keystore of type " + KEYSTORE_TYPE + " or " + KEYSTORE_TYPE_FALLBACK, ex);
            }
        }
    }


    /**
     * creates a KeystoreManager with an empty keystore
     * @throws UnsupportedOperationException if the runtime lacks required features/algorithms
     */
    public KeystoreManager() {
        keystore = getKeystoreInstance();

        try {
            keystore.load(null, KEYSTORE_PASSWORD);
        } catch (IOException e) {               // io error, corrupted, or bad password
            throw new AssertionError("io error while creating empty keystore?", e);
        } catch (CertificateException e) {      // a certificate couldn't be loaded
            throw new AssertionError("failed to create empty keystore due to corrupted certificate?", e);
        } catch (NoSuchAlgorithmException e) {  // no algorithm to check integrity (unsupported?)
            throw new UnsupportedOperationException("unable to create a keystore because there's no matching integrity checking algorithm", e);
        }
    }

    public KeyManager[] getKeyManagers() {
        try {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
            kmf.init(keystore, KEYSTORE_PASSWORD);

            return kmf.getKeyManagers();
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException("keypair algorithm not supported", e);
        } catch (UnrecoverableKeyException e) {
            throw new AssertionError("keystore password rejected?", e);
        } catch (KeyStoreException e) {
            throw new RuntimeException("failed to make a key manager", e);
        }
    }

    public TrustManager[] getTrustManagers() {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
            tmf.init(keystore);

            return tmf.getTrustManagers();
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException("keypair algorithm not supported", e);
        } catch (KeyStoreException e) {
            throw new RuntimeException("failed to make a key manager", e);
        }
    }

    /**
     * determine if there is an existing keypair at the given alias.
     * if this method returns true, it is safe to skip initKeypair() for this alias
     * @param alias the alias of the keypair
     * @return true if it exists in the keystore, false otherwise
     */
    public boolean hasKey(String alias) {
        try {
            return keystore.containsAlias(alias);
        } catch (KeyStoreException e) {
            throw new IllegalStateException("keystore not initialized?", e);
        }
    }

    /**
     * imports a private key and corresponding certificate chain
     * @param alias the alias to store the key as (must be unique)
     * @param keyWithChain the private key and certificate chain as a KeyWithChain
     */
    public void importKey(String alias, KeyWithChain keyWithChain) {
        try {
            if (hasKey(alias)) throw new AssertionError("key already present for alias: " + alias);
            Log.i(TAG, "importing keys: \n" + keyWithChain.certChainInfoToString());
            keystore.setKeyEntry(alias, keyWithChain.key(), KEYSTORE_PASSWORD, keyWithChain.certChain());
        } catch (KeyStoreException e) {
            throw new RuntimeException("failed to store imported key and certificate chain", e);
        }
    }

}
