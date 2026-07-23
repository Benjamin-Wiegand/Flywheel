package io.benwiegand.projection.geargrinder;

import static io.benwiegand.projection.geargrinder.util.ActivityUtil.openLink;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_about);
        setSupportActionBar(findViewById(R.id.action_bar));
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        View sourceCodeButton = findViewById(R.id.source_code_button);
        View wikiButton = findViewById(R.id.wiki_button);
        View licenseButton = findViewById(R.id.license_button);
        View issueTrackerButton = findViewById(R.id.issue_tracker_button);

        sourceCodeButton.setOnClickListener(v ->
                openLink(this, R.string.source_code_link));

        wikiButton.setOnClickListener(v ->
                openLink(this, R.string.wiki_link));

        licenseButton.setOnClickListener(v ->
                openLink(this, R.string.license_link));

        issueTrackerButton.setOnClickListener(v ->
                openLink(this, R.string.issue_tracker_link));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

}
