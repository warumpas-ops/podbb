package com.podbb.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class PodcastDetailActivity extends Activity {
    private DatabaseHelper db;
    private Podcast podcast;
    private final List<Episode> episodes = new ArrayList<>();
    private EpisodeAdapter adapter;
    private Button btnSubscribe;

    // Sticky podcast panel & modes
    private View panelStickyPodcast;
    private View panelExpanded;
    private View panelMinimized;
    private boolean isMinimized = false;

    // Expanded views
    private ImageView imgPodcastCover;
    private TextView tvPodcastTitle;
    private TextView tvAuthor;
    private TextView tvGenrePill;
    private TextView tvEpisodesStatus;

    // Minimized views (exact same shape as item_search_result)
    private ImageView imgPodcastCoverMini;
    private TextView tvPodcastTitleMini;
    private TextView tvAuthorMini;
    private TextView tvStatusMini;

    private ImageLoader imageLoader;
    private boolean isSubscribed = false;
    // Best resolved cover URL — updated from RSS feed after load, passed to PlayerActivity
    private volatile String resolvedImageUrl = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_podcast_detail);
        db = new DatabaseHelper(this);
        imageLoader = ImageLoader.getInstance(this);

        final String title = getIntent().getStringExtra("podcast_title");
        final String author = getIntent().getStringExtra("podcast_author");
        final String feedUrl = getIntent().getStringExtra("feed_url");
        final String itunesId = getIntent().getStringExtra("itunes_id");
        String imageUrl = getIntent().getStringExtra("image_url");
        final String description = getIntent().getStringExtra("description");
        final long podcastId = getIntent().getLongExtra("podcast_id", -1);

        Button btnBack = (Button) findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        TextView tvHeaderTitle = (TextView) findViewById(R.id.tvHeaderTitle);
        tvHeaderTitle.setText("[podcasts]");

        btnSubscribe = (Button) findViewById(R.id.btnSubscribe);
        ListView listEpisodes = (ListView) findViewById(R.id.listEpisodes);

        // Sticky Podcast Panel
        panelStickyPodcast = (View) findViewById(R.id.panelStickyPodcast);
        panelExpanded = findViewById(R.id.panelExpanded);
        panelMinimized = findViewById(R.id.panelMinimized);

        // Expanded view elements
        imgPodcastCover = (ImageView) findViewById(R.id.imgPodcastCover);
        tvPodcastTitle = (TextView) findViewById(R.id.tvPodcastTitle);
        tvAuthor = (TextView) findViewById(R.id.tvAuthor);
        tvGenrePill = (TextView) findViewById(R.id.tvGenrePill);
        tvEpisodesStatus = (TextView) findViewById(R.id.tvEpisodesStatus);

        // Minimized view elements (search result shape)
        imgPodcastCoverMini = (ImageView) findViewById(R.id.imgPodcastCoverMini);
        tvPodcastTitleMini = (TextView) findViewById(R.id.tvPodcastTitleMini);
        tvAuthorMini = (TextView) findViewById(R.id.tvAuthorMini);
        tvStatusMini = (TextView) findViewById(R.id.tvStatusMini);

        // Set titles and authors
        tvPodcastTitle.setText(title != null ? title : "Podcast");
        tvPodcastTitleMini.setText(title != null ? title : "Podcast");

        String displayAuthor = (author != null && !author.isEmpty()) ? author : "Creator";
        tvAuthor.setText(displayAuthor);
        tvAuthorMini.setText(displayAuthor);

        String cleanDesc = (description != null && !description.isEmpty()) ? description.replaceAll("<[^>]*>", "").trim() : "Podcast";
        tvGenrePill.setText(cleanDesc);

        tvEpisodesStatus.setText("Loading feed...");
        tvStatusMini.setText("Loading feed...");

        // Tapping the sticky panel toggles expanded/minimized
        panelStickyPodcast.setOnClickListener(v -> setHeroMinimized(!isMinimized));

        // Determine subscription state by checking if feed is in database
        Podcast existing = db.getPodcastByFeedUrl(feedUrl);
        if (existing != null) {
            podcast = existing;
            isSubscribed = true;
            if (podcast.imageUrl != null && !podcast.imageUrl.isEmpty()) {
                imageUrl = podcast.imageUrl;
            }
        } else {
            podcast = new Podcast(title, author, description != null ? description : "", imageUrl != null ? imageUrl : "", feedUrl, itunesId);
            podcast.id = -1;
            isSubscribed = false;
        }

        // Load cover art instantly from cache/disk for both expanded and mini views
        resolvedImageUrl = imageUrl;
        imageLoader.displayImage(imageUrl, imgPodcastCover, R.drawable.ic_launcher);
        imageLoader.displayImage(imageUrl, imgPodcastCoverMini, R.drawable.ic_launcher);

        updateSubscribeButton();

        btnSubscribe.setOnClickListener(v -> {
            if (isSubscribed) {
                db.deletePodcast(podcast.id);
                isSubscribed = false;
                podcast.id = -1;
            } else {
                podcast.id = db.insertPodcast(podcast);
                isSubscribed = true;
                db.upsertEpisodes(podcast.id, episodes);
            }
            updateSubscribeButton();
        });

        adapter = new EpisodeAdapter();
        listEpisodes.setAdapter(adapter);

        // When scrolling the episodes, smoothly minimize the hero into search result shape
        // and keep it still at the top while episodes scroll underneath it
        listEpisodes.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {}

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (totalItemCount == 0) return;
                View firstChild = view.getChildAt(0);
                int top = (firstChild != null) ? firstChild.getTop() : 0;

                // When at the very top of the list, expand back to full hero
                if (firstVisibleItem == 0 && top >= -15) {
                    setHeroMinimized(false);
                } else if (firstVisibleItem > 0 || top < -35) {
                    setHeroMinimized(true);
                }
            }
        });

        listEpisodes.setOnItemClickListener((parent, view, position, id) -> {
            Object item = parent.getItemAtPosition(position);
            if (!(item instanceof Episode)) return;
            Episode ep = (Episode) item;
            Intent intent = new Intent(PodcastDetailActivity.this, PlayerActivity.class);
            intent.putExtra("episode_id", ep.id);
            intent.putExtra("episode_title", ep.title);
            intent.putExtra("episode_audio_url", ep.audioUrl);
            intent.putExtra("episode_file_path", ep.filePath != null ? ep.filePath : "");
            intent.putExtra("episode_duration", ep.durationSeconds);
            intent.putExtra("episode_position", ep.positionSeconds);
            intent.putExtra("podcast_title", title);
            intent.putExtra("podcast_artwork", resolvedImageUrl != null ? resolvedImageUrl : (podcast.imageUrl != null ? podcast.imageUrl : ""));
            intent.putExtra("podcast_feed_url", feedUrl);
            startActivity(intent);

            // Save state in background
            if (isSubscribed && ep.id == 0) {
                new Thread(() -> {
                    db.upsertEpisodes(podcast.id, episodes);
                }).start();
            }
        });

        loadEpisodes(feedUrl, podcastId);
        FontHelper.apply(findViewById(android.R.id.content), this);
    }

    private void setHeroMinimized(final boolean minimize) {
        if (this.isMinimized == minimize) return;
        this.isMinimized = minimize;

        // Cancel any running animation
        panelExpanded.animate().cancel();
        panelMinimized.animate().cancel();

        final int DURATION = 180;

        if (minimize) {
            // Show minimized at alpha=0 first, then fade in while fading out expanded
            panelMinimized.setAlpha(0f);
            panelMinimized.setVisibility(View.VISIBLE);

            panelExpanded.animate().alpha(0f).setDuration(DURATION)
                .withEndAction(() -> panelExpanded.setVisibility(View.GONE))
                .start();

            panelMinimized.animate().alpha(1f).setDuration(DURATION).start();

        } else {
            // Show expanded at alpha=0 first, then fade in while fading out minimized
            panelExpanded.setAlpha(0f);
            panelExpanded.setVisibility(View.VISIBLE);

            panelMinimized.animate().alpha(0f).setDuration(DURATION)
                .withEndAction(() -> panelMinimized.setVisibility(View.GONE))
                .start();

            panelExpanded.animate().alpha(1f).setDuration(DURATION).start();
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void loadEpisodes(final String feedUrl, final long podcastId) {
        if (feedUrl == null || feedUrl.trim().isEmpty()) {
            tvEpisodesStatus.setText("Feed error: Missing URL");
            tvStatusMini.setText("Missing URL");
            return;
        }
        tvEpisodesStatus.setText("Fetching feed...");
        tvStatusMini.setText("Fetching feed...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final RssFetcher.FeedResult feed = RssFetcher.fetchFeed(feedUrl);

                    // Always prefer the RSS feed's own channel image over whatever gpodder/search
                    // returned (gpodder URLs are often HTTPS CDNs that fail on BB10 SSL).
                    // If RSS has no image, fall back to what we already have.
                    if (feed.channelImageUrl != null && !feed.channelImageUrl.isEmpty()) {
                        podcast.imageUrl = feed.channelImageUrl;
                        resolvedImageUrl = feed.channelImageUrl;
                        final String rssImg = feed.channelImageUrl;
                        runOnUiThread(() -> {
                            imageLoader.displayImage(rssImg, imgPodcastCover, R.drawable.ic_launcher);
                            imageLoader.displayImage(rssImg, imgPodcastCoverMini, R.drawable.ic_launcher);
                        });
                    } else if (podcast.imageUrl != null && !podcast.imageUrl.isEmpty()) {
                        // RSS has no image; keep what search returned (already displayed)
                        resolvedImageUrl = podcast.imageUrl;
                    }

                    if (isSubscribed && podcast.id > 0) {
                        db.upsertEpisodes(podcast.id, feed.episodes);
                        final List<Episode> saved = db.getEpisodesForPodcast(podcast.id);
                        runOnUiThread(() -> {
                            episodes.clear();
                            episodes.addAll(saved);
                            adapter.notifyDataSetChanged();
                            String countStr = saved.size() + " episodes";
                            tvEpisodesStatus.setText(countStr);
                            tvStatusMini.setText(countStr);
                        });
                    } else {
                        runOnUiThread(() -> {
                            episodes.clear();
                            episodes.addAll(feed.episodes);
                            adapter.notifyDataSetChanged();
                            String countStr = feed.episodes.size() + " episodes";
                            tvEpisodesStatus.setText(countStr);
                            tvStatusMini.setText(countStr);
                        });
                    }
                } catch (Throwable e) {
                    final String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    runOnUiThread(() -> {
                        tvEpisodesStatus.setText("Feed error: " + msg);
                        tvStatusMini.setText("Feed error: " + msg);
                    });
                }
            }
        }).start();
    }

    private void updateSubscribeButton() {
        if (isSubscribed) {
            btnSubscribe.setText("✓ Subscribed");
            btnSubscribe.setBackgroundResource(R.drawable.btn_bb_action);
            btnSubscribe.setTextColor(0xFF00A8DF);
        } else {
            btnSubscribe.setText("+ Subscribe");
            btnSubscribe.setBackgroundResource(R.drawable.btn_bb_action);
            btnSubscribe.setTextColor(0xFFF5F5F5);
        }
    }

    private class EpisodeAdapter extends BaseAdapter {
        @Override
        public int getCount() { return episodes.size(); }
        @Override
        public Object getItem(int position) { return episodes.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_episode, parent, false);
            }
            Episode ep = episodes.get(position);
            TextView tvTitle = (TextView) convertView.findViewById(R.id.tvTitle);
            TextView tvDuration = (TextView) convertView.findViewById(R.id.tvDuration);
            TextView tvStatus = (TextView) convertView.findViewById(R.id.tvStatus);

            tvTitle.setText(ep.title);
            String dur = ep.getFormattedDuration();
            tvDuration.setText(!dur.isEmpty() ? dur : "Audio Episode");

            if (ep.downloaded) {
                tvStatus.setText("[offline]");
                tvStatus.setTextColor(0xFF00A8DF);
            } else if (ep.listened) {
                tvStatus.setText("[done]");
                tvStatus.setTextColor(0xFF888888);
            } else {
                tvStatus.setText("[stream]");
                tvStatus.setTextColor(0xFF888888);
            }

            FontHelper.apply(convertView, PodcastDetailActivity.this);
            return convertView;
        }
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }
}
