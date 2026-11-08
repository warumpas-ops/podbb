package com.podbb.app;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

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
                try {
                    return doFetch(feedUrl.replaceFirst("^https://", "http://"));
                } catch (Exception ignored) {}
            }
            throw sslErr;
        }
    }

    public static List<Episode> fetch(String feedUrl) throws Exception {
        return fetchFeed(feedUrl).episodes;
    }

    private static FeedResult doFetch(String feedUrl) throws Exception {
        HttpURLConnection conn = HttpHelper.openWithRedirects(
                feedUrl,
                "application/rss+xml,application/atom+xml,application/xml,text/xml;q=0.9,*/*;q=0.8",
                "PodBB/2.0 (BlackBerry 10; Android 4.3; Podcast Client)"
        );

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            conn.disconnect();
            throw new Exception("HTTP server response " + code);
        }

        String contentEncoding = conn.getContentEncoding();
        InputStream raw = new BufferedInputStream(conn.getInputStream(), 16384);
        InputStream decompressed = handleGzip(raw, contentEncoding);

        FeedResult result;
        try {
            result = parseXmlStream(decompressed);
        } finally {
            try { decompressed.close(); } catch (Exception ignored) {}
            conn.disconnect();
        }

        return result;
    }

    /**
     * Inspects the stream magic bytes to detect GZIP compression automatically.
     */
    private static InputStream handleGzip(InputStream in, String contentEncoding) throws IOException {
        if ("gzip".equalsIgnoreCase(contentEncoding)) {
            return new GZIPInputStream(in);
        }
        PushbackInputStream pb = new PushbackInputStream(in, 2);
        byte[] head = new byte[2];
        int read = pb.read(head);
        if (read == 2) {
            pb.unread(head);
            // GZIP magic header: 0x1f 0x8b
            if ((head[0] == (byte) 0x1f) && (head[1] == (byte) 0x8b)) {
                return new GZIPInputStream(pb);
            }
        } else if (read > 0) {
            pb.unread(head, 0, read);
        }
        return pb;
    }

    private static FeedResult parseXmlStream(InputStream in) throws Exception {
        FeedResult result = new FeedResult();

        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(in, null); // Auto-detect charset from XML declaration or UTF-8

        Episode current = null;
        boolean inItem = false;
        boolean inChannelImage = false;
        String currentTag = "";
        final int MAX_EPISODES = 150; // Cap to keep BlackBerry Q5 fast and light on RAM

        try {
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {

                if (eventType == XmlPullParser.START_TAG) {
                    currentTag = normalizeTag(parser.getName());

                    if (isItemTag(currentTag)) {
                        inItem = true;
                        current = new Episode();

                    } else if (!inItem) {
                        // Channel cover image
                        if (currentTag.contains("image")) {
                            String href = parser.getAttributeValue(null, "href");
                            if (href != null && !href.trim().isEmpty()) {
                                result.channelImageUrl = href.trim();
                            } else {
                                inChannelImage = true;
                            }
                        } else if ("logo".equals(currentTag)) {
                            // Handled in TEXT
                        }

                    } else if (inItem && current != null) {
                        // Episode enclosure
                        if (currentTag.equals("enclosure") || currentTag.endsWith(":content") || currentTag.equals("content")) {
                            String url = parser.getAttributeValue(null, "url");
                            if (url == null) url = parser.getAttributeValue(null, "medium");
                            String type = parser.getAttributeValue(null, "type");

                            if (url != null && !url.trim().isEmpty()) {
                                boolean isAudio = type == null || type.startsWith("audio")
                                        || url.contains(".mp3") || url.contains(".m4a")
                                        || url.contains(".ogg") || url.contains(".aac");
                                if (isAudio || current.audioUrl == null) {
                                    current.audioUrl = url.trim();
                                }
                            }
                        } else if ("link".equals(currentTag)) {
                            String href = parser.getAttributeValue(null, "href");
                            String rel = parser.getAttributeValue(null, "rel");
                            if (href != null && ("enclosure".equals(rel) || href.contains(".mp3") || href.contains(".m4a"))) {
                                if (current.audioUrl == null) current.audioUrl = href.trim();
                            }
                        } else if (currentTag.contains("image")) {
                            String href = parser.getAttributeValue(null, "href");
                            if (href != null && !href.trim().isEmpty() && current.imageUrl == null) {
                                current.imageUrl = href.trim();
                            }
                        }
                    }

                } else if (eventType == XmlPullParser.END_TAG) {
                    String endTag = normalizeTag(parser.getName());
                    if (isItemTag(endTag)) {
                        if (current != null && current.audioUrl != null && !current.audioUrl.isEmpty()) {
                            if (current.title == null || current.title.trim().isEmpty()) {
                                current.title = "Untitled Episode";
                            }
                            result.episodes.add(current);
                            if (result.episodes.size() >= MAX_EPISODES) {
                                break; // Loaded enough episodes for smooth browsing
                            }
                        }
                        inItem = false;
                        current = null;
                    } else if (endTag.contains("image")) {
                        inChannelImage = false;
                    }
                    currentTag = "";

                } else if (eventType == XmlPullParser.TEXT) {
                    String text = safeText(parser);
                    if (text != null && !text.isEmpty()) {
                        if (inItem && current != null) {
                            if ("title".equals(currentTag) || currentTag.endsWith(":title")) {
                                if (current.title == null) current.title = text;
                            } else if ("guid".equals(currentTag) || "id".equals(currentTag) || currentTag.endsWith(":guid")) {
                                current.guid = text;
                            } else if ("description".equals(currentTag) || currentTag.contains("summary") || currentTag.contains("encoded")) {
                                if (current.description == null) current.description = text;
                            } else if ("pubdate".equals(currentTag) || "published".equals(currentTag) || "updated".equals(currentTag)) {
                                current.pubDate = parseDate(text);
                            } else if (currentTag.contains("duration")) {
                                current.durationSeconds = parseDuration(text);
                            } else if (currentTag.contains("image") && current.imageUrl == null && (text.startsWith("http://") || text.startsWith("https://"))) {
                                current.imageUrl = text;
                            }
                        } else if (!inItem) {
                            if (("title".equals(currentTag) || currentTag.endsWith(":title")) && result.channelTitle == null) {
                                result.channelTitle = text;
                            } else if ((inChannelImage || "logo".equals(currentTag)) && result.channelImageUrl == null && (text.startsWith("http://") || text.startsWith("https://"))) {
                                result.channelImageUrl = text;
                            }
                        }
                    }
                }

                eventType = parser.next();
            }
        } catch (XmlPullParserException e) {
            // Partial recovery: if we already parsed episodes, return them
            if (result.episodes.isEmpty()) throw e;
        }

        return result;
    }

    private static String normalizeTag(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase(Locale.ENGLISH);
    }

    private static boolean isItemTag(String tag) {
        return "item".equals(tag) || "entry".equals(tag) || tag.endsWith(":item") || tag.endsWith(":entry");
    }

    private static String safeText(XmlPullParser p) {
        try {
            String t = p.getText();
            return (t == null) ? null : t.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static long parseDate(String s) {
        String[] formats = {
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
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
            if (parts.length == 1) return Integer.parseInt(parts[0].trim());
            if (parts.length == 2) return Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());
            if (parts.length == 3) return Integer.parseInt(parts[0].trim()) * 3600 + Integer.parseInt(parts[1].trim()) * 60 + Integer.parseInt(parts[2].trim());
        } catch (Exception ignored) {}
        return 0;
    }
}
