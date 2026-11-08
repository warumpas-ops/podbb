package com.podbb.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;

public class SettingsHelper {

    private static final String PREFS_NAME = "podbb_settings";
    public static final String KEY_STORAGE_TYPE = "storage_type"; // "internal", "sdcard", "custom"
    public static final String KEY_CUSTOM_PATH  = "custom_storage_path";
    public static final String KEY_DEFAULT_SPEED = "default_speed"; // "1.0x", "1.25x", "1.5x"
    public static final String KEY_SKIP_SECONDS = "skip_seconds"; // 15, 30, 45, 60

    public static final String STORAGE_INTERNAL = "internal";
    public static final String STORAGE_SDCARD   = "sdcard";
    public static final String STORAGE_CUSTOM   = "custom";

    // Known BlackBerry 10 and Android mount points for removable SD cards
    private static final String[] KNOWN_SD_PATHS = {
        "/accounts/1000/removable/sdcard/PodBB",
        "/storage/sdcard1/PodBB",
        "/storage/extSdCard/PodBB",
        "/mnt/extSdCard/PodBB",
        "/mnt/sdcard2/PodBB",
        "/removable/sdcard/PodBB"
    };

    public static SharedPreferences getPrefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String getStorageType(Context ctx) {
        return getPrefs(ctx).getString(KEY_STORAGE_TYPE, STORAGE_SDCARD); // Default to SD card as user requested
    }

    public static void setStorageType(Context ctx, String type) {
        getPrefs(ctx).edit().putString(KEY_STORAGE_TYPE, type).commit();
    }

    public static String getCustomPath(Context ctx) {
        return getPrefs(ctx).getString(KEY_CUSTOM_PATH, "");
    }

    public static void setCustomPath(Context ctx, String path) {
        getPrefs(ctx).edit().putString(KEY_CUSTOM_PATH, path).commit();
    }

    /**
     * Resolves and ensures the actual download folder exists.
     */
    public static File getDownloadDir(Context ctx) {
        String type = getStorageType(ctx);

        if (STORAGE_CUSTOM.equals(type)) {
            String custom = getCustomPath(ctx);
            if (custom != null && !custom.trim().isEmpty()) {
                File dir = new File(custom.trim());
                if (dir.exists() || dir.mkdirs()) return dir;
            }
        }

        if (STORAGE_SDCARD.equals(type)) {
            File sd = getRemovableSdCardDir();
            if (sd != null && (sd.exists() || sd.mkdirs())) return sd;
        }

        // Fallback to internal storage
        File internal = new File(Environment.getExternalStorageDirectory(), "PodBB");
        if (!internal.exists()) internal.mkdirs();
        return internal;
    }

    public static File getRemovableSdCardDir() {
        for (String p : KNOWN_SD_PATHS) {
            File f = new File(p);
            File parent = f.getParentFile();
            if (parent != null && parent.exists() && parent.canWrite()) {
                f.mkdirs();
                return f;
            }
        }
        // If none of the parents exist yet, default to standard BB10 removable sdcard path
        File defaultBb10 = new File(KNOWN_SD_PATHS[0]);
        defaultBb10.mkdirs();
        return defaultBb10;
    }

    public static String getEffectiveDownloadPath(Context ctx) {
        File dir = getDownloadDir(ctx);
        return dir != null ? dir.getAbsolutePath() : "/sdcard/PodBB";
    }

    public static long getAvailableSpaceBytes(Context ctx) {
        try {
            File dir = getDownloadDir(ctx);
            if (dir != null) {
                return dir.getUsableSpace();
            }
        } catch (Exception ignored) {}
        return 0;
    }
}
