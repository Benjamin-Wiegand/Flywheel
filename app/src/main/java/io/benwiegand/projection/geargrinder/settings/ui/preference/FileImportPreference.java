package io.benwiegand.projection.geargrinder.settings.ui.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;

import java.io.FileInputStream;
import java.io.OutputStream;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.exception.FileImportException;
import io.benwiegand.projection.geargrinder.exception.UserFriendlyException;

public abstract class FileImportPreference<T> extends DialogPreference {

    public static final int MAX_FILE_SIZE_UNLIMITED = -1;
    public static final String ACCEPTED_MIME_TYPES_SEPARATOR = ",";

    private int maxFileSize;
    private String[] acceptedMimeTypes;

    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        try (TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FileImportPreference, defStyleAttr, defStyleRes)) {

            maxFileSize = a.getInteger(R.styleable.FileImportPreference_maxFileSize, MAX_FILE_SIZE_UNLIMITED);

            String mimeTypeList = a.getString(R.styleable.FileImportPreference_acceptedMimeTypes);
            acceptedMimeTypes = mimeTypeList != null ? mimeTypeList.split(ACCEPTED_MIME_TYPES_SEPARATOR) : new String[] {"*/*"};

        }
    }

    public FileImportPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    public FileImportPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    public FileImportPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0, 0);
    }

    public FileImportPreference(@NonNull Context context) {
        super(context);
        init(context, null, 0, 0);
    }

    @Override
    protected void onClick() {
        assert getPreferenceManager() != null;
        getPreferenceManager().showDialog(this);
    }

    public int getMaxFileSize() {
        return maxFileSize;
    }

    public String[] getAcceptedMimeTypes() {
        return acceptedMimeTypes;
    }

    protected void readFile(Uri uri, OutputStream os) throws FileImportException {
        try (ParcelFileDescriptor pfd = getContext().getContentResolver().openFileDescriptor(uri, "r")) {
            FileInputStream is = new ParcelFileDescriptor.AutoCloseInputStream(pfd);

            byte[] buffer = new byte[2048];
            int len;
            int total = 0;
            while ((len = is.read(buffer)) >= 0) {
                total += len;
                if (total > getMaxFileSize() && getMaxFileSize() != MAX_FILE_SIZE_UNLIMITED)
                    throw new FileImportException(getContext(), R.string.file_import_error_too_large);
                os.write(buffer, 0, len);
            }
        } catch (UserFriendlyException e) {
            throw e;
        } catch (Throwable t) {
            throw new FileImportException(getContext(), R.string.file_import_error_io_error, t);
        }
    }

    public abstract T onFileSelected(Uri uri) throws UserFriendlyException;

    @Override
    public void notifyChanged() {   // make public
        super.notifyChanged();
    }
}
