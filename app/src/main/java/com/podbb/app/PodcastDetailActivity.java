package com.podbb.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
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
    private ArrayAdapter<Episode> adapter;
    private Button btnSubscribe;
    private TextView tvEpisodesStatus;
    private ImageView imgPodcastCover;
    private boolean isSubscribed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_podcast_detail);
        db = new DatabaseHelper(this);

        final String title = getIntent().getStringExtra("podcast_title");
        final String author = getIntent().getStringExtra("podcast_author");
        final String feedUrl = getIntent().getStringExtra("feed_url");
        final String itunesId = getIntent().getStringExtra("itunes_id");
        final String imageUrl = getIntent().getStringExtra("image_url");
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
        tvGenrePill.setText(description != null && !description.isEmpty() ? description : "Podcast");

        btnSubscribe = (Button) findViewById(R.id.btnSubscribe);
        tvEpisodesStatus = (TextView) findViewById(R.id.tvEpisodesStatus);
        imgPodcastCover = (ImageView) findViewById(R.id.imgPodcastCover);

        // Load cover art asynchronously
        if (imageUrl != null && !imageUrl.isEmpty()) {
            new Thread(() -> {
                Bitmap bmp = HttpHelper.fetchBitmap(imageUrl, 128, 128);
                if (bmp != null) {
                    runOnUiThread(() -> imgPodcastCover.setImageBitmap(bmp));
                }
            }).start();
        }

        // Determine subscription state
        if (podcastId != -1) {
            podcast = new Podcast(title, author, description != null ? description : "", imageUrl != null ? imageUrl : "", feedUrl, itunesId);
            podcast.id = podcastId;
            isSubscribed = true;
        } else {
            Podcast existing = db.getPodcastByItunesId(itunesId);
            if (existing != null) {
                podcast = existing;
                isSubscribed = true;
            } else {
                podcast = new Podcast(title, author, description != null ? description : "", imageUrl != null ? imageUrl : "", feedUrl, itunesId);
                isSubscribed = false;
            }
        }

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
        adapter = new ArrayAdapter<Episode>(this, R.layout.item_episode, R.id.tvTitle, episodes) {
            @Override
            public View getView(int pos, View convertView, android.view.ViewGroup parent) {
                View v = super.getView(pos, convertView, parent);
                Episode e = episodes.get(pos);
                ((TextView) v.findViewById(R.id.tvTitle)).setText(e.title);
                String dur = e.getFormattedDuration();
                ((TextView) v.findViewById(R.id.tvDuration)).setText(!dur.isEmpty() ? dur : "Audio");
                
                TextView tvStatus = (TextView) v.findViewById(R.id.tvStatus);
                if (e.downloaded) {
                    tvStatus.setText("[downloaded]");
                    tvStatus.setTextColor(0xFF00A8DF);
                } else if (e.listened) {
                    tvStatus.setText("[listened]");
                    tvStatus.setTextColor(0xFF808086);
                } else {
                    tvStatus.setText("[stream]");
                    tvStatus.setTextColor(0xFF808086);
                }
                return v;
            }
        };
        listEpisodes.setAdapter(adapter);

        listEpisodes.setOnItemClickListener((parent, view, position, id) -> {
            Episode ep = episodes.get(position);
            if (isSubscribed && ep.id == 0) {
                db.upsertEpisodes(podcast.id, episodes);
                List<Episode> saved = db.getEpisodesForPodcast(podcast.id);
                if (position < saved.size()) ep = saved.get(position);
            }
            Intent intent = new Intent(PodcastDetailActivity.this, PlayerActivity.class);
            intent.putExtra("episode_id", ep.id);
            intent.putExtra("episode_title", ep.title);
            intent.putExtra("episode_audio_url", ep.audioUrl);
            intent.putExtra("episode_file_path", ep.filePath != null ? ep.filePath : "");
            intent.putExtra("episode_duration", ep.durationSeconds);
            intent.putExtra("episode_position", ep.positionSeconds);
            intent.putExtra("podcast_title", title);
            intent.putExtra("podcast_artwork", imageUrl);
            intent.putExtra("podcast_feed_url", feedUrl);
            startActivity(intent);
        });

        loadEpisodes(feedUrl, podcastId);
    }

    private void loadEpisodes(final String feedUrl, final long podcastId) {
        tvEpisodesStatus.setText("Fetching feed...");
        new Thread(() -> {
            try {
                final List<Episode> fetched = RssFetcher.fetch(feedUrl);
                if (isSubscribed && podcast.id > 0) {
                    db.upsertEpisodes(podcast.id, fetched);
                    final List<Episode> saved = db.getEpisodesForPodcast(podcast.id);
                    runOnUiThread(() -> {
                        episodes.clear();
                        episodes.addAll(saved);
                        adapter.notifyDataSetChanged();
                        tvEpisodesStatus.setText(saved.size() + " episodes");
                    });
                } else {
                    runOnUiThread(() -> {
                        episodes.clear();
                        episodes.addAll(fetched);
                        adapter.notifyDataSetChanged();
                        tvEpisodesStatus.setText(fetched.size() + " episodes");
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> tvEpisodesStatus.setText("Feed error: " + (e.getMessage() != null ? e.getMessage() : "offline")));
            }
        }).start();
    }

    private void updateSubscribeButton() {
        if (isSubscribed) {
            btnSubscribe.setText("✓ Subscribed");
            btnSubscribe.setBackgroundResource(R.drawable.btn_action);
            btnSubscribe.setTextColor(0xFF00A8DF);
        } else {
            btnSubscribe.setText("+ Subscribe");
            btnSubscribe.setBackgroundResource(R.drawable.btn_accent);
            btnSubscribe.setTextColor(0xFFF2F2F2);
        }
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }
}
