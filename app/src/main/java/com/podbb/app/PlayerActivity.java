package com.podbb.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class PlayerActivity extends Activity {
    private PlayerService playerService;
    private boolean bound = false;
    private final Handler handler = new Handler();
    private DatabaseHelper db;

    private ImageView imgArtwork;
    private TextView tvSourceBadge;
    private TextView tvEpisodeTitle;
    private TextView tvPodcastName;
    private TextView tvCurrentTime;
    private TextView tvDuration;
    private SeekBar seekBar;
    private Button btnPlayPause;
    private Button btnDownload;

    private float currentSpeed = 1.0f;
    private long episodeId;
    private String episodeTitle, audioUrl, filePath, podcastTitle, artworkUrl;
    private int durationSeconds, positionSeconds;

    private final ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            playerService = ((PlayerService.LocalBinder) service).getService();
            bound = true;

            Episode curr = playerService.getCurrentEpisode();
            if (curr == null || curr.id != episodeId || !playerService.isPrepared()) {
                Episode ep = new Episode();
                ep.id = episodeId;
                ep.title = episodeTitle;
                ep.audioUrl = audioUrl;
                ep.filePath = filePath;
                ep.durationSeconds = durationSeconds;
                ep.positionSeconds = positionSeconds;
                ep.podcastTitle = podcastTitle;
                btnPlayPause.setText("[Connecting...]");
                playerService.playEpisode(ep);
            }
            startSeekBarUpdate();
            updatePlayPauseBtn();
        }

        public void onServiceDisconnected(ComponentName name) {
            bound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        db = new DatabaseHelper(this);

        episodeId = getIntent().getLongExtra("episode_id", 0);
        episodeTitle = getIntent().getStringExtra("episode_title");
        audioUrl = getIntent().getStringExtra("episode_audio_url");
        filePath = getIntent().getStringExtra("episode_file_path");
        durationSeconds = getIntent().getIntExtra("episode_duration", 0);
        positionSeconds = getIntent().getIntExtra("episode_position", 0);
        podcastTitle = getIntent().getStringExtra("podcast_title");
        artworkUrl = getIntent().getStringExtra("podcast_artwork");

        imgArtwork = (ImageView) findViewById(R.id.imgArtwork);
        tvSourceBadge = (TextView) findViewById(R.id.tvSourceBadge);
        tvEpisodeTitle = (TextView) findViewById(R.id.tvEpisodeTitle);
        tvPodcastName = (TextView) findViewById(R.id.tvPodcastName);
        tvCurrentTime = (TextView) findViewById(R.id.tvCurrentTime);
        tvDuration = (TextView) findViewById(R.id.tvDuration);
        seekBar = (SeekBar) findViewById(R.id.seekBar);
        btnPlayPause = (Button) findViewById(R.id.btnPlayPause);
        btnDownload = (Button) findViewById(R.id.btnDownload);

        Button btnBack = (Button) findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        tvEpisodeTitle.setText(episodeTitle != null ? episodeTitle : "Episode");
        tvPodcastName.setText(podcastTitle != null ? podcastTitle : "Podcast");

        updateSourceBadge();

        // Load cover artwork via high-speed disk/memory ImageLoader
        ImageLoader.getInstance(this).displayImage(artworkUrl, imgArtwork, R.drawable.ic_launcher);

        if (durationSeconds > 0) {
            seekBar.setMax(durationSeconds * 1000);
            tvDuration.setText(formatMs(durationSeconds * 1000));
        }

        btnPlayPause.setOnClickListener(v -> {
            if (bound && playerService != null) {
                if (!playerService.isPrepared()) {
                    btnPlayPause.setText("[Connecting...]");
                    Episode ep = new Episode();
                    ep.id = episodeId;
                    ep.title = episodeTitle;
                    ep.audioUrl = audioUrl;
                    ep.filePath = filePath;
                    ep.durationSeconds = durationSeconds;
                    ep.positionSeconds = positionSeconds;
                    ep.podcastTitle = podcastTitle;
                    playerService.playEpisode(ep);
                } else {
                    playerService.togglePlayPause();
                    updatePlayPauseBtn();
                }
            }
        });

        ((Button) findViewById(R.id.btnSkipBack)).setOnClickListener(v -> {
            if (bound && playerService != null) playerService.skipBack();
        });

        ((Button) findViewById(R.id.btnSkipForward)).setOnClickListener(v -> {
            if (bound && playerService != null) playerService.skipForward();
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && bound && playerService != null) {
                    playerService.seekTo(progress);
                }
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });

        setupSpeedButtons();

        // Download offline button
        btnDownload.setOnClickListener(v -> {
            if (filePath != null && !filePath.isEmpty()) {
                Toast.makeText(this, "Already downloaded on device", Toast.LENGTH_SHORT).show();
                return;
            }
            btnDownload.setText("Downloading...");
            btnDownload.setEnabled(false);
            Episode ep = new Episode();
            ep.id = episodeId;
            ep.audioUrl = audioUrl;

            EpisodeDownloader.download(this, ep, db, new EpisodeDownloader.Callback() {
                @Override
                public void onProgress(final int percent) {
                    runOnUiThread(() -> btnDownload.setText("Downloading " + percent + "%"));
                }

                @Override
                public void onComplete(final String path) {
                    runOnUiThread(() -> {
                        filePath = path;
                        btnDownload.setText("✓ Downloaded Offline");
                        btnDownload.setEnabled(true);
                        updateSourceBadge();
                        Toast.makeText(PlayerActivity.this, "Saved to device for offline playback", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(final String error) {
                    runOnUiThread(() -> {
                        btnDownload.setText("[↓ download for offline playback]");
                        btnDownload.setEnabled(true);
                        Toast.makeText(PlayerActivity.this, "Download error: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });

        Intent serviceIntent = new Intent(this, PlayerService.class);
        startService(serviceIntent);
        bindService(serviceIntent, connection, BIND_AUTO_CREATE);
    }

    private void updateSourceBadge() {
        if (filePath != null && !filePath.isEmpty()) {
            tvSourceBadge.setText("[LOCAL OFFLINE]");
            tvSourceBadge.setTextColor(0xFF00A8DF);
            btnDownload.setText("✓ Downloaded Offline");
        } else {
            tvSourceBadge.setText("[STREAMING]");
            tvSourceBadge.setTextColor(0xFF888888);
            btnDownload.setText("[↓ download for offline playback]");
        }
    }

    private void setupSpeedButtons() {
        int[] ids = {R.id.btnSpeed075, R.id.btnSpeed100, R.id.btnSpeed125, R.id.btnSpeed150, R.id.btnSpeed200};
        final float[] speeds = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        for (int i = 0; i < ids.length; i++) {
            final float speed = speeds[i];
            final Button btn = (Button) findViewById(ids[i]);
            btn.setOnClickListener(v -> {
                if (bound && playerService != null) {
                    playerService.setSpeed(speed);
                    currentSpeed = speed;
                    updateSpeedButtons();
                }
            });
        }
    }

    private void updateSpeedButtons() {
        int[] ids = {R.id.btnSpeed075, R.id.btnSpeed100, R.id.btnSpeed125, R.id.btnSpeed150, R.id.btnSpeed200};
        float[] speeds = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        for (int i = 0; i < ids.length; i++) {
            Button btn = (Button) findViewById(ids[i]);
            btn.setTextColor(speeds[i] == currentSpeed ? 0xFF00A8DF : 0xFF888888);
        }
    }

    private void updatePlayPauseBtn() {
        if (bound && playerService != null) {
            if (!playerService.isPrepared()) {
                btnPlayPause.setText("[Connecting...]");
            } else {
                btnPlayPause.setText(playerService.isPlaying() ? "[Pause]" : "[Play]");
            }
        }
    }

    private void startSeekBarUpdate() {
        handler.postDelayed(new Runnable() {
            public void run() {
                if (bound && playerService != null && playerService.isPrepared()) {
                    int pos = playerService.getCurrentPosition();
                    int dur = playerService.getDuration();
                    if (dur > 0) {
                        seekBar.setMax(dur);
                        seekBar.setProgress(pos);
                        tvCurrentTime.setText(formatMs(pos));
                        tvDuration.setText(formatMs(dur));
                    }
                    updatePlayPauseBtn();
                }
                handler.postDelayed(this, 500);
            }
        }, 500);
    }

    private String formatMs(int ms) {
        int s = ms / 1000;
        int h = s / 3600;
        int m = (s % 3600) / 60;
        int sec = s % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, sec);
        return String.format("%d:%02d", m, sec);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        db.close();
        super.onDestroy();
    }
}
