package com.podbb.app;

import android.content.Context;
import java.io.File;

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
                    File dir = SettingsHelper.getDownloadDir(ctx);
                    if (!dir.exists()) dir.mkdirs();
                    String filename = "ep_" + episode.id + ".mp3";
                    File outFile = new File(dir, filename);

                    boolean ok = HttpHelper.downloadFile(episode.audioUrl, outFile, new HttpHelper.ProgressListener() {
                        @Override
                        public void onProgress(long bytesRead, long totalBytes, int percent) {
                            if (cb != null) cb.onProgress(percent);
                        }
                    });

                    if (ok) {
                        db.markDownloaded(episode.id, outFile.getAbsolutePath());
                        if (cb != null) cb.onComplete(outFile.getAbsolutePath());
                    } else {
                        if (cb != null) cb.onError("Failed to save downloaded file");
                    }
                } catch (Exception e) {
                    if (cb != null) cb.onError(e.getMessage());
                }
            }
        }).start();
    }
}
