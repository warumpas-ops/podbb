package com.podbb.app;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
        String contentType = conn.getContentType();
        InputStream raw = new BufferedInputStream(conn.getInputStream(), 16384);
        byte[] data = readAll(raw);
        raw.close();
        conn.disconnect();

        if (data == null || data.length == 0) {
            throw new Exception("Empty feed response");
        }

        // Automatic GZIP decompression: either by Content-Encoding header or magic bytes (0x1F, 0x8B)
        boolean isGzip = (contentEncoding != null && contentEncoding.toLowerCase(Locale.ENGLISH).contains("gzip"))
                || (data.length >= 2 && (data[0] & 0xFF) == 0x1F && (data[1] & 0xFF) == 0x8B);

        if (isGzip) {
            try {
                GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(data));
                data = readAll(gis);
                gis.close();
            } catch (Exception ignored) {}
        }

        // Strip UTF-8 BOM (0xEF, 0xBB, 0xBF) which breaks Android's XmlPullParser
        if (data.length >= 3
                && (data[0] & 0xFF) == 0xEF
                && (data[1] & 0xFF) == 0xBB
                && (data[2] & 0xFF) == 0xBF) {
            byte[] stripped = new byte[data.length - 3];
            System.arraycopy(data, 3, stripped, 0, stripped.length);
            data = stripped;
        }

        String encoding = sniffEncoding(data, contentType);
        return parseXml(new ByteArrayInputStream(data), encoding);
    }

    private static FeedResult parseXml(InputStream in, String encoding) throws Exception {
        FeedResult result = new FeedResult();

        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        try {
            parser.setInput(in, encoding);
        } catch (Exception e) {
            parser.setInput(in, "UTF-8");
        }

        Episode current = null;
        boolean inItem = false;
        boolean inChannelImage = false;
        String tag = "";
        final int MAX_EPISODES = 150;

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

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }

    private static String sniffEncoding(byte[] data, String contentType) {
        try {
            int len = Math.min(data.length, 256);
            String head = new String(data, 0, len, "US-ASCII");
            int encStart = head.indexOf("encoding=\"");
            if (encStart >= 0) {
                int from = encStart + 10;
                int to = head.indexOf('"', from);
                if (to > from) return head.substring(from, to);
            }
            encStart = head.indexOf("encoding='");
            if (encStart >= 0) {
                int from = encStart + 10;
                int to = head.indexOf('\'', from);
                if (to > from) return head.substring(from, to);
            }
        } catch (Exception ignored) {}

        if (contentType != null) {
            int cs = contentType.toLowerCase(Locale.ENGLISH).indexOf("charset=");
            if (cs >= 0) {
                String enc = contentType.substring(cs + 8).trim();
                int semi = enc.indexOf(';');
                if (semi >= 0) enc = enc.substring(0, semi);
                return enc.trim();
            }
        }

        return "UTF-8";
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
