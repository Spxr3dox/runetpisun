package com.runetpisun.careelixir;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class ApiSync {
    static final String BASE = "https://proxy.royaleapi.dev/v1";
    static final long MIN_INTERVAL_MS = 60_000;

    interface Callback {
        void done(boolean ok, String message);
    }

    private static volatile boolean running;

    static void syncAsync(Context ctx, boolean force, final Callback cb) {
        final AppSettings settings = new AppSettings(ctx);
        if (!settings.hasApi() || running) return;
        if (!force && System.currentTimeMillis() - settings.lastSync() < MIN_INTERVAL_MS) return;
        running = true;
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok;
                String msg;
                try {
                    msg = sync(settings);
                    ok = true;
                } catch (Exception e) {
                    msg = "Помилка: " + e.getMessage();
                    ok = false;
                }
                settings.setSyncStatus(msg, ok);
                running = false;
                final boolean fOk = ok;
                final String fMsg = msg;
                main.post(new Runnable() {
                    @Override
                    public void run() { if (cb != null) cb.done(fOk, fMsg); }
                });
            }
        }, "api-sync").start();
    }

    private static String sync(AppSettings s) throws Exception {
        String path = "/players/%23" + s.tag();
        String player = get(path, s.apiKey());
        String log = get(path + "/battlelog", s.apiKey());
        ApiParser.Result r = ApiParser.parse(player, log);
        if (r.myDeck.size() == Tracker.DECK_SIZE) s.setMyDeck(r.myDeck);
        if (r.oppLevel > 0) s.setOppLevel(r.oppLevel);
        return "✅ Колода " + r.myDeck.size() + "/8, рівень суперників ≈" + r.oppLevel;
    }

    private static String get(String path, String key) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(15_000);
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("Accept", "application/json");
        try {
            int code = c.getResponseCode();
            if (code == 403) throw new IOException("403 — невірний ключ або IP");
            if (code == 404) throw new IOException("404 — гравця не знайдено");
            if (code != 200) throw new IOException("HTTP " + code);
            return read(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
