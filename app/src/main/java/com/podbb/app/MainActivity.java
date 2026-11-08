package com.podbb.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private DatabaseHelper db;
    private final List<Podcast> displayedList = new ArrayList<>();
    private final List<Podcast> subscribedList = new ArrayList<>();
    private PodcastListAdapter adapter;
    private ImageLoader imageLoader;

    private TextView tvSectionHeader;
    private EditText etSearch;
    private ListView listPodcasts;
    private TextView tvEmpty;

    // Mini-player bar
    private LinearLayout miniPlayerBar;
    private TextView tvMiniTitle;
    private TextView tvMiniPodcast;
    private Button btnMiniPlayPause;
    private PlayerService playerService;
    private boolean isBound = false;

    private boolean isSearching = false;

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
        imageLoader = ImageLoader.getInstance(this);

        tvSectionHeader = (TextView) findViewById(R.id.tvSectionHeader);
        etSearch = (EditText) findViewById(R.id.etSearch);
        listPodcasts = (ListView) findViewById(R.id.listPodcasts);
        tvEmpty = (TextView) findViewById(R.id.tvEmpty);

        Button btnHeaderDownloads = (Button) findViewById(R.id.btnHeaderDownloads);
        Button btnBottomDownloads = (Button) findViewById(R.id.btnBottomDownloads);
        Button btnBottomPlayer = (Button) findViewById(R.id.btnBottomPlayer);

        miniPlayerBar = (LinearLayout) findViewById(R.id.miniPlayerBar);
        tvMiniTitle = (TextView) findViewById(R.id.tvMiniTitle);
        tvMiniPodcast = (TextView) findViewById(R.id.tvMiniPodcast);
        btnMiniPlayPause = (Button) findViewById(R.id.btnMiniPlayPause);

        adapter = new PodcastListAdapter();
        listPodcasts.setAdapter(adapter);

        View.OnClickListener openDownloads = v -> startActivity(new Intent(MainActivity.this, DownloadsActivity.class));
        btnHeaderDownloads.setOnClickListener(openDownloads);
        btnBottomDownloads.setOnClickListener(openDownloads);

        btnBottomPlayer.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
            if (isBound && playerService != null && playerService.getCurrentEpisode() != null) {
                Episode ep = playerService.getCurrentEpisode();
                intent.putExtra("episode_id", ep.id);
                intent.putExtra("episode_title", ep.title);
                intent.putExtra("episode_audio_url", ep.audioUrl);
                intent.putExtra("episode_file_path", ep.filePath != null ? ep.filePath : "");
                intent.putExtra("episode_duration", ep.durationSeconds);
                intent.putExtra("episode_position", ep.positionSeconds);
                intent.putExtra("podcast_title", ep.podcastTitle != null ? ep.podcastTitle : "");
            }
            startActivity(intent);
        });

        findViewById(R.id.miniPlayerClickArea).setOnClickListener(v -> btnBottomPlayer.performClick());
        btnMiniPlayPause.setOnClickListener(v -> {
            if (isBound && playerService != null) {
                playerService.togglePlayPause();
                updateMiniPlayer();
            }
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            String q = etSearch.getText().toString().trim();
            if (!q.isEmpty()) {
                performSearch(q);
                return true;
            }
            return false;
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 0 && isSearching) {
                    showSubscriptions();
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        listPodcasts.setOnItemClickListener((parent, view, position, id) -> {
            Podcast p = displayedList.get(position);
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

        Intent svcIntent = new Intent(this, PlayerService.class);
        bindService(svcIntent, serviceConn, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isSearching) {
            showSubscriptions();
        }
        updateMiniPlayer();
    }

    private void showSubscriptions() {
        isSearching = false;
        tvSectionHeader.setText("MY SUBSCRIPTIONS");
        subscribedList.clear();
        subscribedList.addAll(db.getAllSubscribed());

        displayedList.clear();
        displayedList.addAll(subscribedList);
        adapter.notifyDataSetChanged();

        if (displayedList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("[ no podcasts subscribed ]\n\ntype keywords above to search\nor paste an RSS feed url");
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private void performSearch(final String query) {
        isSearching = true;
        tvSectionHeader.setText("SEARCHING \"" + query + "\"...");
        displayedList.clear();
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(View.VISIBLE);
        tvEmpty.setText("[ searching podcast directory... ]");

        new Thread(() -> {
            try {
                final List<Podcast> found = PodcastSearchEngine.search(query);
                runOnUiThread(() -> {
                    displayedList.clear();
                    displayedList.addAll(found);
                    adapter.notifyDataSetChanged();

                    if (found.isEmpty()) {
                        tvSectionHeader.setText("NO RESULTS FOR \"" + query + "\"");
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText("[ no podcasts found ]\n\ntry another keyword or paste direct RSS url");
                    } else {
                        tvSectionHeader.setText("SEARCH RESULTS (" + found.size() + ")");
                        tvEmpty.setVisibility(View.GONE);
                    }
                });
            } catch (final Exception e) {
                runOnUiThread(() -> {
                    tvSectionHeader.setText("SEARCH FAILED");
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("[ search error: " + (e.getMessage() != null ? e.getMessage() : "offline") + " ]");
                });
            }
        }).start();
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

    private class PodcastListAdapter extends BaseAdapter {
        @Override
        public int getCount() { return displayedList.size(); }
        @Override
        public Object getItem(int position) { return displayedList.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_podcast, parent, false);
            }
            Podcast p = displayedList.get(position);
            ImageView imgCoverThumb = (ImageView) convertView.findViewById(R.id.imgCoverThumb);
            TextView tvTypeTag = (TextView) convertView.findViewById(R.id.tvTypeTag);
            TextView tvTitle = (TextView) convertView.findViewById(R.id.tvTitle);
            TextView tvPreview = (TextView) convertView.findViewById(R.id.tvPreview);
            TextView tvCategory = (TextView) convertView.findViewById(R.id.tvCategory);

            tvTypeTag.setText(p.isSubscribed ? "[saved]" : "[pod]");
            tvTitle.setText(p.title);
            
            String creatorOrDesc = p.author != null && !p.author.isEmpty() ? p.author : (p.description != null ? p.description : "Podcast");
            tvPreview.setText(creatorOrDesc.replaceAll("<[^>]*>", "").trim());
            tvCategory.setText(p.isSubscribed ? "✓ Subscribed" : "[tap to view]");

            // Load cover picture asynchronously
            imageLoader.displayImage(p.imageUrl, imgCoverThumb, R.drawable.ic_launcher);

            return convertView;
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
