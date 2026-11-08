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
        if (instance == null) instance = new ImageLoader(context.getApplicationContext());
        return instance;
    }

    private ImageLoader(Context context) {
        mainHandler = new Handler(Looper.getMainLooper());
        executor = Executors.newFixedThreadPool(3);

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = Math.max(2048, maxMemory / 6);
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        File external = context.getExternalCacheDir();
        diskCacheDir = new File(external != null ? external : context.getCacheDir(), "covers");
        if (!diskCacheDir.exists()) diskCacheDir.mkdirs();
    }

    public void displayImage(final String url, final ImageView imageView, final int placeholderRes) {
        if (url == null || url.trim().isEmpty()) {
            imageView.setImageResource(placeholderRes);
            return;
        }

        final String cleanUrl = url.trim();
        imageView.setTag(cleanUrl);

        // Memory cache hit → instant
        Bitmap cached = memoryCache.get(cleanUrl);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        imageView.setImageResource(placeholderRes);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                // Try disk cache first
                Bitmap bmp = loadFromDisk(cleanUrl);

                // Try network with fallback chain
                if (bmp == null) bmp = downloadWithFallbacks(cleanUrl);

                if (bmp != null) {
                    final Bitmap finalBmp = bmp;
                    memoryCache.put(cleanUrl, finalBmp);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Object tag = imageView.getTag();
                            if (tag instanceof String && tag.equals(cleanUrl)) {
                                imageView.setImageBitmap(finalBmp);
                            }
                        }
                    });
                }
            }
        });
    }

    /**
     * Try multiple URL strategies to fetch a cover image:
     * 1. Original URL as-is (using HttpHelper SSL bypass + redirect follow)
     * 2. If https://, try http:// version (bypass SSL entirely)
     * 3. If still nothing, try stripping query params
     */
    private Bitmap downloadWithFallbacks(String originalUrl) {
        // Strategy 1: original URL
        Bitmap bmp = downloadSingle(originalUrl);
        if (bmp != null) return bmp;

        // Strategy 2: https → http fallback (BB10 SSL issues)
        if (originalUrl.startsWith("https://")) {
            String httpUrl = originalUrl.replaceFirst("^https://", "http://");
            bmp = downloadSingle(httpUrl);
            if (bmp != null) {
                // cache under original url key too
                return bmp;
            }
        }

        // Strategy 3: strip query params (sometimes CDN requires clean URL)
        int qIdx = originalUrl.indexOf('?');
        if (qIdx > 0) {
            bmp = downloadSingle(originalUrl.substring(0, qIdx));
            if (bmp != null) return bmp;
        }

        return null;
    }

    private Bitmap downloadSingle(String urlStr) {
        File tempFile = null;
        try {
            String hash = hashKeyForDisk(urlStr);
            File targetFile = new File(diskCacheDir, hash);
            tempFile = new File(diskCacheDir, hash + ".tmp");

            // Use HttpHelper which has: SSL bypass, redirect follow, TLS1.2 force
            HttpURLConnection conn = HttpHelper.openWithRedirects(urlStr);
            // Note: openWithRedirects already called connect() internally

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                conn.disconnect();
                return null;
            }

            // Check content type — skip HTML error pages
            String ct = conn.getContentType();
            if (ct != null && ct.contains("text/html")) {
                conn.disconnect();
                return null;
            }

            InputStream in = conn.getInputStream();
            FileOutputStream out = new FileOutputStream(tempFile);
            byte[] buf = new byte[8192];
            int read, totalBytes = 0;
            final int maxBytes = 2 * 1024 * 1024; // 2MB cap for BB10 RAM

            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
                totalBytes += read;
                if (totalBytes > maxBytes) break;
            }
            out.flush();
            out.close();
            in.close();
            conn.disconnect();

            if (totalBytes < 100) { // too small to be a real image
                tempFile.delete();
                return null;
            }

            if (targetFile.exists()) targetFile.delete();
            tempFile.renameTo(targetFile);

            // Decode at display size (max 160×160 for BB10 Q5 list items)
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(targetFile.getAbsolutePath(), opts);

            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null; // not an image

            opts.inSampleSize = calculateInSampleSize(opts, 160, 160);
            opts.inJustDecodeBounds = false;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;

            return BitmapFactory.decodeFile(targetFile.getAbsolutePath(), opts);

        } catch (Exception e) {
            if (tempFile != null && tempFile.exists()) tempFile.delete();
            return null;
        }
    }

    private Bitmap loadFromDisk(String url) {
        try {
            File file = new File(diskCacheDir, hashKeyForDisk(url));
            if (file.exists() && file.length() > 100) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqW, int reqH) {
        int h = options.outHeight, w = options.outWidth, s = 1;
        if (h > reqH || w > reqW) {
            while ((h / 2 / s) >= reqH && (w / 2 / s) >= reqW) s *= 2;
        }
        return s;
    }

    private String hashKeyForDisk(String key) {
        try {
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.update(key.getBytes("UTF-8"));
            byte[] d = m.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(key.hashCode());
        }
    }
}
