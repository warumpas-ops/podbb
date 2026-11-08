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
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private DatabaseHelper db;
    private ImageLoader imageLoader;

    // Subscription grid
    private final List<Podcast> subscribedList = new ArrayList<>();
    private GridView gridSubscriptions;
    private GridCoverAdapter gridAdapter;

    // Search list
    private final List<Podcast> searchResults = new ArrayList<>();
    private ListView listPodcasts;
    private SearchListAdapter searchAdapter;

    private TextView tvSectionHeader;
    private EditText etSearch;
    private TextView tvEmpty;

    // Mini-player
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

        tvSectionHeader  = (TextView)  findViewById(R.id.tvSectionHeader);
        etSearch         = (EditText)  findViewById(R.id.etSearch);
        tvEmpty          = (TextView)  findViewById(R.id.tvEmpty);
        gridSubscriptions = (GridView) findViewById(R.id.gridSubscriptions);
        listPodcasts     = (ListView)  findViewById(R.id.listPodcasts);

        miniPlayerBar    = (LinearLayout) findViewById(R.id.miniPlayerBar);
        tvMiniTitle      = (TextView)  findViewById(R.id.tvMiniTitle);
        tvMiniPodcast    = (TextView)  findViewById(R.id.tvMiniPodcast);
        btnMiniPlayPause = (Button)    findViewById(R.id.btnMiniPlayPause);

        // Grid adapter — covers only
        gridAdapter = new GridCoverAdapter();
        gridSubscriptions.setAdapter(gridAdapter);

        // Search list adapter — full info rows
        searchAdapter = new SearchListAdapter();
        listPodcasts.setAdapter(searchAdapter);

        // Header downloads button
        Button btnHeaderDownloads = (Button) findViewById(R.id.btnHeaderDownloads);
        Button btnBottomDownloads = (Button) findViewById(R.id.btnBottomDownloads);
        Button btnBottomPlayer    = (Button) findViewById(R.id.btnBottomPlayer);

        View.OnClickListener openDownloads = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, DownloadsActivity.class));
            }
        };
        btnHeaderDownloads.setOnClickListener(openDownloads);
        btnBottomDownloads.setOnClickListener(openDownloads);

        btnBottomPlayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
            }
        });

        findViewById(R.id.miniPlayerClickArea).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findViewById(R.id.btnBottomPlayer).performClick();
            }
        });

        btnMiniPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isBound && playerService != null) {
                    playerService.togglePlayPause();
                    updateMiniPlayer();
                }
            }
        });

        // Tap on a cover in the grid
        gridSubscriptions.setOnItemClickListener(
            (parent, view, position, id) -> openPodcast(subscribedList.get(position))
        );

        // Tap on a search result
        listPodcasts.setOnItemClickListener(
            (parent, view, position, id) -> openPodcast(searchResults.get(position))
        );

        // Search input
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            String q = etSearch.getText().toString().trim();
            if (!q.isEmpty()) { performSearch(q); return true; }
            return false;
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 0 && isSearching) showSubscriptions();
            }
        });

        bindService(new Intent(this, PlayerService.class), serviceConn, BIND_AUTO_CREATE);
        FontHelper.apply(findViewById(R.id.rootView), this);
    }

    private void openPodcast(Podcast p) {
        Intent intent = new Intent(MainActivity.this, PodcastDetailActivity.class);
        intent.putExtra("podcast_id",     p.id);
        intent.putExtra("podcast_title",  p.title);
        intent.putExtra("podcast_author", p.author);
        intent.putExtra("feed_url",       p.feedUrl);
        intent.putExtra("itunes_id",      p.itunesId);
        intent.putExtra("image_url",      p.imageUrl);
        intent.putExtra("description",    p.description);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isSearching) showSubscriptions();
        updateMiniPlayer();
    }

    private void showSubscriptions() {
        isSearching = false;

        subscribedList.clear();
        subscribedList.addAll(db.getAllSubscribed());
        gridAdapter.notifyDataSetChanged();

        // Show grid, hide list
        gridSubscriptions.setVisibility(View.VISIBLE);
        listPodcasts.setVisibility(View.GONE);

        if (subscribedList.isEmpty()) {
            tvSectionHeader.setText("MY SUBSCRIPTIONS");
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("[ no podcasts subscribed ]\n\ntype keywords above to search\nor paste an RSS feed url");
            gridSubscriptions.setVisibility(View.GONE);
        } else {
            tvSectionHeader.setText("MY SUBSCRIPTIONS  (" + subscribedList.size() + ")");
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private void performSearch(final String query) {
        isSearching = true;
        tvSectionHeader.setText("SEARCHING \"" + query + "\"...");

        // Show list, hide grid
        gridSubscriptions.setVisibility(View.GONE);
        listPodcasts.setVisibility(View.VISIBLE);

        searchResults.clear();
        searchAdapter.notifyDataSetChanged();
        tvEmpty.setVisibility(View.VISIBLE);
        tvEmpty.setText("[ searching podcast directory... ]");

        new Thread(() -> {
            try {
                final List<Podcast> found = PodcastSearchEngine.search(query);
                runOnUiThread(() -> {
                    searchResults.clear();
                    searchResults.addAll(found);
                    searchAdapter.notifyDataSetChanged();
                    if (found.isEmpty()) {
                        tvSectionHeader.setText("NO RESULTS FOR \"" + query + "\"");
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText("[ no podcasts found ]\n\ntry another keyword or paste direct RSS url");
                    } else {
                        tvSectionHeader.setText("RESULTS (" + found.size() + ")");
                        tvEmpty.setVisibility(View.GONE);
                    }
                });
            } catch (final Exception e) {
                runOnUiThread(() -> {
                    tvSectionHeader.setText("SEARCH FAILED");
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("[ " + (e.getMessage() != null ? e.getMessage() : "offline") + " ]");
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

    // ── Grid adapter: square covers only ──────────────────────────────────

    private class GridCoverAdapter extends BaseAdapter {
        @Override public int getCount() { return subscribedList.size(); }
        @Override public Object getItem(int pos) { return subscribedList.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView img;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_grid_cover, parent, false);
                img = (ImageView) convertView.findViewById(R.id.imgGridCover);
                convertView.setTag(img);
            } else {
                img = (ImageView) convertView.getTag();
            }

            // Force square: width = one-third of screen
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int cellSize = (screenWidth - 12) / 3; // 3 cols, 3dp gaps
            ViewGroup.LayoutParams lp = convertView.getLayoutParams();
            lp.height = cellSize;
            convertView.setLayoutParams(lp);

            Podcast p = subscribedList.get(position);
            imageLoader.displayImage(p.imageUrl, img, R.drawable.ic_launcher);

            return convertView;
        }
    }

    // ── Search list adapter: full info rows ───────────────────────────────

    private class SearchListAdapter extends BaseAdapter {
        @Override public int getCount() { return searchResults.size(); }
        @Override public Object getItem(int pos) { return searchResults.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_search_result, parent, false);
            }
            Podcast p = searchResults.get(position);
            ImageView imgCoverThumb = (ImageView) convertView.findViewById(R.id.imgCoverThumb);
            TextView tvTypeTag  = (TextView) convertView.findViewById(R.id.tvTypeTag);
            TextView tvTitle    = (TextView) convertView.findViewById(R.id.tvTitle);
            TextView tvPreview  = (TextView) convertView.findViewById(R.id.tvPreview);
            TextView tvCategory = (TextView) convertView.findViewById(R.id.tvCategory);

            tvTypeTag.setText("[pod]");
            tvTitle.setText(p.title);
            String sub = p.author != null && !p.author.isEmpty() ? p.author
                       : (p.description != null ? p.description.replaceAll("<[^>]*>", "").trim() : "Podcast");
            tvPreview.setText(sub);
            tvCategory.setText("[tap to view]");

            imageLoader.displayImage(p.imageUrl, imgCoverThumb, R.drawable.ic_launcher);
            FontHelper.apply(convertView, MainActivity.this);
            return convertView;
        }
    }

    @Override
    protected void onDestroy() {
        if (isBound) { unbindService(serviceConn); isBound = false; }
        db.close();
        super.onDestroy();
    }
}
