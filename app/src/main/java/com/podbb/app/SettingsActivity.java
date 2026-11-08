package com.podbb.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class SettingsActivity extends Activity {

    private Button btnStorageSdCard;
    private Button btnStorageInternal;
    private Button btnStorageCustom;
    private LinearLayout layoutCustomPath;
    private EditText etCustomPath;
    private Button btnSaveCustomPath;
    private TextView tvActivePath;
    private TextView tvFreeSpace;
    private Button btnClearCache;
    private TextView tvCacheStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Button btnBack = (Button) findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        btnStorageSdCard   = (Button) findViewById(R.id.btnStorageSdCard);
        btnStorageInternal = (Button) findViewById(R.id.btnStorageInternal);
        btnStorageCustom   = (Button) findViewById(R.id.btnStorageCustom);
        layoutCustomPath   = (LinearLayout) findViewById(R.id.layoutCustomPath);
        etCustomPath       = (EditText) findViewById(R.id.etCustomPath);
        btnSaveCustomPath  = (Button) findViewById(R.id.btnSaveCustomPath);
        tvActivePath       = (TextView) findViewById(R.id.tvActivePath);
        tvFreeSpace        = (TextView) findViewById(R.id.tvFreeSpace);
        btnClearCache      = (Button) findViewById(R.id.btnClearCache);
        tvCacheStatus      = (TextView) findViewById(R.id.tvCacheStatus);

        btnStorageSdCard.setOnClickListener(v -> {
            SettingsHelper.setStorageType(this, SettingsHelper.STORAGE_SDCARD);
            updateStorageUI();
            Toast.makeText(this, "Downloads set to MicroSD Card", Toast.LENGTH_SHORT).show();
        });

        btnStorageInternal.setOnClickListener(v -> {
            SettingsHelper.setStorageType(this, SettingsHelper.STORAGE_INTERNAL);
            updateStorageUI();
            Toast.makeText(this, "Downloads set to Internal Storage", Toast.LENGTH_SHORT).show();
        });

        btnStorageCustom.setOnClickListener(v -> {
            SettingsHelper.setStorageType(this, SettingsHelper.STORAGE_CUSTOM);
            updateStorageUI();
        });

        btnSaveCustomPath.setOnClickListener(v -> {
            String path = etCustomPath.getText().toString().trim();
            if (!path.isEmpty()) {
                File dir = new File(path);
                if (dir.exists() || dir.mkdirs()) {
                    SettingsHelper.setCustomPath(this, path);
                    updateStorageUI();
                    Toast.makeText(this, "Custom download path saved", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Cannot create/access directory", Toast.LENGTH_LONG).show();
                }
            }
        });

        btnClearCache.setOnClickListener(v -> {
            clearCoverCache();
        });

        updateStorageUI();
        FontHelper.apply(findViewById(R.id.rootView), this);
    }

    private void updateStorageUI() {
        String type = SettingsHelper.getStorageType(this);

        btnStorageSdCard.setText(SettingsHelper.STORAGE_SDCARD.equals(type)
                ? "[•] Removable MicroSD Card" : "[ ] Removable MicroSD Card");
        btnStorageSdCard.setTextColor(SettingsHelper.STORAGE_SDCARD.equals(type) ? 0xFF00A8DF : 0xFFF5F5F5);

        btnStorageInternal.setText(SettingsHelper.STORAGE_INTERNAL.equals(type)
                ? "[•] Internal Device Storage" : "[ ] Internal Device Storage");
        btnStorageInternal.setTextColor(SettingsHelper.STORAGE_INTERNAL.equals(type) ? 0xFF00A8DF : 0xFFF5F5F5);

        btnStorageCustom.setText(SettingsHelper.STORAGE_CUSTOM.equals(type)
                ? "[•] Custom Directory Path" : "[ ] Custom Directory Path");
        btnStorageCustom.setTextColor(SettingsHelper.STORAGE_CUSTOM.equals(type) ? 0xFF00A8DF : 0xFFF5F5F5);

        if (SettingsHelper.STORAGE_CUSTOM.equals(type)) {
            layoutCustomPath.setVisibility(View.VISIBLE);
            etCustomPath.setText(SettingsHelper.getCustomPath(this));
        } else {
            layoutCustomPath.setVisibility(View.GONE);
        }

        String effective = SettingsHelper.getEffectiveDownloadPath(this);
        tvActivePath.setText("Active Download Path:\n" + effective);

        long freeBytes = SettingsHelper.getAvailableSpaceBytes(this);
        if (freeBytes > 0) {
            long mb = freeBytes / (1024 * 1024);
            long gb = mb / 1024;
            if (gb > 0) {
                tvFreeSpace.setText(String.format("Available Free Space: %.1f GB", (float) mb / 1024f));
            } else {
                tvFreeSpace.setText("Available Free Space: " + mb + " MB");
            }
        } else {
            tvFreeSpace.setText("Available Free Space: Ready");
        }
    }

    private void clearCoverCache() {
        new Thread(() -> {
            int count = 0;
            long bytes = 0;
            File external = getExternalCacheDir();
            File dir = new File(external != null ? external : getCacheDir(), "covers");
            if (dir.exists()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        bytes += f.length();
                        if (f.delete()) count++;
                    }
                }
            }
            final int finalCount = count;
            final long finalBytes = bytes;
            runOnUiThread(() -> {
                String freed = (finalBytes / 1024) + " KB";
                tvCacheStatus.setText("Cleared " + finalCount + " cached files (" + freed + " freed)");
                Toast.makeText(this, "Cover cache cleared", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }
}
