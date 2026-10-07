package com.runetpisun.croverlay;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses official Clash Royale API responses (/players/{tag}, /players/{tag}/battlelog).
 * The API reports levels per rarity (e.g. a legendary "level 1" of max 6); the in-game level
 * is level + (common max - card max), which also works if the API already uses unified levels.
 */
public final class ApiParser {
    /** Result of a sync: my per-card levels and deck, and the opponents' estimated level. */
    public static final class Result {
        public final Map<String, Integer> myLevels = new HashMap<>();
        public final List<Card> myDeck = new ArrayList<>();
        /** Average card level of recent opponents, 0 if no battles. */
        public int oppLevel;
        public int battlesUsed;
        /** Opponent of the most recent battle: card name -> level. */
        public final Map<String, Integer> lastOpponent = new HashMap<>();
    }

    /** How many recent battles feed the opponent level estimate. */
    static final int RECENT_BATTLES = 5;

    public static Result parse(String playerJson, String battlelogJson) throws JSONException {
        Result r = new Result();
        JSONObject player = new JSONObject(playerJson);
        JSONArray cards = player.optJSONArray("cards");
        int commonMax = commonMax(cards);
        if (cards != null) {
            for (int i = 0; i < cards.length(); i++) {
                JSONObject c = cards.getJSONObject(i);
                r.myLevels.put(c.getString("name"), level(c, commonMax));
            }
        }
        JSONArray deck = player.optJSONArray("currentDeck");
        if (deck != null) {
            for (int i = 0; i < deck.length(); i++) {
                JSONObject c = deck.getJSONObject(i);
                Card card = Card.byId(c.getString("name"));
                if (card != null && !r.myDeck.contains(card)) r.myDeck.add(card);
                r.myLevels.put(c.getString("name"), level(c, commonMax));
            }
        }

        if (battlelogJson != null) {
            JSONArray battles = new JSONArray(battlelogJson);
            long sum = 0;
            int count = 0;
            for (int b = 0; b < battles.length() && r.battlesUsed < RECENT_BATTLES; b++) {
                JSONArray opponents = battles.getJSONObject(b).optJSONArray("opponent");
                if (opponents == null || opponents.length() == 0) continue;
                JSONArray oppCards = opponents.getJSONObject(0).optJSONArray("cards");
                if (oppCards == null || oppCards.length() == 0) continue;
                for (int i = 0; i < oppCards.length(); i++) {
                    JSONObject c = oppCards.getJSONObject(i);
                    int lvl = level(c, commonMax);
                    sum += lvl;
                    count++;
                    if (r.battlesUsed == 0) r.lastOpponent.put(c.getString("name"), lvl);
                }
                r.battlesUsed++;
            }
            if (count > 0) r.oppLevel = Math.round((float) sum / count);
        }
        return r;
    }

    private static int commonMax(JSONArray cards) throws JSONException {
        int max = 0;
        if (cards != null) {
            for (int i = 0; i < cards.length(); i++) max = Math.max(max, cards.getJSONObject(i).optInt("maxLevel"));
        }
        return max > 0 ? max : Stats.MAX_LEVEL;
    }

    static int level(JSONObject card, int commonMax) {
        int level = card.optInt("level", 1);
        int max = card.optInt("maxLevel", commonMax);
        return AppLevels.clamp(level + Math.max(0, commonMax - max));
    }

    /** "#2PP" / "2pp" / " #2PP " -> "2PP". */
    public static String normalizeTag(String tag) {
        return tag == null ? "" : tag.trim().replace("#", "").toUpperCase().replace('O', '0');
    }
}
