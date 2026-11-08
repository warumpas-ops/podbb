package com.podbb.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
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
    private TextView tvEpisodesStatus;
    private ImageView imgPodcastCover;
    private ImageLoader imageLoader;
    private boolean isSubscribed = false;

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
        tvHeaderTitle.setText(title != null ? title : "Podcast");

        TextView tvPodcastTitle = (TextView) findViewById(R.id.tvPodcastTitle);
        tvPodcastTitle.setText(title);

        TextView tvAuthor = (TextView) findViewById(R.id.tvAuthor);
        tvAuthor.setText(author != null && !author.isEmpty() ? author : "Creator");

        TextView tvGenrePill = (TextView) findViewById(R.id.tvGenrePill);
        tvGenrePill.setText(description != null && !description.isEmpty() ? description.replaceAll("<[^>]*>", "").trim() : "Podcast");

        btnSubscribe = (Button) findViewById(R.id.btnSubscribe);
        tvEpisodesStatus = (TextView) findViewById(R.id.tvEpisodesStatus);
        imgPodcastCover = (ImageView) findViewById(R.id.imgPodcastCover);

        // Determine subscription state
        if (podcastId != -1) {
            podcast = new Podcast(title, author, description != null ? description : "", imageUrl != null ? imageUrl : "", feedUrl, itunesId);
            podcast.id = podcastId;
            isSubscribed = true;
        } else {
            Podcast existing = db.getPodcastByItunesId(itunesId != null ? itunesId : String.valueOf(feedUrl.hashCode()));
            if (existing != null) {
                podcast = existing;
                isSubscribed = true;
                if (podcast.imageUrl != null && !podcast.imageUrl.isEmpty()) {
                    imageUrl = podcast.imageUrl;
                }
            } else {
                podcast = new Podcast(title, author, description != null ? description : "", imageUrl != null ? imageUrl : "", feedUrl, itunesId);
                isSubscribed = false;
            }
        }

        // Load cover art instantly from cache/disk
        imageLoader.displayImage(imageUrl, imgPodcastCover, R.drawable.ic_launcher);

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

        ListView listEpisodes = (ListView) findViewById(R.id.listEpisodes);
        adapter = new EpisodeAdapter();
        listEpisodes.setAdapter(adapter);

        listEpisodes.setOnItemClickListener((parent, view, position, id) -> {
            Episode ep = episodes.get(position);
            Intent intent = new Intent(PodcastDetailActivity.this, PlayerActivity.class);
            intent.putExtra("episode_id", ep.id);
            intent.putExtra("episode_title", ep.title);
            intent.putExtra("episode_audio_url", ep.audioUrl);
            intent.putExtra("episode_file_path", ep.filePath != null ? ep.filePath : "");
            intent.putExtra("episode_duration", ep.durationSeconds);
            intent.putExtra("episode_position", ep.positionSeconds);
            intent.putExtra("podcast_title", title);
            intent.putExtra("podcast_artwork", podcast.imageUrl);
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

    private void loadEpisodes(final String feedUrl, final long podcastId) {
        tvEpisodesStatus.setText("Fetching feed...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final RssFetcher.FeedResult feed = RssFetcher.fetchFeed(feedUrl);

                    // Update cover: prefer RSS channelImageUrl, fall back to what we already have
                    if (feed.channelImageUrl != null && !feed.channelImageUrl.isEmpty()) {
                        if (podcast.imageUrl == null || podcast.imageUrl.isEmpty()) {
                            podcast.imageUrl = feed.channelImageUrl;
                        }
                        // Show RSS cover (may be higher quality than gpodder thumbnail)
                        final String rssImg = feed.channelImageUrl;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                imageLoader.displayImage(rssImg, imgPodcastCover, R.drawable.ic_launcher);
                            }
                        });
                    } else if (podcast.imageUrl != null && !podcast.imageUrl.isEmpty()) {
                        // Keep showing the gpodder logo we already loaded
                        final String existingImg = podcast.imageUrl;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                imageLoader.displayImage(existingImg, imgPodcastCover, R.drawable.ic_launcher);
                            }
                        });
                    }

                    if (isSubscribed && podcast.id > 0) {
                        db.upsertEpisodes(podcast.id, feed.episodes);
                        final List<Episode> saved = db.getEpisodesForPodcast(podcast.id);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                episodes.clear();
                                episodes.addAll(saved);
                                adapter.notifyDataSetChanged();
                                tvEpisodesStatus.setText(saved.size() + " episodes");
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                episodes.clear();
                                episodes.addAll(feed.episodes);
                                adapter.notifyDataSetChanged();
                                tvEpisodesStatus.setText(feed.episodes.size() + " episodes");
                            }
                        });
                    }
                } catch (Exception e) {
                    final String msg = e.getMessage() != null ? e.getMessage() : "offline";
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvEpisodesStatus.setText("Feed error: " + msg);
                        }
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
