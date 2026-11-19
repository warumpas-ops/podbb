package com.podbb.app;

import android.util.Log;

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

    private static final String TAG = "PodcastSearchEngine";

    /**
     * Search podcasts using Apple iTunes Podcast Search API first (over 3M+ active shows),
     * with fallback to iTunes Charts RSS feed for toplist.
     * gpodder.net removed — service has been dead since 2024 (HTTP 502).
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
            String itunesUrl = "https://itunes.apple.com/search?term=" + encoded + "&entity=podcast&limit=60";
            Log.d(TAG, "iTunes search: " + itunesUrl);
            String jsonStr = HttpHelper.fetchString(itunesUrl);
            Log.d(TAG, "iTunes response length: " + jsonStr.length());
            JSONObject root = new JSONObject(jsonStr);
            JSONArray items = root.optJSONArray("results");
            Log.d(TAG, "iTunes resultCount: " + root.optInt("resultCount", -1));

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
        } catch (Exception e) {
            Log.e(TAG, "iTunes search failed: " + e.getMessage(), e);
        }

        // If iTunes returned results, we're done
        if (!results.isEmpty()) {
            Log.d(TAG, "Returning " + results.size() + " iTunes results");
            return results;
        }

        // Strategy 2: iTunes search with alternate term form (broader match)
        try {
            String encoded = URLEncoder.encode(query, "UTF-8");
            String itunesUrl = "https://itunes.apple.com/search?term=" + encoded
                    + "&media=podcast&limit=50&country=US";
            Log.d(TAG, "iTunes fallback search: " + itunesUrl);
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
                    String logo = obj.optString("artworkUrl600", "");
                    if (logo.isEmpty()) logo = obj.optString("artworkUrl100", "");
                    String collectionId = String.valueOf(obj.optLong("collectionId", 0));
                    if ("0".equals(collectionId)) collectionId = String.valueOf(feedUrl.hashCode());
                    Podcast p = new Podcast(title, author, desc, logo, feedUrl, collectionId);
                    results.add(p);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "iTunes fallback search failed: " + e.getMessage(), e);
        }

        Log.d(TAG, "Final result count: " + results.size());
        return results;
    }

    /**
     * Fetches top podcasts via iTunes Charts JSON feed (Apple Podcasts Top 100).
     * Falls back to a plain iTunes keyword search for "news" if the charts API fails.
     */
    public static List<Podcast> getToplist() {
        List<Podcast> results = new ArrayList<>();

        // iTunes Podcast Charts API — returns top 100 podcasts as JSON
        try {
            String chartsUrl = "https://itunes.apple.com/us/rss/toppodcasts/limit=50/json";
            Log.d(TAG, "Fetching toplist from iTunes Charts: " + chartsUrl);
            String jsonStr = HttpHelper.fetchString(chartsUrl);
            JSONObject root = new JSONObject(jsonStr);
            JSONObject feed = root.optJSONObject("feed");
            if (feed != null) {
                JSONArray entries = feed.optJSONArray("entry");
                if (entries != null && entries.length() > 0) {
                    for (int i = 0; i < entries.length(); i++) {
                        JSONObject entry = entries.getJSONObject(i);

                        // Extract name
                        String title = "";
                        JSONObject nameObj = entry.optJSONObject("im:name");
                        if (nameObj != null) title = nameObj.optString("label", "");

                        // Extract artist
                        String author = "";
                        JSONObject artistObj = entry.optJSONObject("im:artist");
                        if (artistObj != null) author = artistObj.optString("label", "");

                        // Extract artwork (last entry is highest-res)
                        String logo = "";
                        JSONArray images = entry.optJSONArray("im:image");
                        if (images != null && images.length() > 0) {
                            JSONObject lastImg = images.optJSONObject(images.length() - 1);
                            if (lastImg != null) logo = lastImg.optString("label", "");
                        }

                        // Extract iTunes ID and build feedUrl via iTunes Lookup API
                        String itunesId = "";
                        JSONObject idObj = entry.optJSONObject("id");
                        if (idObj != null) {
                            JSONObject attrs = idObj.optJSONObject("attributes");
                            if (attrs != null) itunesId = attrs.optString("im:id", "");
                        }

                        if (itunesId.isEmpty() || title.isEmpty()) continue;

                        // Use iTunes lookup to get feedUrl for this podcast ID
                        String feedUrl = lookupFeedUrl(itunesId);
                        if (feedUrl == null || feedUrl.isEmpty()) continue;

                        String desc = "Top Podcast";
                        Podcast p = new Podcast(title, author, desc, logo, feedUrl, itunesId);
                        results.add(p);

                        // Limit to 20 for toplist (each requires a lookup call)
                        if (results.size() >= 20) break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "iTunes Charts toplist failed: " + e.getMessage(), e);
        }

        if (!results.isEmpty()) return results;

        // Fallback: plain iTunes search for "news" as a generic interesting toplist
        try {
            String itunesUrl = "https://itunes.apple.com/search?term=technology+news&entity=podcast&limit=50";
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
                    String collectionId = String.valueOf(obj.optLong("collectionId", 0));
                    if ("0".equals(collectionId)) collectionId = String.valueOf(feedUrl.hashCode());
                    Podcast p = new Podcast(title, author, desc, logo, feedUrl, collectionId);
                    results.add(p);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "iTunes toplist fallback failed: " + e.getMessage(), e);
        }

        return results;
    }

    /**
     * Looks up a podcast's RSS feedUrl from its iTunes collection ID using the iTunes Lookup API.
     */
    private static String lookupFeedUrl(String itunesId) {
        try {
            String lookupUrl = "https://itunes.apple.com/lookup?id=" + itunesId + "&entity=podcast";
            String jsonStr = HttpHelper.fetchString(lookupUrl);
            JSONObject root = new JSONObject(jsonStr);
            JSONArray results = root.optJSONArray("results");
            if (results != null && results.length() > 0) {
                return results.getJSONObject(0).optString("feedUrl", "");
            }
        } catch (Exception e) {
            Log.w(TAG, "lookupFeedUrl failed for id=" + itunesId + ": " + e.getMessage());
        }
        return "";
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
