package com.podbb.app;

import android.content.Context;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class EpisodeDownloader {
    public interface Callback {
        void onProgress(int percent);
        void onComplete(String filePath);
        void onError(String error);
    }

    public static void download(final Context ctx, final Episode episode, final DatabaseHelper db, final Callback cb) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    File dir = new File(Environment.getExternalStorageDirectory(), "PodBB");
                    if (!dir.exists()) dir.mkdirs();
                    String filename = "ep_" + episode.id + ".mp3";
                    File outFile = new File(dir, filename);

                    URL url = new URL(episode.audioUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(60000);
                    conn.setInstanceFollowRedirects(true);
                    int total = conn.getContentLength();
                    InputStream in = conn.getInputStream();
                    FileOutputStream out = new FileOutputStream(outFile);
                    byte[] buf = new byte[8192];
                    int read;
                    long downloaded = 0;
                    while ((read = in.read(buf)) != -1) {
                        out.write(buf, 0, read);
                        downloaded += read;
                        if (total > 0 && cb != null) cb.onProgress((int)(downloaded * 100 / total));
                    }
                    out.flush();
                    out.close();
                    in.close();
                    conn.disconnect();
                    db.markDownloaded(episode.id, outFile.getAbsolutePath());
                    if (cb != null) cb.onComplete(outFile.getAbsolutePath());
                } catch (Exception e) {
                    if (cb != null) cb.onError(e.getMessage());
                }
            }
        }).start();
    }
}
