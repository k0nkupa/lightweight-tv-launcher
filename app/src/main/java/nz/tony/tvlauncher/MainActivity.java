package nz.tony.tvlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ImageButton;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int[] TILES = {
            R.id.netflix, R.id.youtube, R.id.plex, R.id.hdmi, R.id.settings
    };
    private int focusedTile;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        focusedTile = savedInstanceState == null
                ? getPreferences(MODE_PRIVATE).getInt("focus", R.id.netflix)
                : savedInstanceState.getInt("focus", R.id.netflix);
        boolean validFocus = false;
        for (int tile : TILES) {
            if (tile == focusedTile) validFocus = true;
            findViewById(tile).setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus) focusedTile = view.getId();
            });
        }
        if (!validFocus) focusedTile = R.id.netflix;
        loadAppIcon(R.id.netflix, "com.netflix.ninja");
        loadAppIcon(R.id.youtube, "com.google.android.youtube.tv");
        loadAppIcon(R.id.plex, "com.plexapp.android");
        findViewById(R.id.netflix).setOnClickListener(v -> openApp("com.netflix.ninja", R.string.netflix));
        findViewById(R.id.youtube).setOnClickListener(v -> openApp("com.google.android.youtube.tv", R.string.youtube));
        findViewById(R.id.plex).setOnClickListener(v -> openApp("com.plexapp.android", R.string.plex));
        findViewById(R.id.hdmi).setOnClickListener(v -> openHdmi());
        findViewById(R.id.settings).setOnClickListener(v -> open(new Intent(Settings.ACTION_SETTINGS),
                getString(R.string.settings_unavailable)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        findViewById(focusedTile).requestFocus();
    }

    @Override
    protected void onPause() {
        getPreferences(MODE_PRIVATE).edit().putInt("focus", focusedTile).apply();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("focus", focusedTile);
        super.onSaveInstanceState(state);
    }

    private void loadAppIcon(int tile, String packageName) {
        try {
            ImageButton button = findViewById(tile);
            button.setImageDrawable(getPackageManager().getApplicationIcon(packageName));
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException | SecurityException exception) {
            // Keep the bundled mark when the TV app's icon is unavailable.
        }
    }

    private void openApp(String packageName, int label) {
        String failure = getString(R.string.app_unavailable, getString(label));
        try {
            Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(packageName);
            if (intent == null) {
                showMessage(failure);
            } else {
                open(intent, failure);
            }
        } catch (SecurityException exception) {
            showMessage(failure);
        }
    }

    private void openHdmi() {
        try {
            TvInputManager manager = getSystemService(TvInputManager.class);
            List<TvInputInfo> hdmiInputs = new ArrayList<>();
            List<TvInputInfo> inputs = manager == null ? null : manager.getTvInputList();
            if (inputs != null) {
                for (TvInputInfo input : inputs) {
                    if (input.getType() == TvInputInfo.TYPE_HDMI && input.isPassthroughInput()) {
                        hdmiInputs.add(input);
                    }
                }
            }
            if (hdmiInputs.isEmpty()) {
                showMessage(getString(R.string.hdmi_unavailable));
            } else if (hdmiInputs.size() == 1) {
                openInput(hdmiInputs.get(0));
            } else {
                CharSequence[] labels = new CharSequence[hdmiInputs.size()];
                for (int i = 0; i < hdmiInputs.size(); i++) {
                    CharSequence label;
                    try {
                        label = hdmiInputs.get(i).loadCustomLabel(this);
                        if (label == null || label.length() == 0) label = hdmiInputs.get(i).loadLabel(this);
                    } catch (Resources.NotFoundException exception) {
                        label = null;
                    }
                    if (label == null || label.length() == 0) label = getString(R.string.hdmi_input);
                    labels[i] = getString(R.string.input_choice, i + 1, label);
                }
                new AlertDialog.Builder(this).setTitle(R.string.choose_hdmi)
                        .setItems(labels, (dialog, which) -> openInput(hdmiInputs.get(which)))
                        .setNegativeButton(android.R.string.cancel, null).show();
            }
        } catch (SecurityException | IllegalStateException exception) {
            showMessage(getString(R.string.hdmi_unavailable));
        }
    }

    private void openInput(TvInputInfo input) {
        open(new Intent(Intent.ACTION_VIEW,
                TvContract.buildChannelUriForPassthroughInput(input.getId())),
                getString(R.string.hdmi_unavailable));
    }

    private void open(Intent intent, String failure) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException exception) {
            showMessage(failure);
        }
    }

    private void showMessage(String message) {
        new AlertDialog.Builder(this).setTitle(R.string.cannot_open).setMessage(message)
                .setPositiveButton(R.string.close, null).show();
    }
}
