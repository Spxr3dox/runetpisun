package com.runetpisun.croverlay;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** User settings shared by the setup screen and the overlay. */
final class AppSettings {
    static final int DEFAULT_LEVEL = 11;
    private static final String MY_DECK = "my_deck";
    private static final String MY_LEVEL = "my_level";
    private static final String OPP_LEVEL = "opp_level";
    private static final String MY_LEVELS = "my_levels";
    private static final String TAG = "player_tag";
    private static final String API_KEY = "api_key";
    private static final String LAST_SYNC = "last_sync";
    private static final String SYNC_STATUS = "sync_status";

    private final SharedPreferences prefs;

    AppSettings(Context ctx) {
        prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    List<Card> myDeck() {
        List<Card> deck = new ArrayList<>();
        for (String id : prefs.getString(MY_DECK, "").split("\\|")) {
            Card c = Card.byId(id);
            if (c != null) deck.add(c);
        }
        return deck;
    }

    void setMyDeck(List<Card> deck) {
        StringBuilder sb = new StringBuilder();
        for (Card c : deck) {
            if (sb.length() > 0) sb.append('|');
            sb.append(c.id);
        }
        prefs.edit().putString(MY_DECK, sb.toString()).apply();
    }

    int myLevel() {
        return prefs.getInt(MY_LEVEL, DEFAULT_LEVEL);
    }

    void setMyLevel(int level) {
        prefs.edit().putInt(MY_LEVEL, clampLevel(level)).apply();
    }

    int oppLevel() {
        return prefs.getInt(OPP_LEVEL, DEFAULT_LEVEL);
    }

    void setOppLevel(int level) {
        prefs.edit().putInt(OPP_LEVEL, clampLevel(level)).apply();
    }

    /** Exact per-card levels from the API (card name -> level). */
    Map<String, Integer> myLevels() {
        Map<String, Integer> out = new HashMap<>();
        for (String entry : prefs.getString(MY_LEVELS, "").split("\\|")) {
            int eq = entry.lastIndexOf('=');
            if (eq <= 0) continue;
            try {
                out.put(entry.substring(0, eq), Integer.parseInt(entry.substring(eq + 1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    void setMyLevels(Map<String, Integer> levels) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : levels.entrySet()) {
            if (sb.length() > 0) sb.append('|');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        prefs.edit().putString(MY_LEVELS, sb.toString()).apply();
    }

    AppLevels levels() {
        return new AppLevels(myLevels(), myLevel(), oppLevel());
    }

    String tag() {
        return prefs.getString(TAG, "");
    }

    String apiKey() {
        return prefs.getString(API_KEY, "");
    }

    void setApi(String tag, String key) {
        prefs.edit().putString(TAG, ApiParser.normalizeTag(tag)).putString(API_KEY, key.trim()).apply();
    }

    boolean hasApi() {
        return !tag().isEmpty() && !apiKey().isEmpty();
    }

    long lastSync() {
        return prefs.getLong(LAST_SYNC, 0);
    }

    String syncStatus() {
        return prefs.getString(SYNC_STATUS, "");
    }

    void setSyncStatus(String status, boolean ok) {
        SharedPreferences.Editor e = prefs.edit().putString(SYNC_STATUS, status);
        if (ok) e.putLong(LAST_SYNC, System.currentTimeMillis());
        e.apply();
    }

    static int clampLevel(int level) {
        return Math.max(1, Math.min(Stats.MAX_LEVEL, level));
    }
}
