package com.podbb.app;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RssFetcher {
    public static List<Episode> fetch(String feedUrl) throws Exception {
        List<Episode> episodes = new ArrayList<>();
        URL url = new URL(feedUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "PodBB/1.0");
        conn.setInstanceFollowRedirects(true);
        InputStream in = conn.getInputStream();

        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(in, null);

        Episode current = null;
        boolean inItem = false;
        String tag = "";

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                tag = parser.getName();
                if ("item".equals(tag)) {
                    inItem = true;
                    current = new Episode();
                } else if (inItem && "enclosure".equals(tag)) {
                    String type = parser.getAttributeValue(null, "type");
                    if (type != null && type.startsWith("audio")) {
                        current.audioUrl = parser.getAttributeValue(null, "url");
                    }
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if ("item".equals(parser.getName())) {
                    if (current != null && current.audioUrl != null && !current.audioUrl.isEmpty()) {
                        episodes.add(current);
                    }
                    inItem = false;
                    current = null;
                }
                tag = "";
            } else if (eventType == XmlPullParser.TEXT && inItem && current != null) {
                String text = parser.getText().trim();
                if (text.isEmpty()) {
                    eventType = parser.next();
                    continue;
                }
                if ("title".equals(tag)) {
                    current.title = text;
                } else if ("guid".equals(tag)) {
                    current.guid = text;
                } else if ("description".equals(tag) || "content:encoded".equals(tag)) {
                    if (current.description == null) current.description = text;
                } else if ("pubDate".equals(tag)) {
                    current.pubDate = parseDate(text);
                } else if ("itunes:duration".equals(tag)) {
                    current.durationSeconds = parseDuration(text);
                }
            }
            eventType = parser.next();
        }
        in.close();
        conn.disconnect();
        return episodes;
    }

    private static long parseDate(String s) {
        String[] formats = {
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy HH:mm:ss z"
        };
        for (String fmt : formats) {
            try {
                return new SimpleDateFormat(fmt, Locale.ENGLISH).parse(s).getTime();
            } catch (Exception e) { /* try next */ }
        }
        return 0;
    }

    private static int parseDuration(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            String[] parts = s.split(":");
            if (parts.length == 1) return Integer.parseInt(parts[0]);
            if (parts.length == 2) return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            if (parts.length == 3) return Integer.parseInt(parts[0]) * 3600 + Integer.parseInt(parts[1]) * 60 + Integer.parseInt(parts[2]);
        } catch (Exception e) { /* ignore */ }
        return 0;
    }
}
