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
import java.util.ArrayList;
import java.util.List;

public class PlayerActivity extends Activity {
    public static class Cue {
        public long startMs;
        public long endMs;
        public String speaker;
        public String text;
    }

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
    private Button btnCaptions;
    private android.widget.LinearLayout panelCaptions;
    private TextView tvCaptionsContent;
    private boolean captionsVisible = false;

    private String transcriptUrl, transcriptType;
    private final List<Cue> transcriptCues = new ArrayList<>();
    private boolean transcriptLoaded = false;
    private boolean transcriptLoading = false;
    private int lastCueIndex = -2;

    private float currentSpeed = 1.0f;
    private long episodeId;
    private String episodeTitle, audioUrl, filePath, podcastTitle, artworkUrl;
    private int durationSeconds, positionSeconds;

    private final ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            playerService = ((PlayerService.LocalBinder) service).getService();
            bound = true;

            Episode curr = playerService.getCurrentEpisode();
            if (curr != null) {
                if ((artworkUrl == null || artworkUrl.isEmpty()) && curr.imageUrl != null && !curr.imageUrl.isEmpty()) {
                    artworkUrl = curr.imageUrl;
                    ImageLoader.getInstance(PlayerActivity.this).displayImage(artworkUrl, imgArtwork, R.drawable.ic_launcher);
                }
                if ((podcastTitle == null || podcastTitle.isEmpty()) && curr.podcastTitle != null && !curr.podcastTitle.isEmpty()) {
                    podcastTitle = curr.podcastTitle;
                    tvPodcastName.setText(podcastTitle);
                }
            }

            if (curr == null || curr.id != episodeId || !playerService.isPrepared()) {
                Episode ep = new Episode();
                ep.id = episodeId;
                ep.title = episodeTitle;
                ep.audioUrl = audioUrl;
                ep.filePath = filePath;
                ep.durationSeconds = durationSeconds;
                ep.positionSeconds = positionSeconds;
                ep.podcastTitle = podcastTitle;
                ep.imageUrl = artworkUrl;
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
        transcriptUrl = getIntent().getStringExtra("transcript_url");
        transcriptType = getIntent().getStringExtra("transcript_type");

        imgArtwork = (ImageView) findViewById(R.id.imgArtwork);
        tvSourceBadge = (TextView) findViewById(R.id.tvSourceBadge);
        tvEpisodeTitle = (TextView) findViewById(R.id.tvEpisodeTitle);
        tvPodcastName = (TextView) findViewById(R.id.tvPodcastName);
        tvCurrentTime = (TextView) findViewById(R.id.tvCurrentTime);
        tvDuration = (TextView) findViewById(R.id.tvDuration);
        seekBar = (SeekBar) findViewById(R.id.seekBar);
        btnPlayPause = (Button) findViewById(R.id.btnPlayPause);
        btnDownload = (Button) findViewById(R.id.btnDownload);
        btnCaptions = (Button) findViewById(R.id.btnCaptions);
        panelCaptions = (android.widget.LinearLayout) findViewById(R.id.panelCaptions);
        tvCaptionsContent = (TextView) findViewById(R.id.tvCaptionsContent);

        Button btnBack = (Button) findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // [CC] button — toggle captions panel visibility and load transcript if needed
        if (btnCaptions != null) {
            btnCaptions.setOnClickListener(v -> {
                captionsVisible = !captionsVisible;
                if (panelCaptions != null) {
                    panelCaptions.setVisibility(captionsVisible ? android.view.View.VISIBLE : android.view.View.GONE);
                }
                btnCaptions.setTextColor(captionsVisible ? 0xFF00A8DF : 0xFF888888);
                if (captionsVisible && !transcriptLoaded && !transcriptLoading) {
                    loadTranscript();
                }
            });
        }

        tvEpisodeTitle.setText(episodeTitle != null ? episodeTitle : "Episode");
        tvPodcastName.setText(podcastTitle != null ? podcastTitle : "Podcast");

        updateSourceBadge();

        // Load cover artwork via high-speed disk/memory ImageLoader
        ImageLoader.getInstance(this).displayImage(artworkUrl, imgArtwork, R.drawable.ic_launcher);

        // If artworkUrl is not present yet, resolve from database asynchronously
        if ((artworkUrl == null || artworkUrl.isEmpty()) && episodeId > 0) {
            new Thread(() -> {
                Episode dbEp = db.getEpisode(episodeId);
                if (dbEp != null && dbEp.podcastId > 0) {
                    Podcast p = db.getPodcast(dbEp.podcastId);
                    if (p != null && p.imageUrl != null && !p.imageUrl.isEmpty()) {
                        artworkUrl = p.imageUrl;
                        runOnUiThread(() -> {
                            ImageLoader.getInstance(PlayerActivity.this).displayImage(artworkUrl, imgArtwork, R.drawable.ic_launcher);
                        });
                    }
                }
            }).start();
        }

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
                    ep.imageUrl = artworkUrl;
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
        FontHelper.apply(findViewById(android.R.id.content), this);
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
                    if (captionsVisible) {
                        updateCaptions(pos);
                    }
                    updatePlayPauseBtn();
                }
                handler.postDelayed(this, 500);
            }
        }, 500);
    }

    private void loadTranscript() {
        if (transcriptUrl == null || transcriptUrl.trim().isEmpty()) {
            if (tvCaptionsContent != null) {
                tvCaptionsContent.setText("[No transcript provided in RSS feed]\n\nThis show does not publish synchronized <podcast:transcript> files.");
            }
            return;
        }

        transcriptLoading = true;
        if (tvCaptionsContent != null) {
            tvCaptionsContent.setText("[Fetching transcript...]");
        }

        new Thread(() -> {
            try {
                String raw = HttpHelper.fetchString(transcriptUrl);
                List<Cue> cues = parseTranscript(raw, transcriptType);
                runOnUiThread(() -> {
                    transcriptLoading = false;
                    transcriptLoaded = true;
                    transcriptCues.clear();
                    transcriptCues.addAll(cues);
                    if (transcriptCues.isEmpty()) {
                        tvCaptionsContent.setText("[Transcript could not be parsed]\n\n" + (raw.length() > 200 ? raw.substring(0, 200) + "..." : raw));
                    } else {
                        lastCueIndex = -2;
                        if (bound && playerService != null && playerService.isPrepared()) {
                            updateCaptions(playerService.getCurrentPosition());
                        } else {
                            updateCaptions(0);
                        }
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    transcriptLoading = false;
                    tvCaptionsContent.setText("[Transcript fetch error: " + (e.getMessage() != null ? e.getMessage() : "Network error") + "]");
                });
            }
        }).start();
    }

    private void updateCaptions(int currentMs) {
        if (!captionsVisible || transcriptCues.isEmpty() || tvCaptionsContent == null) return;

        int activeIdx = -1;
        for (int i = 0; i < transcriptCues.size(); i++) {
            Cue c = transcriptCues.get(i);
            if (currentMs >= c.startMs && currentMs <= c.endMs) {
                activeIdx = i;
                break;
            }
            if (c.startMs > currentMs) {
                activeIdx = Math.max(0, i - 1);
                break;
            }
        }
        if (activeIdx == -1 && !transcriptCues.isEmpty()) {
            activeIdx = transcriptCues.size() - 1;
        }

        if (activeIdx == lastCueIndex) return; // avoid re-rendering same cue
        lastCueIndex = activeIdx;

        Cue cur = transcriptCues.get(activeIdx);
        StringBuilder sb = new StringBuilder();

        // Previous line (dim gray)
        if (activeIdx > 0) {
            Cue prev = transcriptCues.get(activeIdx - 1);
            sb.append("<font color='#666666'>");
            if (prev.speaker != null && !prev.speaker.isEmpty()) sb.append("[").append(prev.speaker).append("] ");
            sb.append(prev.text).append("</font><br><br>");
        }

        // Active line (bold cyan)
        sb.append("<font color='#00A8DF'><b>");
        if (cur.speaker != null && !cur.speaker.isEmpty()) sb.append("[").append(cur.speaker).append("] ");
        sb.append(cur.text).append("</b></font>");

        // Next line (dim gray)
        if (activeIdx < transcriptCues.size() - 1) {
            Cue next = transcriptCues.get(activeIdx + 1);
            sb.append("<br><br><font color='#666666'>");
            if (next.speaker != null && !next.speaker.isEmpty()) sb.append("[").append(next.speaker).append("] ");
            sb.append(next.text).append("</font>");
        }

        tvCaptionsContent.setText(android.text.Html.fromHtml(sb.toString()));
    }

    private List<Cue> parseTranscript(String raw, String type) {
        List<Cue> list = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return list;

        raw = raw.trim();

        // 1. Try JSON (PodcastIndex JSON or similar)
        if (raw.startsWith("{") || raw.startsWith("[")) {
            try {
                if (raw.startsWith("{")) {
                    org.json.JSONObject obj = new org.json.JSONObject(raw);
                    org.json.JSONArray segments = obj.optJSONArray("segments");
                    if (segments == null) segments = obj.optJSONArray("cues");
                    if (segments != null) {
                        for (int i = 0; i < segments.length(); i++) {
                            org.json.JSONObject s = segments.getJSONObject(i);
                            Cue cue = new Cue();
                            double start = s.optDouble("startTime", s.optDouble("start", 0));
                            double end = s.optDouble("endTime", s.optDouble("end", start + 3));
                            cue.startMs = (long) (start * 1000);
                            cue.endMs = (long) (end * 1000);
                            cue.speaker = s.optString("speaker", "");
                            cue.text = s.optString("body", s.optString("text", ""));
                            if (!cue.text.isEmpty()) list.add(cue);
                        }
                    }
                }
                if (!list.isEmpty()) return list;
            } catch (Exception ignored) {}
        }

        // 2. Try WebVTT / SRT
        try {
            String[] lines = raw.split("\r?\n");
            Cue currentCue = null;
            java.util.regex.Pattern timePattern = java.util.regex.Pattern.compile(
                "(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})[.,](\\d{3})\\s*-->\\s*(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})[.,](\\d{3})"
            );

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("WEBVTT") || line.startsWith("NOTE")) {
                    if (currentCue != null && currentCue.text != null && !currentCue.text.isEmpty()) {
                        list.add(currentCue);
                        currentCue = null;
                    }
                    continue;
                }

                java.util.regex.Matcher m = timePattern.matcher(line);
                if (m.find()) {
                    if (currentCue != null && currentCue.text != null && !currentCue.text.isEmpty()) {
                        list.add(currentCue);
                    }
                    currentCue = new Cue();
                    long startH = m.group(1) != null ? Long.parseLong(m.group(1)) : 0;
                    long startM = Long.parseLong(m.group(2));
                    long startS = Long.parseLong(m.group(3));
                    long startMs = Long.parseLong(m.group(4));
                    currentCue.startMs = (startH * 3600 + startM * 60 + startS) * 1000 + startMs;

                    long endH = m.group(5) != null ? Long.parseLong(m.group(5)) : 0;
                    long endM = Long.parseLong(m.group(6));
                    long endS = Long.parseLong(m.group(7));
                    long endMs = Long.parseLong(m.group(8));
                    currentCue.endMs = (endH * 3600 + endM * 60 + endS) * 1000 + endMs;
                    currentCue.text = "";
                } else if (currentCue != null) {
                    // Check for speaker like <v Speaker Name> or Speaker:
                    String text = line.replaceAll("<[^>]*>", "");
                    int colon = text.indexOf(':');
                    if (colon > 0 && colon < 25 && currentCue.speaker == null && !text.contains("-->")) {
                        currentCue.speaker = text.substring(0, colon).trim();
                        text = text.substring(colon + 1).trim();
                    }
                    if (currentCue.text.isEmpty()) {
                        currentCue.text = text;
                    } else {
                        currentCue.text += " " + text;
                    }
                }
            }
            if (currentCue != null && currentCue.text != null && !currentCue.text.isEmpty()) {
                list.add(currentCue);
            }
        } catch (Exception ignored) {}

        return list;
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
