package com.podbb.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import java.io.File;
import java.util.List;

public class DownloadsActivity extends Activity {
    private DatabaseHelper db;
    private List<Episode> downloads;
    private DownloadAdapter adapter;
    private ListView listView;
    private TextView tvEmptyDownloads;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);
        db = new DatabaseHelper(this);

        listView = (ListView) findViewById(R.id.listDownloads);
        tvEmptyDownloads = (TextView) findViewById(R.id.tvEmptyDownloads);

        Button btnBack = (Button) findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        refresh();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Episode ep = downloads.get(position);
            Intent intent = new Intent(DownloadsActivity.this, PlayerActivity.class);
            intent.putExtra("episode_id", ep.id);
            intent.putExtra("episode_title", ep.title);
            intent.putExtra("episode_audio_url", ep.audioUrl);
            intent.putExtra("episode_file_path", ep.filePath != null ? ep.filePath : "");
            intent.putExtra("episode_duration", ep.durationSeconds);
            intent.putExtra("episode_position", ep.positionSeconds);
            intent.putExtra("podcast_title", ep.podcastTitle != null ? ep.podcastTitle : "Offline Podcast");
            intent.putExtra("podcast_artwork", ep.imageUrl != null ? ep.imageUrl : "");
            startActivity(intent);
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            final Episode ep = downloads.get(position);
            new AlertDialog.Builder(DownloadsActivity.this)
                .setTitle("Delete Offline Episode")
                .setMessage("Delete \"" + ep.title + "\" from storage?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (ep.filePath != null) new File(ep.filePath).delete();
                    db.deleteDownload(ep.id);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
            return true;
        });

        FontHelper.apply(findViewById(android.R.id.content), this);
    }

    private void refresh() {
        downloads = db.getDownloadedEpisodes();
        if (downloads.isEmpty()) {
            listView.setVisibility(View.GONE);
            tvEmptyDownloads.setVisibility(View.VISIBLE);
        } else {
            listView.setVisibility(View.VISIBLE);
            tvEmptyDownloads.setVisibility(View.GONE);
            adapter = new DownloadAdapter();
            listView.setAdapter(adapter);
        }
    }

    private class DownloadAdapter extends BaseAdapter {
        @Override
        public int getCount() { return downloads.size(); }
        @Override
        public Object getItem(int position) { return downloads.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_episode, parent, false);
            }
            Episode ep = downloads.get(pos);
            TextView tvTitle = (TextView) convertView.findViewById(R.id.tvTitle);
            TextView tvDuration = (TextView) convertView.findViewById(R.id.tvDuration);
            TextView tvStatus = (TextView) convertView.findViewById(R.id.tvStatus);

            tvTitle.setText(ep.title);
            tvDuration.setText(ep.podcastTitle != null ? ep.podcastTitle : "Offline Episode");
            tvStatus.setText("[play offline]");
            tvStatus.setTextColor(0xFF00A8DF);
            FontHelper.apply(convertView, DownloadsActivity.this);
            return convertView;
        }
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }
}
