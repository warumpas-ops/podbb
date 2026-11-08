package com.podbb.app;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RssFetcher {

    public static class FeedResult {
        public String channelTitle;
        public String channelDescription;
        public String channelImageUrl;
        public List<Episode> episodes = new ArrayList<>();
    }

    // ── public entry points ────────────────────────────────────────────────

    public static FeedResult fetchFeed(String feedUrl) throws Exception {
        try {
            return doFetch(feedUrl);
        } catch (javax.net.ssl.SSLException | java.security.GeneralSecurityException sslErr) {
            if (feedUrl.startsWith("https://")) {
                try { return doFetch(feedUrl.replaceFirst("^https://", "http://")); }
                catch (Exception ignored) {}
            }
            throw sslErr;
        } catch (XmlPullParserException parseErr) {
            // Feed might have returned an HTML error page – try http fallback
            if (feedUrl.startsWith("https://")) {
                try { return doFetch(feedUrl.replaceFirst("^https://", "http://")); }
                catch (Exception ignored) {}
            }
            throw parseErr;
        }
    }

    public static List<Episode> fetch(String feedUrl) throws Exception {
        return fetchFeed(feedUrl).episodes;
    }

    // ── core fetch ─────────────────────────────────────────────────────────

    private static FeedResult doFetch(String feedUrl) throws Exception {
        HttpURLConnection conn = HttpHelper.openWithRedirects(feedUrl,
                "application/rss+xml,application/atom+xml,application/xml,text/xml;q=0.9,*/*;q=0.8",
                "PodBB/1.3 (BlackBerry)");

        // Some servers only return RSS if you ask for it (like bPod does)

        InputStream raw = new BufferedInputStream(conn.getInputStream(), 8192);

        // Read all bytes so we can strip a BOM and detect encoding
        byte[] data = readAll(raw);
        raw.close();
        conn.disconnect();

        // Strip UTF-8 BOM (EF BB BF) – XmlPullParser chokes on it
        if (data.length >= 3
                && (data[0] & 0xFF) == 0xEF
                && (data[1] & 0xFF) == 0xBB
                && (data[2] & 0xFF) == 0xBF) {
            byte[] stripped = new byte[data.length - 3];
            System.arraycopy(data, 3, stripped, 0, stripped.length);
            data = stripped;
        }

        // Detect encoding from <?xml ... encoding="..."?> or Content-Type
        String encoding = sniffEncoding(data, conn.getContentType());

        return parseXml(new ByteArrayInputStream(data), encoding);
    }

    // ── XML parser ─────────────────────────────────────────────────────────

    private static FeedResult parseXml(InputStream in, String encoding) throws Exception {
        FeedResult result = new FeedResult();

        XmlPullParser parser = Xml.newPullParser();
        // Let the parser handle encoding itself; pass null so it reads
        // the <?xml?> declaration, but override if we detected a BOM charset.
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        try {
            parser.setInput(in, encoding);  // null = auto-detect from declaration
        } catch (Exception e) {
            parser.setInput(in, "UTF-8");
        }

        Episode current = null;
        boolean inItem = false;
        boolean inChannelImage = false;
        String tag = "";
        String ns = "";

        try {
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {

                if (eventType == XmlPullParser.START_TAG) {
                    // qualifiedName includes namespace prefix e.g. "itunes:image"
                    String fullName = qualifiedName(parser);
                    tag = fullName;

                    if ("item".equals(fullName) || "entry".equals(fullName)) {
                        inItem = true;
                        current = new Episode();

                    } else if (!inItem) {
                        // Channel-level image: <itunes:image href="..."> or <image>
                        if ("itunes:image".equals(fullName)) {
                            String href = parser.getAttributeValue(null, "href");
                            if (href != null && !href.trim().isEmpty()) {
                                result.channelImageUrl = href.trim();
                            }
                        } else if ("image".equals(fullName)) {
                            inChannelImage = true;
                        }
                        // Atom: <logo> element contains the image URL
                        else if ("logo".equals(fullName)) {
                            // text content handled below
                        }

                    } else if (inItem && current != null) {
                        // Episode image: <itunes:image href="...">
                        if ("itunes:image".equals(fullName)) {
                            String href = parser.getAttributeValue(null, "href");
                            if (href != null && !href.trim().isEmpty()
                                    && current.imageUrl == null) {
                                current.imageUrl = href.trim();
                            }
                        }
                        // Audio enclosure
                        else if ("enclosure".equals(fullName) || "media:content".equals(fullName)) {
                            String type = parser.getAttributeValue(null, "type");
                            String url  = parser.getAttributeValue(null, "url");
                            if (url == null) url = parser.getAttributeValue(null, "medium"); // fallback
                            if (url != null && !url.isEmpty()) {
                                boolean isAudio = type == null
                                        || type.startsWith("audio")
                                        || url.contains(".mp3")
                                        || url.contains(".m4a")
                                        || url.contains(".ogg");
                                if (isAudio && current.audioUrl == null) {
                                    current.audioUrl = url.trim();
                                }
                            }
                        }
                        // Atom <link> with rel=enclosure
                        else if ("link".equals(fullName)) {
                            String rel  = parser.getAttributeValue(null, "rel");
                            String href = parser.getAttributeValue(null, "href");
                            if ("enclosure".equals(rel) && href != null && current.audioUrl == null) {
                                current.audioUrl = href.trim();
                            }
                        }
                    }

                } else if (eventType == XmlPullParser.END_TAG) {
                    String endTag = qualifiedName(parser);
                    if ("item".equals(endTag) || "entry".equals(endTag)) {
                        if (current != null && current.audioUrl != null
                                && !current.audioUrl.isEmpty()) {
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
                    String text = safeText(parser);
                    if (text != null && !text.isEmpty()) {

                        if (inItem && current != null) {
                            if ("title".equals(tag)) {
                                if (current.title == null) current.title = text;
                            } else if ("guid".equals(tag) || "id".equals(tag)) {
                                current.guid = text;
                            } else if ("description".equals(tag)
                                    || "content:encoded".equals(tag)
                                    || "summary".equals(tag)
                                    || "itunes:summary".equals(tag)) {
                                if (current.description == null) current.description = text;
                            } else if ("pubDate".equals(tag)
                                    || "published".equals(tag)
                                    || "updated".equals(tag)) {
                                current.pubDate = parseDate(text);
                            } else if ("itunes:duration".equals(tag)) {
                                current.durationSeconds = parseDuration(text);
                            } else if ("itunes:image".equals(tag)) {
                                // some feeds put image URL as text child (not href attr)
                                if (current.imageUrl == null) current.imageUrl = text;
                            }
                        } else if (!inItem) {
                            if ("title".equals(tag) && result.channelTitle == null) {
                                result.channelTitle = text;
                            } else if (inChannelImage && "url".equals(tag)) {
                                result.channelImageUrl = text;
                            } else if ("logo".equals(tag) && result.channelImageUrl == null) {
                                result.channelImageUrl = text;
                            }
                        }
                    }
                }

                eventType = parser.next();
            }
        } catch (XmlPullParserException e) {
            // If we parsed at least some episodes, return what we have
            if (result.episodes.isEmpty()) throw e;
            // else: partial parse – return whatever we got
        }

        return result;
    }

    // ── helpers ────────────────────────────────────────────────────────────

    /** Returns "prefix:localName" if prefix exists, else just localName. */
    private static String qualifiedName(XmlPullParser p) {
        String prefix = p.getPrefix();
        String local  = p.getName();
        if (prefix != null && !prefix.isEmpty()) return prefix + ":" + local;
        return local;
    }

    /** Safely get text, never throws. */
    private static String safeText(XmlPullParser p) {
        try {
            String t = p.getText();
            return (t == null) ? null : t.trim();
        } catch (Exception e) { return null; }
    }

    /** Read all bytes from stream into byte array. */
    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
        return buf.toByteArray();
    }

    /**
     * Sniff the character encoding from the raw feed bytes.
     * Priority: BOM > XML declaration > HTTP Content-Type header.
     */
    private static String sniffEncoding(byte[] data, String contentType) {
        // Try to find encoding in <?xml version="1.0" encoding="..."?>
        try {
            // Read first 200 bytes as ASCII
            int len = Math.min(data.length, 200);
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

        // Fall back to Content-Type: charset
        if (contentType != null) {
            int cs = contentType.toLowerCase().indexOf("charset=");
            if (cs >= 0) {
                String enc = contentType.substring(cs + 8).trim();
                int semi = enc.indexOf(';');
                if (semi >= 0) enc = enc.substring(0, semi);
                return enc.trim();
            }
        }

        return "UTF-8"; // safe default
    }

    // ── date & duration ────────────────────────────────────────────────────

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
            if (parts.length == 2) return Integer.parseInt(parts[0].trim()) * 60
                    + Integer.parseInt(parts[1].trim());
            if (parts.length == 3) return Integer.parseInt(parts[0].trim()) * 3600
                    + Integer.parseInt(parts[1].trim()) * 60
                    + Integer.parseInt(parts[2].trim());
        } catch (Exception ignored) {}
        return 0;
    }
}
