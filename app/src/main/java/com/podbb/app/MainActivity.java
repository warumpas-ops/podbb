package com.podbb.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import java.util.List;

public class MainActivity extends Activity {
    private DatabaseHelper db;
    private List<Podcast> podcasts;
    private ArrayAdapter<Podcast> adapter;
    private ListView listView;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        db = new DatabaseHelper(this);
        listView = (ListView) findViewById(R.id.listSubscriptions);
        tvEmpty = (TextView) findViewById(R.id.tvEmpty);

        TextView navSearch = (TextView) findViewById(R.id.navSearch);
        TextView navDownloads = (TextView) findViewById(R.id.navDownloads);

        navSearch.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SearchActivity.class));
            }
        });

        navDownloads.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, DownloadsActivity.class));
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Podcast p = podcasts.get(position);
                Intent intent = new Intent(MainActivity.this, PodcastDetailActivity.class);
                intent.putExtra("podcast_id", p.id);
                intent.putExtra("podcast_title", p.title);
                intent.putExtra("podcast_author", p.author);
                intent.putExtra("feed_url", p.feedUrl);
                intent.putExtra("itunes_id", p.itunesId);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        podcasts = db.getAllSubscribed();
        if (podcasts.isEmpty()) {
            listView.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            listView.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
            adapter = new ArrayAdapter<Podcast>(this, R.layout.item_podcast, R.id.tvTitle, podcasts) {
                @Override
                public View getView(int position, View convertView, android.view.ViewGroup parent) {
                    View v = super.getView(position, convertView, parent);
                    Podcast p = podcasts.get(position);
                    ((TextView) v.findViewById(R.id.tvTitle)).setText(p.title);
                    ((TextView) v.findViewById(R.id.tvAuthor)).setText(p.author);
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
