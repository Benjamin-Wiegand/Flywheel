package io.benwiegand.projection.geargrinder;

import static io.benwiegand.projection.geargrinder.util.ActivityUtil.ISSUE_TRACKER_LINK;
import static io.benwiegand.projection.geargrinder.util.ActivityUtil.LICENSE_LINK;
import static io.benwiegand.projection.geargrinder.util.ActivityUtil.SOURCE_CODE_LINK;
import static io.benwiegand.projection.geargrinder.util.ActivityUtil.WIKI_LINK;
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
                openLink(this, SOURCE_CODE_LINK));

        wikiButton.setOnClickListener(v ->
                openLink(this, WIKI_LINK));

        licenseButton.setOnClickListener(v ->
                openLink(this, LICENSE_LINK));

        issueTrackerButton.setOnClickListener(v ->
                openLink(this, ISSUE_TRACKER_LINK));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

}
