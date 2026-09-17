package com.tsuyu.line;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class Net {
    static final String BG = "yoru-bg";
    static final String FG = "yoru-fg";
    private static final Dispatcher DISPATCHER = new Dispatcher();
    private static final ConnectionPool POOL = new ConnectionPool(32, 5L, TimeUnit.MINUTES);
    private static final OkHttpClient BASE;
    private static final ConcurrentHashMap<String, OkHttpClient> CLIENTS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> BG_FLAG = new ThreadLocal<>();
    private static volatile String focusScreen = "";
    private static volatile okhttp3.Cache diskCache;

    private static okhttp3.Cache getCache() {
        if (diskCache == null) {
            synchronized (Net.class) {
                if (diskCache == null) {
                    try {
                        YoruApp app = YoruApp.app();
                        if (app != null && app.getCacheDir() != null) {
                            java.io.File dir = new java.io.File(app.getCacheDir(), "http-cache");
                            diskCache = new okhttp3.Cache(dir, 25L * 1024L * 1024L);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return diskCache;
    }

    static {
        DISPATCHER.setMaxRequests(64);
        DISPATCHER.setMaxRequestsPerHost(16);
        BASE = new OkHttpClient.Builder()
                .dispatcher(DISPATCHER)
                .connectionPool(POOL)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(true)
                .connectTimeout(4500, TimeUnit.MILLISECONDS)
                .readTimeout(6500, TimeUnit.MILLISECONDS)
                .writeTimeout(6500, TimeUnit.MILLISECONDS)
                .build();
    }
    private Net() {
    }
    public static final class HttpCode extends IOException {
        public final int code;
        HttpCode(int code) {
            super("HTTP " + code);
            this.code = code;
        }
    }
    public static final class Page {
        public String text = "";
        public String cookies = "";
    }
    public static final class Body implements Closeable {
        private final Response response;
        private final InputStream input;
        Body(Response response, InputStream input) {
            this.response = response;
            this.input = input;
        }
        public InputStream input() {
            return input;
        }
        @Override
        public void close() {
            try {
                input.close();
            } catch (Exception ignored) {
            }
            try {
                response.close();
            } catch (Exception ignored) {
            }
        }
    }
    public static void bg(boolean value) {
        if (value) BG_FLAG.set(Boolean.TRUE);
        else BG_FLAG.remove();
    }
    public static boolean isBg() {
        return Boolean.TRUE.equals(BG_FLAG.get());
    }
    public static void runBg(Runnable work) {
        bg(true);
        try {
            work.run();
        } finally {
            bg(false);
        }
    }
    public static String focus() {
        return focusScreen;
    }
    public static void focus(String screen) {
        focusScreen = screen == null ? "" : screen;
        if ("details".equals(focusScreen) || "player".equals(focusScreen)) cancelBackground();
    }
    public static void cancelBackground() {
        try {
            for (okhttp3.Call call : DISPATCHER.queuedCalls())
                if (BG.equals(call.request().tag(String.class))) call.cancel();
        } catch (Exception ignored) {
        }
        try {
            for (okhttp3.Call call : DISPATCHER.runningCalls())
                if (BG.equals(call.request().tag(String.class))) call.cancel();
        } catch (Exception ignored) {
        }
    }
    private static OkHttpClient client(int connectMs, int readMs, boolean follow) {
        String key = connectMs + "|" + readMs + "|" + (follow ? 1 : 0);
        OkHttpClient cached = CLIENTS.get(key);
        if (cached != null) return cached;
        OkHttpClient.Builder b = BASE.newBuilder()
                .connectTimeout(Math.max(800, connectMs), TimeUnit.MILLISECONDS)
                .readTimeout(Math.max(1200, readMs), TimeUnit.MILLISECONDS)
                .writeTimeout(Math.max(1200, readMs), TimeUnit.MILLISECONDS)
                .followRedirects(follow)
                .followSslRedirects(follow);
        okhttp3.Cache c = getCache();
        if (c != null) b.cache(c);
        OkHttpClient built = b.build();
        CLIENTS.put(key, built);
        return built;
    }
    private static RequestBody bodyOf(String body, boolean form) {
        if (body == null) return null;
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return null;
            }
            @Override
            public void writeTo(okio.BufferedSink sink) throws IOException {
                sink.write(bytes);
            }
        };
    }
    private static Request build(String url, String method, String body, boolean form, Map<String, String> headers, String accept, String ua) {
        Request.Builder builder = new Request.Builder().url(url).tag(String.class, isBg() ? BG : FG);
        if (accept != null && !accept.isEmpty()) builder.header("Accept", accept);
        builder.header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5");
        if (ua != null && !ua.isEmpty()) builder.header("User-Agent", ua);
        if (headers != null) for (Map.Entry<String, String> entry : headers.entrySet())
            if (entry.getKey() != null && entry.getValue() != null) builder.header(entry.getKey(), entry.getValue());
        String verb = method == null || method.isEmpty() ? "GET" : method.toUpperCase(java.util.Locale.ROOT);
        if ("GET".equals(verb)) builder.get();
        else if ("HEAD".equals(verb)) builder.head();
        else {
            builder.header("Content-Type", form ? "application/x-www-form-urlencoded; charset=utf-8" : "application/json; charset=utf-8");
            builder.method(verb, bodyOf(body == null ? "" : body, form));
        }
        return builder.build();
    }
    private static String resolve(String base, String location) {
        try {
            return new java.net.URL(new java.net.URL(base), location).toString();
        } catch (Exception e) {
            return "";
        }
    }
    private static byte[] readCapped(InputStream input, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int n;
        while ((n = input.read(buffer)) != -1) {
            try {
                TaskQueue.check();
            } catch (java.io.InterruptedIOException interrupted) {
                throw interrupted;
            }
            if (out.size() + n > max) throw new IOException("Too large");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
    public static String text(String url, String method, String body, boolean form, Map<String, String> headers, String accept, String ua, int connectMs, int readMs, int max) throws IOException {
        String current = url;
        String verb = method;
        String payload = body;
        OkHttpClient http = client(connectMs, readMs, false);
        IOException last = null;
        for (int redirect = 0; redirect < 5; redirect++) {
            TaskQueue.check();
            if (current == null || current.isEmpty()) throw new IOException("Bad address");
            Request request = build(current, verb, payload, form, headers, accept, ua);
            try (Response response = http.newCall(request).execute()) {
                int code = response.code();
                if (code >= 300 && code < 400) {
                    String location = response.header("Location");
                    if (location == null || location.isEmpty()) throw new HttpCode(code);
                    current = resolve(current, location);
                    if (current.isEmpty()) throw new HttpCode(code);
                    if (code != 307 && code != 308) {
                        verb = "GET";
                        payload = null;
                    }
                    continue;
                }
                if (code < 200 || code >= 300) throw new HttpCode(code);
                ResponseBody responseBody = response.body();
                if (responseBody == null) throw new IOException("Empty");
                try (InputStream input = responseBody.byteStream()) {
                    return new String(readCapped(input, max), StandardCharsets.UTF_8);
                }
            } catch (HttpCode e) {
                throw e;
            } catch (IOException e) {
                last = e;
                if (isInterrupted(e)) throw e;
                throw e;
            }
        }
        if (last != null) throw last;
        throw new IOException("No response");
    }
    public static byte[] bytes(String url, Map<String, String> headers, String accept, String ua, String contentPrefix, int connectMs, int readMs, int max) throws IOException {
        TaskQueue.check();
        OkHttpClient http = client(connectMs, readMs, true);
        Request request = build(url, "GET", null, false, headers, accept, ua);
        try (Response response = http.newCall(request).execute()) {
            int code = response.code();
            if (code < 200 || code >= 300) throw new HttpCode(code);
            if (contentPrefix != null && !contentPrefix.isEmpty()) {
                String type = response.header("Content-Type");
                String lower = type == null ? "" : type.toLowerCase(java.util.Locale.ROOT);
                if (!lower.isEmpty() && !lower.startsWith(contentPrefix) && !lower.contains("octet-stream"))
                    throw new IOException("Bad content");
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) throw new IOException("Empty");
            try (InputStream input = responseBody.byteStream()) {
                return readCapped(input, max);
            }
        }
    }
    public static Page page(String url, String referer, String ua, String accept, int connectMs, int readMs, int max) throws IOException {
        TaskQueue.check();
        OkHttpClient http = client(connectMs, readMs, true);
        Request.Builder builder = new Request.Builder().url(url).tag(String.class, isBg() ? BG : FG).get();
        if (accept != null && !accept.isEmpty()) builder.header("Accept", accept);
        builder.header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5");
        if (ua != null && !ua.isEmpty()) builder.header("User-Agent", ua);
        if (referer != null && !referer.isEmpty()) builder.header("Referer", referer);
        try (Response response = http.newCall(builder.build()).execute()) {
            int code = response.code();
            if (code < 200 || code >= 300) throw new HttpCode(code);
            Page result = new Page();
            ResponseBody responseBody = response.body();
            if (responseBody != null) try (InputStream input = responseBody.byteStream()) {
                result.text = new String(readCapped(input, max), StandardCharsets.UTF_8);
            }
            List<String> set = response.headers("Set-Cookie");
            if (set != null) {
                StringBuilder cookies = new StringBuilder();
                for (String row : set) {
                    String one = row.split(";", 2)[0];
                    if (one.isEmpty()) continue;
                    if (cookies.length() > 0) cookies.append("; ");
                    cookies.append(one);
                }
                result.cookies = cookies.toString();
            }
            return result;
        }
    }
    public static Body open(String url, Map<String, String> headers, String accept, String ua, int connectMs, int readMs) throws IOException {
        TaskQueue.check();
        OkHttpClient http = client(connectMs, readMs, true);
        Request request = build(url, "GET", null, false, headers, accept, ua);
        Response response = http.newCall(request).execute();
        int code = response.code();
        if (code < 200 || code >= 300) {
            try {
                response.close();
            } catch (Exception ignored) {
            }
            throw new HttpCode(code);
        }
        ResponseBody responseBody = response.body();
        if (responseBody == null) {
            try {
                response.close();
            } catch (Exception ignored) {
            }
            throw new IOException("Empty");
        }
        return new Body(response, responseBody.byteStream());
    }
    private static boolean isInterrupted(IOException e) {
        if (e instanceof java.io.InterruptedIOException) return true;
        String message = e.getMessage();
        return message != null && (message.contains("Canceled") || message.contains("cancelled") || message.contains("Socket closed"));
    }
}
