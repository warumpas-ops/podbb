package com.podbb.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class PodcastSearchEngine {

    /**
     * Search podcasts using Apple iTunes Podcast Search API first (over 3M+ active shows),
     * with seamless fallback to gpodder.net.
     */
    public static List<Podcast> search(String query) throws Exception {
        List<Podcast> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return getToplist();
        }

        query = query.trim();

        // If user pasted a direct RSS feed URL
        if (query.startsWith("http://") || query.startsWith("https://")) {
            Podcast p = new Podcast();
            p.title = "Direct Feed: " + query;
            p.author = "RSS Feed";
            p.feedUrl = query;
            p.description = "Custom RSS Feed";
            p.itunesId = String.valueOf(query.hashCode());
            results.add(p);
            return results;
        }

        // Strategy 1: Apple iTunes Podcast Search API (global, up-to-date, canonical feed URLs)
        try {
            String encoded = URLEncoder.encode(query, "UTF-8");
            String itunesUrl = "https://itunes.apple.com/search?term=" + encoded + "&entity=podcast&limit=30";
            String jsonStr = HttpHelper.fetchString(itunesUrl);
            JSONObject root = new JSONObject(jsonStr);
            JSONArray items = root.optJSONArray("results");

            if (items != null && items.length() > 0) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject obj = items.getJSONObject(i);
                    String feedUrl = obj.optString("feedUrl", "");
                    if (feedUrl.isEmpty()) continue;

                    String title = obj.optString("collectionName", obj.optString("trackName", "Podcast"));
                    String author = obj.optString("artistName", "");
                    String desc = obj.optString("primaryGenreName", "Podcast");

                    // Prefer high resolution 600x600 artwork, fallback to 100x100
                    String logo = obj.optString("artworkUrl600", "");
                    if (logo.isEmpty()) logo = obj.optString("artworkUrl100", "");
                    if (logo.isEmpty()) logo = obj.optString("artworkUrl60", "");

                    String collectionId = String.valueOf(obj.optLong("collectionId", 0));
                    if ("0".equals(collectionId)) collectionId = String.valueOf(feedUrl.hashCode());

                    Podcast p = new Podcast(title, author, desc, logo, feedUrl, collectionId);
                    results.add(p);
                }
            }
        } catch (Exception ignored) {}

        // If iTunes returned results, return them
        if (!results.isEmpty()) {
            return results;
        }

        // Strategy 2: gpodder.net plain HTTP search (fallback)
        try {
            String encoded = URLEncoder.encode(query, "UTF-8");
            String urlStr = "http://gpodder.net/search.json?q=" + encoded;
            String jsonStr = fetchHttpJson(urlStr);
            JSONArray items = new JSONArray(jsonStr);

            for (int i = 0; i < items.length(); i++) {
                JSONObject obj = items.getJSONObject(i);
                String feedUrl = obj.optString("url", "");
                if (feedUrl.isEmpty()) continue;

                String title = obj.optString("title", "Unknown Podcast");
                String author = obj.optString("author", "");
                String desc = obj.optString("description", "");
                String logo = obj.optString("logo_url", "");
                if (logo.isEmpty()) logo = obj.optString("scaled_logo_url", "");
                String id = String.valueOf(feedUrl.hashCode());

                Podcast p = new Podcast(title, author, desc, logo, feedUrl, id);
                results.add(p);
            }
        } catch (Exception ignored) {}

        return results;
    }

    /**
     * Fetches top podcasts: tries iTunes general popular podcasts first, then gpodder toplist.
     */
    public static List<Podcast> getToplist() {
        List<Podcast> results = new ArrayList<>();

        // Try iTunes top podcasts
        try {
            String itunesUrl = "https://itunes.apple.com/search?term=podcast&entity=podcast&limit=30";
            String jsonStr = HttpHelper.fetchString(itunesUrl);
            JSONObject root = new JSONObject(jsonStr);
            JSONArray items = root.optJSONArray("results");

            if (items != null && items.length() > 0) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject obj = items.getJSONObject(i);
                    String feedUrl = obj.optString("feedUrl", "");
                    if (feedUrl.isEmpty()) continue;

                    String title = obj.optString("collectionName", obj.optString("trackName", "Podcast"));
                    String author = obj.optString("artistName", "");
                    String desc = obj.optString("primaryGenreName", "Top Podcast");

                    String logo = obj.optString("artworkUrl600", "");
                    if (logo.isEmpty()) logo = obj.optString("artworkUrl100", "");
                    if (logo.isEmpty()) logo = obj.optString("artworkUrl60", "");

                    String collectionId = String.valueOf(obj.optLong("collectionId", 0));
                    if ("0".equals(collectionId)) collectionId = String.valueOf(feedUrl.hashCode());

                    Podcast p = new Podcast(title, author, desc, logo, feedUrl, collectionId);
                    results.add(p);
                }
            }
        } catch (Exception ignored) {}

        if (!results.isEmpty()) return results;

        // Fallback: gpodder toplist
        try {
            String urlStr = "http://gpodder.net/toplist/30.json";
            String jsonStr = fetchHttpJson(urlStr);
            JSONArray items = new JSONArray(jsonStr);

            for (int i = 0; i < items.length(); i++) {
                JSONObject obj = items.getJSONObject(i);
                String feedUrl = obj.optString("url", "");
                if (feedUrl.isEmpty()) continue;

                String title = obj.optString("title", "Podcast");
                String author = obj.optString("author", "");
                String desc = obj.optString("description", "");
                String logo = obj.optString("logo_url", "");
                if (logo.isEmpty()) logo = obj.optString("scaled_logo_url", "");
                String id = String.valueOf(feedUrl.hashCode());

                Podcast p = new Podcast(title, author, desc, logo, feedUrl, id);
                results.add(p);
            }
        } catch (Exception ignored) {}

        return results;
    }

    private static String fetchHttpJson(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "PodBB/2.0 (BlackBerry 10; Android 4.3; Podcast Client)");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(12000);
        conn.setInstanceFollowRedirects(true);

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            conn.disconnect();
            throw new Exception("HTTP response: " + code);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        conn.disconnect();
        return sb.toString();
    }
}
