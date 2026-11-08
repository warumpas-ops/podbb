package com.podbb.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import java.io.File;
import java.util.List;

public class DownloadsActivity extends Activity {
    private DatabaseHelper db;
    private List<Episode> downloads;
    private ArrayAdapter<Episode> adapter;
    private ListView listView;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);
        db = new DatabaseHelper(this);

        listView = (ListView) findViewById(R.id.listDownloads);
        tvEmpty = (TextView) findViewById(R.id.tvEmpty);

        ((TextView) findViewById(R.id.tvBack)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });

        refresh();

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Episode ep = downloads.get(position);
                Intent intent = new Intent(DownloadsActivity.this, PlayerActivity.class);
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

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                final Episode ep = downloads.get(position);
                new AlertDialog.Builder(DownloadsActivity.this)
                    .setTitle(ep.title)
                    .setMessage("delete download?")
                    .setPositiveButton("[delete]", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            if (ep.filePath != null) new File(ep.filePath).delete();
                            db.deleteDownload(ep.id);
                            refresh();
                        }
                    })
                    .setNegativeButton("[cancel]", null)
                    .show();
                return true;
            }
        });
    }

    private void refresh() {
        downloads = db.getDownloadedEpisodes();
        if (downloads.isEmpty()) {
            listView.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            listView.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
            adapter = new ArrayAdapter<Episode>(this, R.layout.item_episode, R.id.tvTitle, downloads) {
                @Override
                public View getView(int pos, View convertView, android.view.ViewGroup parent) {
                    View v = super.getView(pos, convertView, parent);
                    Episode ep = downloads.get(pos);
                    ((TextView) v.findViewById(R.id.tvTitle)).setText(ep.title);
                    ((TextView) v.findViewById(R.id.tvDuration)).setText(ep.podcastTitle != null ? ep.podcastTitle : "");
                    ((TextView) v.findViewById(R.id.tvStatus)).setText("[dl]");
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
