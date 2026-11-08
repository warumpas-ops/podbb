package com.podbb.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.List;

public class MainActivity extends Activity {
    private DatabaseHelper db;
    private List<Podcast> podcasts;
    private ArrayAdapter<Podcast> adapter;
    private ListView listView;
    private LinearLayout layoutEmpty;
    private TextView tvSubsCount;

    // Mini-player components
    private LinearLayout miniPlayerBar;
    private TextView tvMiniTitle;
    private TextView tvMiniPodcast;
    private Button btnMiniPlayPause;
    private PlayerService playerService;
    private boolean isBound = false;

    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            playerService = ((PlayerService.LocalBinder) service).getService();
            isBound = true;
            updateMiniPlayer();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            playerService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        db = new DatabaseHelper(this);

        listView = (ListView) findViewById(R.id.listSubscriptions);
        layoutEmpty = (LinearLayout) findViewById(R.id.layoutEmpty);
        tvSubsCount = (TextView) findViewById(R.id.tvSubsCount);

        Button btnNavSearch = (Button) findViewById(R.id.btnNavSearch);
        Button btnNavDownloads = (Button) findViewById(R.id.btnNavDownloads);
        Button btnEmptySearch = (Button) findViewById(R.id.btnEmptySearch);

        miniPlayerBar = (LinearLayout) findViewById(R.id.miniPlayerBar);
        tvMiniTitle = (TextView) findViewById(R.id.tvMiniTitle);
        tvMiniPodcast = (TextView) findViewById(R.id.tvMiniPodcast);
        btnMiniPlayPause = (Button) findViewById(R.id.btnMiniPlayPause);

        View.OnClickListener openSearch = v -> startActivity(new Intent(MainActivity.this, SearchActivity.class));
        btnNavSearch.setOnClickListener(openSearch);
        btnEmptySearch.setOnClickListener(openSearch);

        btnNavDownloads.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, DownloadsActivity.class)));

        findViewById(R.id.miniPlayerInfo).setOnClickListener(v -> {
            if (isBound && playerService != null && playerService.getCurrentEpisode() != null) {
                Episode ep = playerService.getCurrentEpisode();
                Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                intent.putExtra("episode_id", ep.id);
                intent.putExtra("episode_title", ep.title);
                intent.putExtra("episode_audio_url", ep.audioUrl);
                intent.putExtra("episode_file_path", ep.filePath != null ? ep.filePath : "");
                intent.putExtra("episode_duration", ep.durationSeconds);
                intent.putExtra("episode_position", ep.positionSeconds);
                intent.putExtra("podcast_title", ep.podcastTitle != null ? ep.podcastTitle : "");
                startActivity(intent);
            }
        });

        btnMiniPlayPause.setOnClickListener(v -> {
            if (isBound && playerService != null) {
                playerService.togglePlayPause();
                updateMiniPlayer();
            }
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Podcast p = podcasts.get(position);
            Intent intent = new Intent(MainActivity.this, PodcastDetailActivity.class);
            intent.putExtra("podcast_id", p.id);
            intent.putExtra("podcast_title", p.title);
            intent.putExtra("podcast_author", p.author);
            intent.putExtra("feed_url", p.feedUrl);
            intent.putExtra("itunes_id", p.itunesId);
            intent.putExtra("image_url", p.imageUrl);
            intent.putExtra("description", p.description);
            startActivity(intent);
        });

        // Bind PlayerService for mini-player
        Intent svcIntent = new Intent(this, PlayerService.class);
        bindService(svcIntent, serviceConn, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        updateMiniPlayer();
    }

    private void refresh() {
        podcasts = db.getAllSubscribed();
        int count = podcasts.size();
        tvSubsCount.setText(count + (count == 1 ? " podcast" : " podcasts"));

        if (podcasts.isEmpty()) {
            listView.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
        } else {
            listView.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
            adapter = new ArrayAdapter<Podcast>(this, R.layout.item_podcast, R.id.tvTitle, podcasts) {
                @Override
                public View getView(int position, View convertView, android.view.ViewGroup parent) {
                    View v = super.getView(position, convertView, parent);
                    Podcast p = podcasts.get(position);
                    ((TextView) v.findViewById(R.id.tvTitle)).setText(p.title);
                    ((TextView) v.findViewById(R.id.tvAuthor)).setText(p.author != null && !p.author.isEmpty() ? p.author : "Unknown Creator");
                    return v;
                }
            };
            listView.setAdapter(adapter);
        }
    }

    private void updateMiniPlayer() {
        if (isBound && playerService != null && playerService.getCurrentEpisode() != null) {
            Episode ep = playerService.getCurrentEpisode();
            miniPlayerBar.setVisibility(View.VISIBLE);
            tvMiniTitle.setText(ep.title != null ? ep.title : "Now Playing");
            tvMiniPodcast.setText(ep.podcastTitle != null ? ep.podcastTitle : "Podcast");
            btnMiniPlayPause.setText(playerService.isPlaying() ? "[pause]" : "[play]");
        } else {
            miniPlayerBar.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        if (isBound) {
            unbindService(serviceConn);
            isBound = false;
        }
        db.close();
        super.onDestroy();
    }
}
