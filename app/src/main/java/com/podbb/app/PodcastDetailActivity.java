package com.podbb.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class PodcastDetailActivity extends Activity {
    private DatabaseHelper db;
    private Podcast podcast;
    private List<Episode> episodes = new ArrayList<>();
    private ArrayAdapter<Episode> adapter;
    private TextView btnSubscribe;
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

        ((TextView) findViewById(R.id.tvBack)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });

        ((TextView) findViewById(R.id.tvPodcastTitle)).setText(title);
        ((TextView) findViewById(R.id.tvAuthor)).setText(author);

        btnSubscribe = (TextView) findViewById(R.id.btnSubscribe);

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

        btnSubscribe.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
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
            }
        });

        ListView listEpisodes = (ListView) findViewById(R.id.listEpisodes);
        adapter = new ArrayAdapter<Episode>(this, R.layout.item_episode, R.id.tvTitle, episodes) {
            @Override
            public View getView(int pos, View convertView, android.view.ViewGroup parent) {
                View v = super.getView(pos, convertView, parent);
                Episode e = episodes.get(pos);
                ((TextView) v.findViewById(R.id.tvTitle)).setText(e.title);
                String dur = e.getFormattedDuration();
                String status = e.downloaded ? " [dl]" : e.listened ? " [done]" : "";
                ((TextView) v.findViewById(R.id.tvDuration)).setText(dur);
                ((TextView) v.findViewById(R.id.tvStatus)).setText(status);
                return v;
            }
        };
        listEpisodes.setAdapter(adapter);

        listEpisodes.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
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
                intent.putExtra("podcast_feed_url", feedUrl);
                startActivity(intent);
            }
        });

        loadEpisodes(feedUrl, podcastId);
    }

    private void loadEpisodes(final String feedUrl, final long podcastId) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    final List<Episode> fetched = RssFetcher.fetch(feedUrl);
                    if (isSubscribed && podcast.id > 0) {
                        db.upsertEpisodes(podcast.id, fetched);
                        final List<Episode> saved = db.getEpisodesForPodcast(podcast.id);
                        runOnUiThread(new Runnable() {
                            public void run() {
                                episodes.clear();
                                episodes.addAll(saved);
                                adapter.notifyDataSetChanged();
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                episodes.clear();
                                episodes.addAll(fetched);
                                adapter.notifyDataSetChanged();
                            }
                        });
                    }
                } catch (Exception e) {
                }
            }
        }).start();
    }

    private void updateSubscribeButton() {
        if (isSubscribed) {
            btnSubscribe.setText("[unsubscribe]");
        } else {
            btnSubscribe.setText("[subscribe]");
        }
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }
}
