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

public class ItunesApi {
    public static List<Podcast> search(String query) throws Exception {
        List<Podcast> results = new ArrayList<>();
        String encoded = URLEncoder.encode(query, "UTF-8");
        String urlStr = "https://itunes.apple.com/search?term=" + encoded + "&media=podcast&limit=20";
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "PodBB/1.0");
        int code = conn.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code);
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        conn.disconnect();
        JSONObject root = new JSONObject(sb.toString());
        JSONArray items = root.getJSONArray("results");
        for (int i = 0; i < items.length(); i++) {
            JSONObject obj = items.getJSONObject(i);
            String feedUrl = obj.optString("feedUrl", "");
            if (feedUrl.isEmpty()) continue;
            Podcast p = new Podcast(
                obj.optString("collectionName", ""),
                obj.optString("artistName", ""),
                obj.optString("primaryGenreName", ""),
                obj.optString("artworkUrl100", ""),
                feedUrl,
                String.valueOf(obj.optLong("collectionId", 0))
            );
            results.add(p);
        }
        return results;
    }
}
