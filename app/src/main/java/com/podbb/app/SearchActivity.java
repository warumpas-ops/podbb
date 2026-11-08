package com.podbb.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends Activity {
    private EditText etSearch;
    private ListView listResults;
    private TextView tvStatus;
    private Button btnDoSearch;
    private final List<Podcast> results = new ArrayList<>();
    private ArrayAdapter<Podcast> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        etSearch = (EditText) findViewById(R.id.etSearch);
        listResults = (ListView) findViewById(R.id.listResults);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        btnDoSearch = (Button) findViewById(R.id.btnDoSearch);

        Button btnBack = (Button) findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        btnDoSearch.setOnClickListener(v -> {
            String q = etSearch.getText().toString().trim();
            if (!q.isEmpty()) {
                doSearch(q);
            }
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            String q = etSearch.getText().toString().trim();
            if (!q.isEmpty()) {
                doSearch(q);
                return true;
            }
            return false;
        });

        setupList();

        // Load initial popular podcasts
        loadInitialDiscover();
    }

    private void setupList() {
        adapter = new ArrayAdapter<Podcast>(this, R.layout.item_search_result, R.id.tvTitle, results) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                Podcast p = results.get(position);
                ((TextView) v.findViewById(R.id.tvTitle)).setText(p.title);
                ((TextView) v.findViewById(R.id.tvAuthor)).setText(p.author != null && !p.author.isEmpty() ? p.author : "Podcast");
                ((TextView) v.findViewById(R.id.tvGenre)).setText(p.description != null && !p.description.isEmpty() ? p.description : "Top Show");
                return v;
            }
        };
        listResults.setAdapter(adapter);

        listResults.setOnItemClickListener((parent, view, position, id) -> {
            Podcast p = results.get(position);
            Intent intent = new Intent(SearchActivity.this, PodcastDetailActivity.class);
            intent.putExtra("podcast_title", p.title);
            intent.putExtra("podcast_author", p.author);
            intent.putExtra("feed_url", p.feedUrl);
            intent.putExtra("itunes_id", p.itunesId);
            intent.putExtra("image_url", p.imageUrl);
            intent.putExtra("description", p.description);
            startActivity(intent);
        });
    }

    private void loadInitialDiscover() {
        tvStatus.setText("Loading featured podcasts...");
        new Thread(() -> {
            try {
                final List<Podcast> popular = ItunesApi.search("news");
                runOnUiThread(() -> {
                    if (results.isEmpty() && !popular.isEmpty()) {
                        results.addAll(popular);
                        adapter.notifyDataSetChanged();
                        tvStatus.setText("Featured Podcasts (type above to search)");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> tvStatus.setText("Type keywords above to search iTunes directory"));
            }
        }).start();
    }

    private void doSearch(final String query) {
        tvStatus.setText("Searching for \"" + query + "\"...");
        results.clear();
        adapter.notifyDataSetChanged();

        new Thread(() -> {
            try {
                final List<Podcast> found = ItunesApi.search(query);
                runOnUiThread(() -> {
                    results.addAll(found);
                    adapter.notifyDataSetChanged();
                    if (found.isEmpty()) {
                        tvStatus.setText("No podcasts found for \"" + query + "\"");
                    } else {
                        tvStatus.setText("Found " + found.size() + " podcasts for \"" + query + "\"");
                    }
                });
            } catch (final Exception e) {
                runOnUiThread(() -> {
                    String msg = e.getMessage() != null ? e.getMessage() : "Check internet connection";
                    tvStatus.setText("Search error: " + msg);
                });
            }
        }).start();
    }
}
