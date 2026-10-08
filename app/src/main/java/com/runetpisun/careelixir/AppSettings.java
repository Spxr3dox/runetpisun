package com.runetpisun.careelixir;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public final class AppSettings {
    static final int DEFAULT_LEVEL = 11;

    private final SharedPreferences prefs;

    AppSettings(Context ctx) {
        prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    String tag() { return prefs.getString("player_tag", ""); }

    String apiKey() { return prefs.getString("api_key", ""); }

    void setApi(String tag, String key) {
        prefs.edit()
            .putString("player_tag", normalizeTag(tag))
            .putString("api_key", key.trim())
            .apply();
    }

    boolean hasApi() { return !tag().isEmpty() && !apiKey().isEmpty(); }

    long lastSync() { return prefs.getLong("last_sync", 0); }

    String syncStatus() { return prefs.getString("sync_status", ""); }

    void setSyncStatus(String status, boolean ok) {
        SharedPreferences.Editor e = prefs.edit().putString("sync_status", status);
        if (ok) e.putLong("last_sync", System.currentTimeMillis());
        e.apply();
    }

    List<Card> myDeck() {
        List<Card> deck = new ArrayList<>();
        for (String id : prefs.getString("my_deck", "").split("\\|")) {
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
        prefs.edit().putString("my_deck", sb.toString()).apply();
    }

    int oppLevel() { return prefs.getInt("opp_level", DEFAULT_LEVEL); }

    void setOppLevel(int level) {
        prefs.edit().putInt("opp_level", Math.max(1, Math.min(16, level))).apply();
    }

    static String normalizeTag(String tag) {
        return tag == null ? "" : tag.trim().replace("#", "").toUpperCase().replace('O', '0');
    }
}
