package com.podbb.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;

public class PlayerService extends Service {
    public static final String ACTION_PLAY_PAUSE = "com.podbb.app.PLAY_PAUSE";
    private static final int NOTIF_ID = 1;

    private final IBinder binder = new LocalBinder();
    private MediaPlayer mediaPlayer;
    private Episode currentEpisode;
    private DatabaseHelper db;
    private boolean isPrepared = false;
    private float playbackSpeed = 1.0f;

    public class LocalBinder extends Binder {
        PlayerService getService() { return PlayerService.this; }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        db = new DatabaseHelper(this);
        initMediaPlayer();
    }

    private void initMediaPlayer() {
        if (mediaPlayer != null) {
            try { mediaPlayer.release(); } catch (Exception ignored) {}
        }
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        mediaPlayer.setOnPreparedListener(mp -> {
            isPrepared = true;
            if (currentEpisode != null && currentEpisode.positionSeconds > 0) {
                mp.seekTo(currentEpisode.positionSeconds * 1000);
            }
            mp.start();
            updateNotification();
        });
        mediaPlayer.setOnCompletionListener(mp -> {
            if (currentEpisode != null) db.markListened(currentEpisode.id);
            updateNotification();
        });
        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            isPrepared = false;
            return false;
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_PLAY_PAUSE.equals(intent.getAction())) {
            togglePlayPause();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void playEpisode(Episode episode) {
        try {
            currentEpisode = episode;
            isPrepared = false;
            if (mediaPlayer == null) initMediaPlayer();
            mediaPlayer.reset();
            if (episode.filePath != null && !episode.filePath.isEmpty()) {
                mediaPlayer.setDataSource(episode.filePath);
            } else {
                mediaPlayer.setDataSource(episode.audioUrl);
            }
            mediaPlayer.prepareAsync();
            startForeground(NOTIF_ID, buildNotification());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void togglePlayPause() {
        if (!isPrepared || mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            savePosition();
        } else {
            mediaPlayer.start();
        }
        updateNotification();
    }

    public boolean isPlaying() {
        return isPrepared && mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public int getCurrentPosition() {
        if (!isPrepared || mediaPlayer == null) return 0;
        try {
            return mediaPlayer.getCurrentPosition();
        } catch (Exception e) {
            return 0;
        }
    }

    public int getDuration() {
        if (!isPrepared || mediaPlayer == null) return 0;
        try {
            return mediaPlayer.getDuration();
        } catch (Exception e) {
            return 0;
        }
    }

    public void seekTo(int ms) {
        if (isPrepared && mediaPlayer != null) {
            try {
                mediaPlayer.seekTo(ms);
            } catch (Exception ignored) {}
        }
    }

    public void skipForward() {
        if (!isPrepared || mediaPlayer == null) return;
        try {
            int pos = mediaPlayer.getCurrentPosition() + 30000;
            int dur = mediaPlayer.getDuration();
            mediaPlayer.seekTo(Math.min(pos, dur));
        } catch (Exception ignored) {}
    }

    public void skipBack() {
        if (!isPrepared || mediaPlayer == null) return;
        try {
            int pos = mediaPlayer.getCurrentPosition() - 30000;
            mediaPlayer.seekTo(Math.max(pos, 0));
        } catch (Exception ignored) {}
    }

    public void setSpeed(float speed) {
        this.playbackSpeed = speed;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 23 && mediaPlayer != null) {
                android.media.PlaybackParams params = mediaPlayer.getPlaybackParams();
                params.setSpeed(speed);
                mediaPlayer.setPlaybackParams(params);
            }
        } catch (Exception ignored) {}
    }

    public float getSpeed() { return playbackSpeed; }

    public Episode getCurrentEpisode() { return currentEpisode; }

    public boolean isPrepared() { return isPrepared; }

    private void savePosition() {
        if (currentEpisode != null && isPrepared && mediaPlayer != null) {
            try {
                db.updatePosition(currentEpisode.id, mediaPlayer.getCurrentPosition() / 1000);
            } catch (Exception ignored) {}
        }
    }

    private Notification buildNotification() {
        Intent playPauseIntent = new Intent(this, PlayerService.class);
        playPauseIntent.setAction(ACTION_PLAY_PAUSE);
        PendingIntent playPausePi = PendingIntent.getService(this, 0, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent openIntent = new Intent(this, PlayerActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        String title = currentEpisode != null && currentEpisode.title != null ? currentEpisode.title : "PodBB";
        String status = isPrepared && mediaPlayer != null && mediaPlayer.isPlaying() ? "▶ Playing" : "⏸ Paused";

        Notification.Builder builder = new Notification.Builder(this)
                .setContentTitle(title)
                .setContentText(status)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(openPi)
                .addAction(android.R.drawable.ic_media_play, isPlaying() ? "Pause" : "Play", playPausePi)
                .setOngoing(true);
        return builder.build();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIF_ID, buildNotification());
        }
    }

    @Override
    public void onDestroy() {
        savePosition();
        if (mediaPlayer != null) {
            try {
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        if (db != null) db.close();
        super.onDestroy();
    }
}
