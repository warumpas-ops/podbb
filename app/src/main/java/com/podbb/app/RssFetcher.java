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
                "text/xml,application/xml,application/rss+xml,application/atom+xml,*/*;q=0.8",
                null
        );

        // getResponseCode() is safe to call here — HttpHelper.openWithRedirects already
        // called it internally and returns the final 2xx connection. Android caches the value.
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            conn.disconnect();
            throw new Exception("HTTP " + code);
        }

        // Buffer the response body. 20MB cap ensures large feeds with many episodes are never truncated.
        byte[] body;
        try {
            String contentEncoding = conn.getContentEncoding();
            InputStream raw = conn.getInputStream();
            body = readAndDecompress(raw, contentEncoding);
        } finally {
            conn.disconnect();
        }

        return parseXmlBytes(body);
    }

    /**
     * Reads all bytes from the stream, decompressing GZIP if needed.
     */
    private static byte[] readAndDecompress(InputStream raw, String contentEncoding) throws IOException {
        // Peek at first 2 bytes to auto-detect GZIP magic regardless of Content-Encoding
        BufferedInputStream bis = new BufferedInputStream(raw, 8192);
        bis.mark(2);
        int b0 = bis.read();
        int b1 = bis.read();
        bis.reset();

        boolean isGzip = (contentEncoding != null && contentEncoding.toLowerCase(Locale.ENGLISH).contains("gzip"))
                || (b0 == 0x1F && b1 == 0x8B);

        InputStream in = isGzip ? new GZIPInputStream(bis) : bis;

        ByteArrayOutputStream out = new ByteArrayOutputStream(65536);
        byte[] buf = new byte[8192];
        int read;
        int totalRead = 0;
        final int maxBytes = 20 * 1024 * 1024; // 20MB cap (large feeds won't be truncated)

        while ((read = in.read(buf)) != -1) {
            out.write(buf, 0, read);
            totalRead += read;
            if (totalRead >= maxBytes) break;
        }
        return out.toByteArray();
    }

    /**
     * Parse XML from a byte array, stripping UTF-8 BOM if present.
     * If standard parsing fails (e.g. unescaped & in titles or descriptions),
     * sanitizes unescaped entities and retries.
     */
    private static FeedResult parseXmlBytes(byte[] body) throws Exception {
        // Strip UTF-8 BOM (EF BB BF)
        int offset = 0;
        if (body.length >= 3 && (body[0] & 0xFF) == 0xEF && (body[1] & 0xFF) == 0xBB && (body[2] & 0xFF) == 0xBF) {
            offset = 3;
        }

        try {
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(body, offset, body.length - offset);
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(bais, null);
            return parseXml(parser);
        } catch (Exception e) {
            // If raw parse failed (e.g. unescaped & in XML, entity error), sanitize & retry
            try {
                String xml = new String(body, offset, body.length - offset, "UTF-8");
                xml = xml.replaceAll("&(?!(?:amp|lt|gt|quot|apos|#\\d+|#x[0-9a-fA-F]+);)", "&amp;");
                XmlPullParser parser = Xml.newPullParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                parser.setInput(new java.io.StringReader(xml));
                return parseXml(parser);
            } catch (Exception ignored) {}
            throw e;
        }
    }

    private static FeedResult parseXml(XmlPullParser parser) throws Exception {
        FeedResult result = new FeedResult();

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
                        } else if (tag.contains("transcript")) {
                            String url = parser.getAttributeValue(null, "url");
                            if (url == null) url = parser.getAttributeValue(null, "href");
                            String type = parser.getAttributeValue(null, "type");
                            if (url != null && !url.trim().isEmpty() && current.transcriptUrl == null) {
                                current.transcriptUrl = url.trim();
                                current.transcriptType = (type != null) ? type.trim() : "text/vtt";
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
