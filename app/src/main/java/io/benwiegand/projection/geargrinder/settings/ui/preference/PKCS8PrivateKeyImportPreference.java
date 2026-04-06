package io.benwiegand.projection.geargrinder.settings.ui.preference;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.crypto.CryptoConstants;
import io.benwiegand.projection.geargrinder.exception.FileImportException;
import io.benwiegand.projection.geargrinder.exception.UserFriendlyException;

public class PKCS8PrivateKeyImportPreference extends FileImportPreference<PrivateKey> {
    private static final String TAG = PKCS8PrivateKeyImportPreference.class.getSimpleName();

    public PKCS8PrivateKeyImportPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public PKCS8PrivateKeyImportPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public PKCS8PrivateKeyImportPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public PKCS8PrivateKeyImportPreference(@NonNull Context context) {
        super(context);
    }

    @Override
    public PrivateKey onFileSelected(Uri uri) throws UserFriendlyException {
        ByteArrayOutputStream os = new ByteArrayOutputStream(2048);
        readFile(uri, os);

        String dataString = new String(os.toByteArray(), StandardCharsets.UTF_8);
        byte[] data = os.toByteArray();
        byte[] pkcs8EncodedKeyData = null;

        // TODO: maybe support plain base64 too
        try {
            String[] beginSplit = dataString.split(CryptoConstants.BEGIN_PRIVATE_KEY_MARKER, 2);
            if (beginSplit.length > 1) {
                String[] endSplit = beginSplit[1].split(CryptoConstants.END_PRIVATE_KEY_MARKER, 2);
                pkcs8EncodedKeyData = Base64.decode(endSplit[0], 0);
            }
        } catch (Throwable t) {
            Log.d(TAG, "failed to parse file as PEM", t);
        }

        if (pkcs8EncodedKeyData == null) pkcs8EncodedKeyData = data;

        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec encodedKeySpec = new PKCS8EncodedKeySpec(pkcs8EncodedKeyData);

            return keyFactory.generatePrivate(encodedKeySpec);
        } catch (Throwable t) {
            Log.w(TAG, "failed to parse private key for import", t);
            throw new FileImportException(getContext(), R.string.file_import_error_invalid_key, t);
        }
    }
}
