package io.benwiegand.projection.geargrinder.settings.ui.preference;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileInputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.crypto.KeyWithChain;
import io.benwiegand.projection.geargrinder.exception.FileImportException;
import io.benwiegand.projection.geargrinder.util.CryptoUtil;

public class PemCertificateKeyBundleImportPreference extends FileImportPreference<KeyWithChain> {

    public PemCertificateKeyBundleImportPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public PemCertificateKeyBundleImportPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public PemCertificateKeyBundleImportPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public PemCertificateKeyBundleImportPreference(@NonNull Context context) {
        super(context);
    }

    @Override
    public KeyWithChain<PrivateKey, X509Certificate> onFileSelected(Uri uri) throws FileImportException {
        try (ParcelFileDescriptor pfd = getContext().getContentResolver().openFileDescriptor(uri, "r")) {
            if (pfd == null) throw new AssertionError("file provider crashed?");
            FileInputStream is = new ParcelFileDescriptor.AutoCloseInputStream(pfd);

            return CryptoUtil.parseX509CertificatePKCS8KeyBundleFromPemFile(getContext(), is);
        } catch (FileImportException e) {
            throw e;
        } catch (Throwable t) {
            throw new FileImportException(getContext(), R.string.file_import_error_io_error, t);
        }
    }
}
