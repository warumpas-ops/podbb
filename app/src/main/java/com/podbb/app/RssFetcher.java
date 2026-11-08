package com.podbb.app;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RssFetcher {

    public static class FeedResult {
        public String channelTitle;
        public String channelDescription;
        public String channelImageUrl;
        public List<Episode> episodes = new ArrayList<>();
    }

    public static FeedResult fetchFeed(String feedUrl) throws Exception {
        try {
            return doFetch(feedUrl);
        } catch (javax.net.ssl.SSLException | java.security.GeneralSecurityException sslErr) {
            if (feedUrl.startsWith("https://")) {
                String httpUrl = feedUrl.replaceFirst("^https://", "http://");
                try {
                    return doFetch(httpUrl);
                } catch (Exception ignored) {}
            }
            throw sslErr;
        }
    }

    public static List<Episode> fetch(String feedUrl) throws Exception {
        return fetchFeed(feedUrl).episodes;
    }

    private static FeedResult doFetch(String feedUrl) throws Exception {
        FeedResult result = new FeedResult();
        HttpURLConnection conn = HttpHelper.openWithRedirects(feedUrl);
        InputStream in = conn.getInputStream();

        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(in, null);

        Episode current = null;
        boolean inItem = false;
        boolean inChannelImage = false;
        String tag = "";

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                tag = parser.getName();
                if ("item".equals(tag)) {
                    inItem = true;
                    current = new Episode();
                } else if (!inItem && ("itunes:image".equals(tag) || "image".equals(tag))) {
                    String href = parser.getAttributeValue(null, "href");
                    if (href != null && !href.isEmpty()) {
                        result.channelImageUrl = href.trim();
                    } else if ("image".equals(tag)) {
                        inChannelImage = true;
                    }
                } else if (inItem && "enclosure".equals(tag)) {
                    String type = parser.getAttributeValue(null, "type");
                    String url = parser.getAttributeValue(null, "url");
                    if (url != null && !url.isEmpty()) {
                        if (type == null || type.startsWith("audio") || url.contains(".mp3") || url.contains(".m4a")) {
                            current.audioUrl = url.trim();
                        }
                    }
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                String endTag = parser.getName();
                if ("item".equals(endTag)) {
                    if (current != null && current.audioUrl != null && !current.audioUrl.isEmpty()) {
                        if (current.title == null || current.title.isEmpty()) {
                            current.title = "Untitled Episode";
                        }
                        result.episodes.add(current);
                    }
                    inItem = false;
                    current = null;
                } else if ("image".equals(endTag)) {
                    inChannelImage = false;
                }
                tag = "";
            } else if (eventType == XmlPullParser.TEXT) {
                String text = parser.getText();
                if (text != null) {
                    text = text.trim();
                    if (!text.isEmpty()) {
                        if (inItem && current != null) {
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
                        } else if (!inItem) {
                            if ("title".equals(tag) && result.channelTitle == null) {
                                result.channelTitle = text;
                            } else if (inChannelImage && "url".equals(tag)) {
                                result.channelImageUrl = text;
                            }
                        }
                    }
                }
            }
            eventType = parser.next();
        }
        in.close();
        conn.disconnect();
        return result;
    }

    private static long parseDate(String s) {
        String[] formats = {
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd'T'HH:mm:ssZ"
        };
        for (String fmt : formats) {
            try {
                return new SimpleDateFormat(fmt, Locale.ENGLISH).parse(s).getTime();
            } catch (Exception ignored) {}
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
        } catch (Exception ignored) {}
        return 0;
    }
}
