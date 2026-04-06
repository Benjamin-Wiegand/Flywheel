package io.benwiegand.projection.geargrinder.settings.ui.preference;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.bouncycastle.jce.provider.X509CertParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import java.security.cert.X509Certificate;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.exception.FileImportException;

public class X509CertificateChainImportPreference extends FileImportPreference<X509Certificate[]> {
    private static final String TAG = X509CertificateChainImportPreference.class.getSimpleName();

    public X509CertificateChainImportPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public X509CertificateChainImportPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public X509CertificateChainImportPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public X509CertificateChainImportPreference(@NonNull Context context) {
        super(context);
    }

    @Override
    public X509Certificate[] onFileSelected(Uri uri) throws FileImportException {
        ByteArrayOutputStream os = new ByteArrayOutputStream(2048);
        readFile(uri, os);

        List<X509Certificate> certChainList = new ArrayList<>();
        try {
            X509CertParser parser = new X509CertParser();
            parser.engineInit(new ByteArrayInputStream(os.toByteArray()));

            X509Certificate certificate;
            while ((certificate = (X509Certificate) parser.engineRead()) != null)
                certChainList.add(certificate);

            if (certChainList.isEmpty()) throw new IOException("expected at least one certificate, found none before EOS");

            X509Certificate[] certChain = new X509Certificate[certChainList.size()];
            for (int i = 0; i < certChain.length; i++)
                certChain[i] = certChainList.get(i);
            return certChain;
        } catch (Throwable t) {
            Log.w(TAG, "failed to parse certificate for import", t);
            throw new FileImportException(getContext(), R.string.file_import_error_invalid_cert, t);
        }
    }

}
