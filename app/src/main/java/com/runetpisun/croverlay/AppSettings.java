package com.runetpisun.croverlay;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** User settings shared by the setup screen and the overlay. */
final class AppSettings {
    static final int DEFAULT_LEVEL = 11;
    private static final String MY_DECK = "my_deck";
    private static final String MY_LEVEL = "my_level";
    private static final String OPP_LEVEL = "opp_level";

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

    static int clampLevel(int level) {
        return Math.max(1, Math.min(Stats.MAX_LEVEL, level));
    }
}
