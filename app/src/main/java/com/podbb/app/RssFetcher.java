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
        } catch (Exception primaryErr) {
            // If primary fetch failed (e.g. SSL or handshake error) and URL was HTTPS, try HTTP fallback
            if (feedUrl != null && feedUrl.startsWith("https://")) {
                try {
                    return doFetch(feedUrl.replaceFirst("^https://", "http://"));
                } catch (Exception ignored) {}
            }
            throw primaryErr;
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
            throw new Exception("HTTP " + code);
        }

        String contentEncoding = conn.getContentEncoding();
        InputStream raw = conn.getInputStream();
        InputStream stream = prepareStream(raw, contentEncoding);

        FeedResult result;
        try {
            result = parseXmlStream(stream);
        } finally {
            try { stream.close(); } catch (Exception ignored) {}
            conn.disconnect();
        }

        return result;
    }

    /**
     * Inspects the input stream without buffering the entire document into memory:
     * 1. Detects and decompresses GZIP (magic 0x1F, 0x8B or Content-Encoding header)
     * 2. Detects and strips UTF-8 BOM (0xEF, 0xBB, 0xBF)
     */
    private static InputStream prepareStream(InputStream raw, String contentEncoding) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(raw, 16384);
        PushbackInputStream pb = new PushbackInputStream(bis, 4);

        byte[] header = new byte[4];
        int read = pb.read(header);
        if (read < 2) {
            if (read > 0) pb.unread(header, 0, read);
            return pb;
        }

        // 1. Check GZIP
        boolean isGzip = (contentEncoding != null && contentEncoding.toLowerCase(Locale.ENGLISH).contains("gzip"))
                || ((header[0] & 0xFF) == 0x1F && (header[1] & 0xFF) == 0x8B);

        InputStream streamToUse;
        if (isGzip) {
            pb.unread(header, 0, read);
            GZIPInputStream gis = new GZIPInputStream(pb);
            streamToUse = new BufferedInputStream(gis, 8192);
        } else {
            pb.unread(header, 0, read);
            streamToUse = pb;
        }

        // 2. Strip UTF-8 BOM if present
        PushbackInputStream pbFinal = new PushbackInputStream(streamToUse, 4);
        byte[] bom = new byte[3];
        int bomRead = pbFinal.read(bom);
        if (bomRead == 3 && (bom[0] & 0xFF) == 0xEF && (bom[1] & 0xFF) == 0xBB && (bom[2] & 0xFF) == 0xBF) {
            // BOM stripped cleanly
        } else if (bomRead > 0) {
            pbFinal.unread(bom, 0, bomRead);
        }
        return pbFinal;
    }

    private static FeedResult parseXmlStream(InputStream in) throws Exception {
        FeedResult result = new FeedResult();

        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(in, null); // Auto-detect from <?xml ...?> declaration or UTF-8

        Episode current = null;
        boolean inItem = false;
        boolean inChannelImage = false;
        String tag = "";
        final int MAX_EPISODES = 100; // Cap to keep BlackBerry Q5 fast and light on RAM

        try {
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {

                if (eventType == XmlPullParser.START_TAG) {
                    tag = normalizeTag(parser.getName());

                    if (isItemTag(tag)) {
                        inItem = true;
                        current = new Episode();

                    } else if (!inItem) {
                        // Channel cover image: <itunes:image href="...">, or <image><url>...</url></image>, or <logo>...</logo>
                        if (tag.contains("image")) {
                            String href = parser.getAttributeValue(null, "href");
                            if (href != null && !href.trim().isEmpty()) {
                                result.channelImageUrl = href.trim();
                            } else {
                                inChannelImage = true;
                            }
                        }

                    } else if (inItem && current != null) {
                        if (tag.equals("enclosure") || tag.contains("content")) {
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
                        } else if (tag.equals("link")) {
                            String href = parser.getAttributeValue(null, "href");
                            String rel = parser.getAttributeValue(null, "rel");
                            if (href != null && ("enclosure".equals(rel) || href.contains(".mp3") || href.contains(".m4a"))) {
                                if (current.audioUrl == null) current.audioUrl = href.trim();
                            }
                        } else if (tag.contains("image")) {
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
                                break;
                            }
                        }
                        inItem = false;
                        current = null;
                    } else if (endTag.contains("image")) {
                        inChannelImage = false;
                    }
                    tag = "";

                } else if (eventType == XmlPullParser.TEXT) {
                    String text = safeText(parser);
                    if (text != null && !text.isEmpty()) {
                        if (inItem && current != null) {
                            if (tag.equals("title") || tag.endsWith(":title")) {
                                if (current.title == null) current.title = text;
                            } else if (tag.equals("guid") || tag.equals("id") || tag.endsWith(":guid")) {
                                current.guid = text;
                            } else if (tag.equals("description") || tag.contains("summary") || tag.contains("encoded")) {
                                if (current.description == null) current.description = text;
                            } else if (tag.equals("pubdate") || tag.equals("published") || tag.equals("updated")) {
                                current.pubDate = parseDate(text);
                            } else if (tag.contains("duration")) {
                                current.durationSeconds = parseDuration(text);
                            } else if (tag.contains("image") && current.imageUrl == null && (text.startsWith("http://") || text.startsWith("https://"))) {
                                current.imageUrl = text;
                            }
                        } else if (!inItem) {
                            if ((tag.equals("title") || tag.endsWith(":title")) && result.channelTitle == null) {
                                result.channelTitle = text;
                            } else if ((tag.equals("description") || tag.contains("summary")) && result.channelDescription == null) {
                                result.channelDescription = text;
                            } else if (inChannelImage && tag.equals("url") && result.channelImageUrl == null) {
                                result.channelImageUrl = text;
                            } else if ((tag.equals("logo") || tag.contains("image")) && result.channelImageUrl == null && (text.startsWith("http://") || text.startsWith("https://"))) {
                                result.channelImageUrl = text;
                            }
                        }
                    }
                }

                eventType = parser.next();
            }
        } catch (XmlPullParserException e) {
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
