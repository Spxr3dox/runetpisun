package com.runetpisun.croverlay;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Pulls my card levels/deck and recent opponents' levels from the official Clash Royale API.
 * Goes through the RoyaleAPI proxy because developer keys are bound to a fixed IP and a
 * phone's IP keeps changing (the key must whitelist the proxy IP {@link #PROXY_IP}).
 */
final class ApiSync {
    static final String BASE = "https://proxy.royaleapi.dev/v1";
    static final String PROXY_IP = "45.79.218.79";
    /** Auto-sync (on game start / new battle) at most this often. */
    static final long MIN_INTERVAL_MS = 60_000;

    interface Callback {
        void done(boolean ok, String message);
    }

    private static volatile boolean running;

    /** Syncs unless one is running or the last success was too recent (when !force). */
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
                    msg = "Помилка синхронізації: " + e.getMessage();
                    ok = false;
                }
                settings.setSyncStatus(msg, ok);
                running = false;
                final boolean fOk = ok;
                final String fMsg = msg;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (cb != null) cb.done(fOk, fMsg);
                    }
                });
            }
        }, "cr-api-sync").start();
    }

    private static String sync(AppSettings s) throws Exception {
        String path = "/players/%23" + s.tag();
        String player = get(path, s.apiKey());
        String log = get(path + "/battlelog", s.apiKey());
        ApiParser.Result r = ApiParser.parse(player, log);
        if (!r.myLevels.isEmpty()) s.setMyLevels(r.myLevels);
        if (r.myDeck.size() == Tracker.DECK_SIZE) s.setMyDeck(r.myDeck);
        if (r.oppLevel > 0) s.setOppLevel(r.oppLevel);
        return "✅ Синхронізовано: " + r.myLevels.size() + " карт, колода " + r.myDeck.size() + "/8"
                + (r.oppLevel > 0 ? ", рівень суперників ≈" + r.oppLevel + " (за " + r.battlesUsed + " боями)" : "");
    }

    private static String get(String path, String key) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(15_000);
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("Accept", "application/json");
        try {
            int code = c.getResponseCode();
            if (code == 403) throw new IOException("403 — ключ невірний або в ньому не вказано IP " + PROXY_IP);
            if (code == 404) throw new IOException("404 — гравця з таким тегом не знайдено");
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
