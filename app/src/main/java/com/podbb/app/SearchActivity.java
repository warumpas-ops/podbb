package com.podbb.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends Activity {
    private EditText etSearch;
    private ListView listResults;
    private TextView tvStatus;
    private List<Podcast> results = new ArrayList<>();
    private ArrayAdapter<Podcast> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        etSearch = (EditText) findViewById(R.id.etSearch);
        listResults = (ListView) findViewById(R.id.listResults);
        tvStatus = (TextView) findViewById(R.id.tvStatus);

        ((TextView) findViewById(R.id.tvBack)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });

        etSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                doSearch(etSearch.getText().toString().trim());
                return true;
            }
        });

        setupList();
    }

    private void setupList() {
        adapter = new ArrayAdapter<Podcast>(this, R.layout.item_search_result, R.id.tvTitle, results) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                Podcast p = results.get(position);
                ((TextView) v.findViewById(R.id.tvTitle)).setText(p.title);
                ((TextView) v.findViewById(R.id.tvAuthor)).setText(p.author);
                ((TextView) v.findViewById(R.id.tvGenre)).setText(p.description);
                return v;
            }
        };
        listResults.setAdapter(adapter);
        listResults.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Podcast p = results.get(position);
                Intent intent = new Intent(SearchActivity.this, PodcastDetailActivity.class);
                intent.putExtra("podcast_title", p.title);
                intent.putExtra("podcast_author", p.author);
                intent.putExtra("feed_url", p.feedUrl);
                intent.putExtra("itunes_id", p.itunesId);
                intent.putExtra("image_url", p.imageUrl);
                intent.putExtra("description", p.description);
                startActivity(intent);
            }
        });
    }

    private void doSearch(final String query) {
        if (query.isEmpty()) return;
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("searching...");
        results.clear();
        adapter.notifyDataSetChanged();

        new Thread(new Runnable() {
            public void run() {
                try {
                    final List<Podcast> found = ItunesApi.search(query);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            results.addAll(found);
                            adapter.notifyDataSetChanged();
                            tvStatus.setVisibility(found.isEmpty() ? View.VISIBLE : View.GONE);
                            if (found.isEmpty()) tvStatus.setText("no results");
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            tvStatus.setVisibility(View.VISIBLE);
                            tvStatus.setText("error: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }
}
