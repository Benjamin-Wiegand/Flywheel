package io.benwiegand.projection.geargrinder.util;

import android.content.Context;
import android.util.Log;

import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.jce.provider.X509CertParser;
import org.bouncycastle.x509.X509V1CertificateGenerator;
import org.bouncycastle.x509.util.StreamParsingException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.crypto.KeyWithChain;
import io.benwiegand.projection.geargrinder.crypto.PemParser;
import io.benwiegand.projection.geargrinder.exception.FileImportException;
import io.benwiegand.projection.geargrinder.exception.PemParseException;

public class CryptoUtil {
    private static final String TAG = CryptoUtil.class.getSimpleName();

    // TODO: subject, issuer, and before/after dates might be configurable at some point
    public static final String KEY_ALGORITHM = "RSA";
    public static final String SELF_SIGNING_ALGORITHM = "SHA256WithRSAEncryption";
    public static final String SELF_CERTIFICATE_SUBJECT = "CN=Bob";                         // not specifying O=CarService seems to work on at least one OEM headunit (of sample size one)
    public static final String SELF_CERTIFICATE_ISSUER = "CN=Bob";
    public static final BigInteger SELF_CERTIFICATE_SERIAL = BigInteger.valueOf(42069L);    // I don't think this matters
    public static final long SELF_CERTIFICATE_NOT_BEFORE_EPOCH = 0L;                        // real certs default to July 4, 2014 GMT
    public static final long SELF_CERTIFICATE_NOT_AFTER_EPOCH = 99999999999L;               // holds off until the far-off year of 5138
    public static final int SELF_KEY_SIZE = 2048;

    public static KeyPair generatePhoneSelfKeypair() {
        try {
            Log.d(TAG, "generating a " + SELF_KEY_SIZE + "-bit " + KEY_ALGORITHM + " key");
            KeyPairGenerator keygen = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            keygen.initialize(SELF_KEY_SIZE);
            return keygen.genKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException("no sufficient keypair algorithm found", e);
        }
    }

    public static X509Certificate selfSignPhoneKeypair(KeyPair keypair) {
        try {
            Log.d(TAG, "self-signing keypair with X509 / SSLv1");
            X509V1CertificateGenerator certgen = new X509V1CertificateGenerator();
            certgen.setSubjectDN(new X509Name(SELF_CERTIFICATE_SUBJECT));
            certgen.setIssuerDN(new X509Name(SELF_CERTIFICATE_ISSUER));
            certgen.setPublicKey(keypair.getPublic());
            certgen.setSerialNumber(SELF_CERTIFICATE_SERIAL);
            certgen.setNotBefore(Date.from(Instant.ofEpochSecond(SELF_CERTIFICATE_NOT_BEFORE_EPOCH)));
            certgen.setNotAfter(Date.from(Instant.ofEpochSecond(SELF_CERTIFICATE_NOT_AFTER_EPOCH)));
            certgen.setSignatureAlgorithm(SELF_SIGNING_ALGORITHM);
            return certgen.generate(keypair.getPrivate());
        } catch (CertificateEncodingException e) {
            throw new RuntimeException("failed to encode certificate", e);
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException("no suitable signing algorithm found", e);
        } catch (SignatureException e) {
            throw new RuntimeException("failed to sign keypair", e);
        } catch (InvalidKeyException e) {
            throw new UnsupportedOperationException("bouncycastle rejected keys", e);
        }
    }

    public static KeyWithChain<PrivateKey, X509Certificate> generateSelfSignedPhoneKeys() {
        KeyPair keypair = generatePhoneSelfKeypair();
        X509Certificate certificate = selfSignPhoneKeypair(keypair);
        return new KeyWithChain<>(keypair.getPrivate(), new X509Certificate[] {certificate});
    }

    public static byte[][] encodeCertChain(Certificate... certChain) throws CertificateEncodingException {
        byte[][] encodedCertChain = new byte[certChain.length][];
        for (int i = 0; i < certChain.length; i++)
            encodedCertChain[i] = certChain[i].getEncoded();
        return encodedCertChain;
    }

    public static X509Certificate[] decodeX509CertChain(byte[][] encodedCertChain) throws StreamParsingException {
        try {
            if (encodedCertChain == null) return null;
            X509CertParser parser = new X509CertParser();
            X509Certificate[] certChain = new X509Certificate[encodedCertChain.length];
            for (int i = 0; i < certChain.length; i++) {
                parser.engineInit(new ByteArrayInputStream(encodedCertChain[i]));
                X509Certificate cert = (X509Certificate) parser.engineRead();
                assert parser.engineRead() == null;
                certChain[i] = cert;
            }

            return certChain;
        } catch (Throwable t) {
            Log.wtf(TAG, "failed to load stored X509 cert chain", t);
            throw t;
        }
    }

    public static byte[] encodePrivateKey(PrivateKey privateKey) {
        byte[] encoded = privateKey.getEncoded();
        if (encoded == null) throw new UnsupportedOperationException("PrivateKey (algo = " + privateKey.getAlgorithm() + ", format =" + privateKey.getFormat() + ") cannot be saved");
        return encoded;
    }

    public static PrivateKey decodePKCS8PrivateKey(byte[] encoded) throws NoSuchAlgorithmException, InvalidKeySpecException {
        try {
            if (encoded == null) return null;
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (Throwable t) {
            Log.wtf(TAG, "failed to load stored private key", t);
            throw t;
        }
    }

    public static KeyWithChain<PrivateKey, X509Certificate> parseX509CertificatePKCS8KeyBundleFromPemFile(Context context, InputStream is) throws FileImportException {
        PemParser pemParser;
        try {
            Log.i(TAG, "parsing PEM bundle");
            pemParser = new PemParser(is);
            pemParser.parse();
        } catch (PemParseException e) {
            throw new FileImportException(context, R.string.file_import_error_invalid_pem, e);
        } catch (Throwable t) {
            throw new FileImportException(context, R.string.file_import_error_io_error, t);
        }

        List<byte[]> privateKeyBlocks = new ArrayList<>(1);
        List<byte[]> certificateBlocks = new ArrayList<>(2);

        privateKeyBlocks.addAll(pemParser.getBlocks(PemParser.PEM_TAG_RSA_PRIVATE_KEY));
        privateKeyBlocks.addAll(pemParser.getBlocks(PemParser.PEM_TAG_PRIVATE_KEY));
        certificateBlocks.addAll(pemParser.getBlocks(PemParser.PEM_TAG_CERTIFICATE));
        certificateBlocks.addAll(pemParser.getBlocks(PemParser.PEM_TAG_X509_CERTIFICATE));
        certificateBlocks.addAll(pemParser.getBlocks(PemParser.PEM_TAG_X_509_CERTIFICATE));

        Log.i(TAG, "found " + privateKeyBlocks.size() + " private key(s)");
        Log.i(TAG, "found " + certificateBlocks.size() + " certificate(s)");

        if (privateKeyBlocks.isEmpty())
            throw new FileImportException(context, R.string.file_import_error_bundle_missing_key);
        if (certificateBlocks.isEmpty())
            throw new FileImportException(context, R.string.file_import_error_bundle_missing_certificate);
        if (privateKeyBlocks.size() > 1)
            throw new FileImportException(context, R.string.file_import_error_bundle_multiple_keys);

        PrivateKey privateKey;
        X509Certificate[] certChain = new X509Certificate[certificateBlocks.size()];
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X509");
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            Log.i(TAG, "parsing private key");
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBlocks.get(0));
            privateKey = keyFactory.generatePrivate(keySpec);

            Log.i(TAG, "parsing certificates");
            for (int i = 0; i < certificateBlocks.size(); i++) {
                certChain[i] = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(certificateBlocks.get(i)));
            }

        } catch (CertificateException e) {
            throw new FileImportException(context, R.string.file_import_error_invalid_cert, e);
        } catch (InvalidKeySpecException e) {
            throw new FileImportException(context, R.string.file_import_error_invalid_key, e);
        } catch (Throwable t) {
            throw new FileImportException(context, R.string.file_import_error_unexpected, t);
        }

        return new KeyWithChain<>(privateKey, certChain);
    }

}
