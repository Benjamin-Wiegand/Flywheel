package io.benwiegand.projection.geargrinder;

import static io.benwiegand.projection.geargrinder.util.UiUtil.errorDialog;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.exception.UserFriendlyException;

public class LogRecordingsActivity extends AppCompatActivity {
    private static final String TAG = LogRecordingsActivity.class.getSimpleName();

    private static final String MIME_PLAIN_TEXT = "text/plain";

    private final List<File> files = new ArrayList<>();

    private File selectedFile = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_recordings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        RecyclerView recyclerView = findViewById(R.id.log_recording_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @SuppressLint("NotifyDataSetChanged")
    private void refresh() {
        File[] filesDirectories = new File[] {
                getExternalFilesDir(null),
                getFilesDir(),
        };

        files.clear();
        for (File dir : filesDirectories) {
            if (dir == null) continue;
            File logDir = dir.toPath().resolve("logs").toFile();
            File[] listing = logDir.listFiles();
            if (listing == null) {
                Log.w(TAG, "log directory doesn't exist: " + logDir);
                continue;
            }

            Log.i(TAG, "fetching log recordings from: " + logDir);
            files.addAll(List.of(listing));
        }

        files.sort(Comparator.comparingLong(File::lastModified).reversed());

        Log.v(TAG, "found " + files.size() + " total log recordings");
        adapter.notifyDataSetChanged();
    }

    private void openRecording(File file) {
        Log.i(TAG, "launching open intent for log recording: " + file);
        Uri uri = FileProvider.getUriForFile(this, getPackageName(), file);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, MIME_PLAIN_TEXT);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(intent);
    }

    private void exportRecording(File file) {
        Log.i(TAG, "launching create document intent for recording: " + file);
        selectedFile = file;
        createDocumentLauncher.launch(file.getName());
    }

    private void deleteRecording(File file) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.log_recording_delete_dialog_title)
                .setMessage(R.string.log_recording_delete_dialog_message)
                .setPositiveButton(R.string.delete_button, (di, i) -> {
                    Log.i(TAG, "deleting log recording: " + file);

                    if (!file.delete())
                        Log.w(TAG, "File.delete() returned false");

                    refresh();
                })
                .setNeutralButton(R.string.cancel_button, null)
                .show();
    }

    private boolean onRecordingEntryMenuItemClicked(MenuItem item, File file) {
        Map<Integer, Runnable> actionMap = Map.of(
                R.id.open_button, () -> openRecording(file),
                R.id.export_button, () -> exportRecording(file),
                R.id.delete_button, () -> deleteRecording(file)
        );
        Runnable action = actionMap.get(item.getItemId());
        if (action == null) return false;
        action.run();
        return true;
    }

    private void exportRecordingToDocument(File srcFile, Uri dstUri) throws UserFriendlyException {
        Log.i(TAG, "exporting log: " + srcFile);
        Log.i(TAG, "destination: " + dstUri);

        try (OutputStream os = getContentResolver().openOutputStream(dstUri); InputStream is = new FileInputStream(srcFile)) {
            if (os == null) throw new IOException("document provider crashed?");

            int len;
            byte[] buffer = new byte[65535];
            while ((len = is.read(buffer)) >= 0)
                os.write(buffer, 0, len);
            os.flush();

        } catch (IOException e) {
            Log.e(TAG, "export failed due to io exception", e);
            throw new UserFriendlyException(this, R.string.log_export_error_title, R.string.log_export_error_io, e);
        } catch (Throwable t) {
            Log.e(TAG, "export failed", t);
            throw new UserFriendlyException(this, R.string.log_export_error_title, R.string.log_export_error_unexpected, t);
        }

        Toast.makeText(this, R.string.log_export_finished_toast, Toast.LENGTH_SHORT).show();
    }

    private final RecyclerView.Adapter<RecordingEntryHolder> adapter = new RecyclerView.Adapter<>() {
        @NonNull
        @Override
        public RecordingEntryHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View entryView = getLayoutInflater().inflate(R.layout.layout_log_recording_entry, parent, false);
            return new RecordingEntryHolder(entryView);
        }

        @Override
        public void onBindViewHolder(@NonNull RecordingEntryHolder holder, int position) {
            View entryView = holder.itemView;
            File file = files.get(position);

            TextView titleText = entryView.findViewById(R.id.title);
            TextView timestampText = entryView.findViewById(R.id.timestamp);

            String timestampFormat = getString(DateFormat.is24HourFormat(LogRecordingsActivity.this) ? R.string.detailed_date_time_format_24_hour : R.string.detailed_date_time_format_12_hour);
            titleText.setText(file.getName());
            timestampText.setText(DateFormat.format(timestampFormat, file.lastModified()));

            PopupMenu popupMenu = new PopupMenu(LogRecordingsActivity.this, entryView);
            popupMenu.inflate(R.menu.menu_log_recording_context);
            popupMenu.setOnMenuItemClickListener((item) -> onRecordingEntryMenuItemClicked(item, file));

            entryView.setOnClickListener(v -> popupMenu.show());
        }

        @Override
        public int getItemCount() {
            return files.size();
        }
    };

    private final ActivityResultLauncher<String> createDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(MIME_PLAIN_TEXT),
            uri -> {
                if (uri == null) return;
                try {
                    exportRecordingToDocument(selectedFile, uri);
                    selectedFile = null;
                } catch (UserFriendlyException e) {
                    errorDialog(this, e).show();
                }
            });

    public static class RecordingEntryHolder extends RecyclerView.ViewHolder {
        public RecordingEntryHolder(@NonNull View view) {
            super(view);
        }
    }
}
