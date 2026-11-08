package com.podbb.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class ItunesApi {

    public static List<Podcast> search(String query) throws Exception {
        List<Podcast> results = new ArrayList<>();
        String encoded = URLEncoder.encode(query, "UTF-8");
        String urlStr = "https://itunes.apple.com/search?term=" + encoded + "&media=podcast&entity=podcast&limit=30";
        
        String jsonStr = HttpHelper.fetchString(urlStr);
        JSONObject root = new JSONObject(jsonStr);
        JSONArray items = root.optJSONArray("results");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject obj = items.getJSONObject(i);
                String feedUrl = obj.optString("feedUrl", "");
                if (feedUrl.isEmpty()) continue;

                String title = obj.optString("collectionName", "Unknown Podcast");
                String author = obj.optString("artistName", "");
                String genre = obj.optString("primaryGenreName", "Podcast");
                String artwork = obj.optString("artworkUrl600", obj.optString("artworkUrl100", ""));
                String itunesId = String.valueOf(obj.optLong("collectionId", 0));

                Podcast p = new Podcast(title, author, genre, artwork, feedUrl, itunesId);
                results.add(p);
            }
        }
        return results;
    }

    public static List<Podcast> getPopular() {
        List<Podcast> results = new ArrayList<>();
        try {
            // Default discover query for top podcasts
            return search("top podcast");
        } catch (Exception e) {
            return results;
        }
    }
}
