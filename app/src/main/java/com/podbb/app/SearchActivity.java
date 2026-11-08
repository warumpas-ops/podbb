package com.podbb.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
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
    private final List<Podcast> results = new ArrayList<>();
    private ResultAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        etSearch = (EditText) findViewById(R.id.etSearch);
        listResults = (ListView) findViewById(R.id.listResults);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        Button btnDoSearch = (Button) findViewById(R.id.btnDoSearch);
        Button btnBack = (Button) findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());
        btnDoSearch.setOnClickListener(v -> {
            String q = etSearch.getText().toString().trim();
            if (!q.isEmpty()) doSearch(q);
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            String q = etSearch.getText().toString().trim();
            if (!q.isEmpty()) {
                doSearch(q);
                return true;
            }
            return false;
        });

        adapter = new ResultAdapter();
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

        // Load top podcasts by default
        loadInitial();
        FontHelper.apply(findViewById(android.R.id.content), this);
    }

    private void loadInitial() {
        tvStatus.setText("Loading popular podcasts...");
        new Thread(() -> {
            final List<Podcast> top = PodcastSearchEngine.getToplist();
            runOnUiThread(() -> {
                results.clear();
                results.addAll(top);
                adapter.notifyDataSetChanged();
                tvStatus.setText("Top Podcasts (or type above to search)");
            });
        }).start();
    }

    private void doSearch(final String query) {
        tvStatus.setText("Searching for \"" + query + "\"...");
        results.clear();
        adapter.notifyDataSetChanged();

        new Thread(() -> {
            try {
                final List<Podcast> found = PodcastSearchEngine.search(query);
                runOnUiThread(() -> {
                    results.clear();
                    results.addAll(found);
                    adapter.notifyDataSetChanged();
                    if (found.isEmpty()) {
                        tvStatus.setText("No podcasts found for \"" + query + "\"");
                    } else {
                        tvStatus.setText("Found " + found.size() + " podcasts");
                    }
                });
            } catch (final Exception e) {
                runOnUiThread(() -> tvStatus.setText("Search error: " + e.getMessage()));
            }
        }).start();
    }

    private class ResultAdapter extends BaseAdapter {
        @Override
        public int getCount() { return results.size(); }
        @Override
        public Object getItem(int position) { return results.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_search_result, parent, false);
            }
            Podcast p = results.get(position);
            TextView tvTitle = (TextView) convertView.findViewById(R.id.tvTitle);
            TextView tvPreview = (TextView) convertView.findViewById(R.id.tvPreview);
            TextView tvCategory = (TextView) convertView.findViewById(R.id.tvCategory);

            if (tvTitle != null) tvTitle.setText(p.title);
            String desc = p.author != null && !p.author.isEmpty() ? p.author
                        : (p.description != null ? p.description.replaceAll("<[^>]*>", "").trim() : "Podcast");
            if (tvPreview != null) tvPreview.setText(desc);
            if (tvCategory != null) tvCategory.setText("[tap to view]");

            FontHelper.apply(convertView, SearchActivity.this);
            return convertView;
        }
    }
}
