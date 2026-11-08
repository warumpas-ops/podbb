package com.podbb.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageLoader {

    private static ImageLoader instance;
    private final LruCache<String, Bitmap> memoryCache;
    private final File diskCacheDir;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public static synchronized ImageLoader getInstance(Context context) {
        if (instance == null) {
            instance = new ImageLoader(context.getApplicationContext());
        }
        return instance;
    }

    private ImageLoader(Context context) {
        mainHandler = new Handler(Looper.getMainLooper());
        executor = Executors.newFixedThreadPool(3);

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = Math.max(1024, maxMemory / 8);
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        File external = context.getExternalCacheDir();
        if (external != null) {
            diskCacheDir = new File(external, "covers");
        } else {
            diskCacheDir = new File(context.getCacheDir(), "covers");
        }
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs();
        }
    }

    public void displayImage(String url, ImageView imageView, int placeholderRes) {
        if (url == null || url.trim().isEmpty()) {
            imageView.setImageResource(placeholderRes);
            return;
        }

        url = url.trim();
        imageView.setTag(url);

        // 1. Check memory cache (instant)
        Bitmap cached = memoryCache.get(url);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        // Set placeholder while loading
        imageView.setImageResource(placeholderRes);

        // 2. Load asynchronously from disk or network
        final String targetUrl = url;
        executor.execute(() -> {
            Bitmap bitmap = loadFromDisk(targetUrl);
            if (bitmap == null) {
                bitmap = downloadAndCache(targetUrl);
            }

            if (bitmap != null) {
                memoryCache.put(targetUrl, bitmap);
                final Bitmap finalBmp = bitmap;
                mainHandler.post(() -> {
                    String tag = (String) imageView.getTag();
                    if (tag != null && tag.equals(targetUrl)) {
                        imageView.setImageBitmap(finalBmp);
                    }
                });
            }
        });
    }

    private Bitmap loadFromDisk(String url) {
        try {
            File file = new File(diskCacheDir, hashKeyForDisk(url));
            if (file.exists() && file.length() > 0) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Bitmap downloadAndCache(String urlStr) {
        File tempFile = null;
        try {
            String hash = hashKeyForDisk(urlStr);
            File targetFile = new File(diskCacheDir, hash);
            tempFile = new File(diskCacheDir, hash + ".tmp");

            HttpURLConnection conn = HttpHelper.openWithRedirects(urlStr);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            int code = conn.getResponseCode();
            if (code != 200 && code != 206) {
                conn.disconnect();
                return null;
            }

            InputStream in = conn.getInputStream();
            FileOutputStream out = new FileOutputStream(tempFile);
            byte[] buf = new byte[8192];
            int read;
            int totalBytes = 0;
            final int maxBytes = 3 * 1024 * 1024; // cap at 3MB to protect BB10 RAM

            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
                totalBytes += read;
                if (totalBytes > maxBytes) break;
            }
            out.flush();
            out.close();
            in.close();
            conn.disconnect();

            if (targetFile.exists()) targetFile.delete();
            tempFile.renameTo(targetFile);

            // Decode scaled image for BlackBerry Q5 (e.g., 160x160 max)
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(targetFile.getAbsolutePath(), opts);

            opts.inSampleSize = calculateInSampleSize(opts, 160, 160);
            opts.inJustDecodeBounds = false;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;

            return BitmapFactory.decodeFile(targetFile.getAbsolutePath(), opts);
        } catch (Exception e) {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            return null;
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private String hashKeyForDisk(String key) {
        try {
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.update(key.getBytes("UTF-8"));
            byte[] digest = m.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(key.hashCode());
        }
    }
}
