package com.podbb.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

public class PlayerActivity extends Activity {
    private PlayerService playerService;
    private boolean bound = false;
    private Handler handler = new Handler();
    private SeekBar seekBar;
    private TextView tvCurrentTime, tvDuration, tvPlayPause, tvEpisodeTitle, tvPodcastName;
    private float currentSpeed = 1.0f;

    private long episodeId;
    private String episodeTitle, audioUrl, filePath, podcastTitle;
    private int durationSeconds, positionSeconds;

    private ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            playerService = ((PlayerService.LocalBinder) service).getService();
            bound = true;
            Episode curr = playerService.getCurrentEpisode();
            if (curr == null || curr.id != episodeId) {
                Episode ep = new Episode();
                ep.id = episodeId;
                ep.title = episodeTitle;
                ep.audioUrl = audioUrl;
                ep.filePath = filePath;
                ep.durationSeconds = durationSeconds;
                ep.positionSeconds = positionSeconds;
                playerService.playEpisode(ep);
            }
            startSeekBarUpdate();
        }
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        episodeId = getIntent().getLongExtra("episode_id", 0);
        episodeTitle = getIntent().getStringExtra("episode_title");
        audioUrl = getIntent().getStringExtra("episode_audio_url");
        filePath = getIntent().getStringExtra("episode_file_path");
        durationSeconds = getIntent().getIntExtra("episode_duration", 0);
        positionSeconds = getIntent().getIntExtra("episode_position", 0);
        podcastTitle = getIntent().getStringExtra("podcast_title");

        tvEpisodeTitle = (TextView) findViewById(R.id.tvEpisodeTitle);
        tvPodcastName = (TextView) findViewById(R.id.tvPodcastName);
        tvCurrentTime = (TextView) findViewById(R.id.tvCurrentTime);
        tvDuration = (TextView) findViewById(R.id.tvDuration);
        tvPlayPause = (TextView) findViewById(R.id.btnPlayPause);
        seekBar = (SeekBar) findViewById(R.id.seekBar);

        tvEpisodeTitle.setText(episodeTitle);
        tvPodcastName.setText(podcastTitle);

        if (durationSeconds > 0) {
            seekBar.setMax(durationSeconds * 1000);
            tvDuration.setText(formatMs(durationSeconds * 1000));
        }

        ((TextView) findViewById(R.id.tvBack)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });

        tvPlayPause.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (bound) {
                    playerService.togglePlayPause();
                    updatePlayPauseBtn();
                }
            }
        });

        ((TextView) findViewById(R.id.btnSkipBack)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { if (bound) playerService.skipBack(); }
        });

        ((TextView) findViewById(R.id.btnSkipForward)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { if (bound) playerService.skipForward(); }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && bound) playerService.seekTo(progress);
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });

        setupSpeedButtons();

        Intent serviceIntent = new Intent(this, PlayerService.class);
        startService(serviceIntent);
        bindService(serviceIntent, connection, BIND_AUTO_CREATE);
    }

    private void setupSpeedButtons() {
        int[] ids = {R.id.btnSpeed075, R.id.btnSpeed100, R.id.btnSpeed125, R.id.btnSpeed150, R.id.btnSpeed200};
        final float[] speeds = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        for (int i = 0; i < ids.length; i++) {
            final float speed = speeds[i];
            final TextView btn = (TextView) findViewById(ids[i]);
            btn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (bound) {
                        playerService.setSpeed(speed);
                        currentSpeed = speed;
                        updateSpeedButtons();
                    }
                }
            });
        }
    }

    private void updateSpeedButtons() {
        int[] ids = {R.id.btnSpeed075, R.id.btnSpeed100, R.id.btnSpeed125, R.id.btnSpeed150, R.id.btnSpeed200};
        float[] speeds = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        for (int i = 0; i < ids.length; i++) {
            TextView btn = (TextView) findViewById(ids[i]);
            btn.setTextColor(speeds[i] == currentSpeed ? 0xFFA8D8A8 : 0xFF555A64);
        }
    }

    private void updatePlayPauseBtn() {
        if (bound) {
            tvPlayPause.setText(playerService.isPlaying() ? "[pause]" : "[play]");
        }
    }

    private void startSeekBarUpdate() {
        handler.postDelayed(new Runnable() {
            public void run() {
                if (bound && playerService.isPrepared()) {
                    int pos = playerService.getCurrentPosition();
                    int dur = playerService.getDuration();
                    seekBar.setMax(dur);
                    seekBar.setProgress(pos);
                    tvCurrentTime.setText(formatMs(pos));
                    tvDuration.setText(formatMs(dur));
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
        super.onDestroy();
    }
}
