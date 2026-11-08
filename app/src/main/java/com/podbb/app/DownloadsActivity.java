package com.podbb.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.io.File;
import java.util.List;

public class DownloadsActivity extends Activity {
    private DatabaseHelper db;
    private List<Episode> downloads;
    private ArrayAdapter<Episode> adapter;
    private ListView listView;
    private LinearLayout layoutEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);
        db = new DatabaseHelper(this);

        listView = (ListView) findViewById(R.id.listDownloads);
        layoutEmpty = (LinearLayout) findViewById(R.id.layoutEmpty);

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
            intent.putExtra("podcast_title", ep.podcastTitle != null ? ep.podcastTitle : "Podcast");
            startActivity(intent);
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            final Episode ep = downloads.get(position);
            new AlertDialog.Builder(DownloadsActivity.this)
                .setTitle("Delete Download")
                .setMessage("Remove \"" + ep.title + "\" from offline storage?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (ep.filePath != null) new File(ep.filePath).delete();
                    db.deleteDownload(ep.id);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
            return true;
        });
    }

    private void refresh() {
        downloads = db.getDownloadedEpisodes();
        if (downloads.isEmpty()) {
            listView.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
        } else {
            listView.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
            adapter = new ArrayAdapter<Episode>(this, R.layout.item_episode, R.id.tvTitle, downloads) {
                @Override
                public View getView(int pos, View convertView, android.view.ViewGroup parent) {
                    View v = super.getView(pos, convertView, parent);
                    Episode ep = downloads.get(pos);
                    ((TextView) v.findViewById(R.id.tvTitle)).setText(ep.title);
                    ((TextView) v.findViewById(R.id.tvDuration)).setText(ep.podcastTitle != null ? ep.podcastTitle : "Offline Audio");
                    
                    TextView tvStatus = (TextView) v.findViewById(R.id.tvStatus);
                    tvStatus.setText("[play offline]");
                    tvStatus.setTextColor(0xFF00A8DF);
                    return v;
                }
            };
            listView.setAdapter(adapter);
        }
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }
}
