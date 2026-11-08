package com.podbb.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class HttpHelper {

    private static final String USER_AGENT = "PodBB/2.0 (BlackBerry 10; Android 4.3; Podcast Client)";
    private static SSLSocketFactory sslSocketFactory;
    private static LruCache<String, Bitmap> memoryCache;

    static {
        // Initialize 10MB memory cache for podcast covers
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = Math.max(1024, maxMemory / 8);
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        // Permissive SSLContext to handle expired BlackBerry 10 root CA certs
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {}
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            
            final SSLSocketFactory defaultFactory = sslContext.getSocketFactory();
            sslSocketFactory = new SSLSocketFactory() {
                private final String[] PROTOCOLS = new String[]{"TLSv1.2", "TLSv1.1", "TLSv1"};

                private Socket configureSocket(Socket socket, String host) {
                    if (socket instanceof SSLSocket) {
                        SSLSocket ssl = (SSLSocket) socket;
                        try {
                            ssl.setEnabledProtocols(PROTOCOLS);
                        } catch (Exception ignored) {
                            try {
                                ssl.setEnabledProtocols(ssl.getSupportedProtocols());
                            } catch (Exception ignored2) {}
                        }
                        // Enable SNI (Server Name Indication) via reflection for Android 4.3
                        if (host != null && !host.isEmpty()) {
                            try {
                                Method setHostname = ssl.getClass().getMethod("setHostname", String.class);
                                setHostname.invoke(ssl, host);
                            } catch (Exception ignored) {}
                            try {
                                Method setUseSessionTickets = ssl.getClass().getMethod("setUseSessionTickets", boolean.class);
                                setUseSessionTickets.invoke(ssl, true);
                            } catch (Exception ignored) {}
                        }
                    }
                    return socket;
                }

                @Override
                public String[] getDefaultCipherSuites() {
                    return defaultFactory.getDefaultCipherSuites();
                }

                @Override
                public String[] getSupportedCipherSuites() {
                    return defaultFactory.getSupportedCipherSuites();
                }

                @Override
                public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
                    return configureSocket(defaultFactory.createSocket(s, host, port, autoClose), host);
                }

                @Override
                public Socket createSocket(String host, int port) throws IOException {
                    return configureSocket(defaultFactory.createSocket(host, port), host);
                }

                @Override
                public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
                    return configureSocket(defaultFactory.createSocket(host, port, localHost, localPort), host);
                }

                @Override
                public Socket createSocket(InetAddress host, int port) throws IOException {
                    return configureSocket(defaultFactory.createSocket(host, port), host.getHostName());
                }

                @Override
                public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
                    return configureSocket(defaultFactory.createSocket(address, port, localAddress, localPort), address.getHostName());
                }
            };

            HttpsURLConnection.setDefaultSSLSocketFactory(sslSocketFactory);
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public interface ProgressListener {
        void onProgress(long bytesRead, long totalBytes, int percent);
    }

    public static HttpURLConnection openConnection(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (conn instanceof HttpsURLConnection && sslSocketFactory != null) {
            ((HttpsURLConnection) conn).setSSLSocketFactory(sslSocketFactory);
            ((HttpsURLConnection) conn).setHostnameVerifier((hostname, session) -> true);
        }
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "*/*");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setInstanceFollowRedirects(false);
        return conn;
    }

    public static HttpURLConnection openWithRedirects(String urlStr) throws Exception {
        return openWithRedirects(urlStr, null, null);
    }

    public static HttpURLConnection openWithRedirects(String urlStr, String acceptHeader, String extraUserAgent) throws Exception {
        String currentUrl = urlStr;
        int redirectCount = 0;
        final int maxRedirects = 10;

        while (redirectCount < maxRedirects) {
            HttpURLConnection conn = null;
            try {
                conn = openConnection(currentUrl);
                if (acceptHeader != null) conn.setRequestProperty("Accept", acceptHeader);
                if (extraUserAgent != null) conn.setRequestProperty("User-Agent", extraUserAgent);
                conn.connect();
            } catch (javax.net.ssl.SSLException | java.security.GeneralSecurityException sslErr) {
                // If HTTPS failed on BB10, try falling back to plain HTTP
                if (currentUrl.startsWith("https://")) {
                    currentUrl = currentUrl.replaceFirst("^https://", "http://");
                    redirectCount++;
                    continue;
                }
                throw sslErr;
            }

            int status = conn.getResponseCode();

            if (status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_SEE_OTHER ||
                status == 307 || status == 308) {

                String newUrl = conn.getHeaderField("Location");
                conn.disconnect();
                if (newUrl == null || newUrl.isEmpty()) {
                    throw new Exception("Redirect without Location header");
                }
                if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                    URL base = new URL(currentUrl);
                    newUrl = new URL(base, newUrl).toString();
                }
                currentUrl = newUrl;
                redirectCount++;
            } else {
                return conn;
            }
        }
        throw new Exception("Too many redirects: " + urlStr);
    }

    public static String fetchString(String urlStr) throws Exception {
        HttpURLConnection conn = openWithRedirects(urlStr);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            conn.disconnect();
            throw new Exception("HTTP " + code);
        }
        InputStream in = new BufferedInputStream(conn.getInputStream());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
        in.close();
        conn.disconnect();
        return out.toString("UTF-8");
    }

    public static Bitmap fetchBitmap(String urlStr, int reqWidth, int reqHeight) {
        if (urlStr == null || urlStr.isEmpty()) return null;

        Bitmap cached = memoryCache.get(urlStr);
        if (cached != null) return cached;

        try {
            HttpURLConnection conn = openWithRedirects(urlStr);
            InputStream in = new BufferedInputStream(conn.getInputStream());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            conn.disconnect();

            byte[] bytes = out.toByteArray();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;

            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (bitmap != null) {
                memoryCache.put(urlStr, bitmap);
            }
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean downloadFile(String urlStr, File destination, ProgressListener listener) throws Exception {
        HttpURLConnection conn = openWithRedirects(urlStr);
        int code = conn.getResponseCode();
        if (code != 200 && code != 206) {
            conn.disconnect();
            throw new Exception("HTTP error: " + code);
        }

        long totalSize = conn.getContentLength();
        if (totalSize <= 0) {
            String lenStr = conn.getHeaderField("Content-Length");
            if (lenStr != null) {
                try { totalSize = Long.parseLong(lenStr); } catch (Exception ignored) {}
            }
        }

        File parent = destination.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        File tempFile = new File(destination.getAbsolutePath() + ".tmp");
        InputStream in = new BufferedInputStream(conn.getInputStream(), 16384);
        FileOutputStream out = new FileOutputStream(tempFile);

        byte[] buffer = new byte[16384];
        long bytesReadTotal = 0;
        int read;

        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            bytesReadTotal += read;
            if (listener != null) {
                int percent = (totalSize > 0) ? (int) ((bytesReadTotal * 100) / totalSize) : 0;
                listener.onProgress(bytesReadTotal, totalSize, percent);
            }
        }

        out.flush();
        out.close();
        in.close();
        conn.disconnect();

        if (destination.exists()) {
            destination.delete();
        }
        return tempFile.renameTo(destination);
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}
