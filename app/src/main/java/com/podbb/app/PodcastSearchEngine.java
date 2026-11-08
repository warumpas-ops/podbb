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
     * Search podcasts using gpodder.net over plain HTTP.
     * Plain HTTP bypasses all expired CA certificates and TLS 1.2 handshake issues on BlackBerry 10!
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

        String encoded = URLEncoder.encode(query, "UTF-8");
        // Using plain HTTP endpoint: http://gpodder.net/search.json?q=...
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
            // Prefer logo_url (direct CDN link), fall back to scaled_logo_url (gpodder proxy - has redirects)
            String logo = obj.optString("logo_url", "");
            if (logo.isEmpty()) logo = obj.optString("scaled_logo_url", "");
            String id = String.valueOf(feedUrl.hashCode());

            Podcast p = new Podcast(title, author, desc, logo, feedUrl, id);
            results.add(p);
        }

        // If gpodder returned empty, try fallback to top list matching query or simple query
        if (results.isEmpty()) {
            List<Podcast> top = getToplist();
            for (Podcast tp : top) {
                if (tp.title.toLowerCase().contains(query.toLowerCase()) || 
                    (tp.author != null && tp.author.toLowerCase().contains(query.toLowerCase()))) {
                    results.add(tp);
                }
            }
        }

        return results;
    }

    /**
     * Fetches top podcasts from gpodder toplist over plain HTTP.
     */
    public static List<Podcast> getToplist() {
        List<Podcast> results = new ArrayList<>();
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
                // Prefer logo_url (direct CDN link), fall back to scaled_logo_url (gpodder proxy - has redirects)
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
