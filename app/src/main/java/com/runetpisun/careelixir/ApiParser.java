package com.runetpisun.careelixir;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ApiParser {

    public static final class Result {
        public final List<Card> myDeck = new ArrayList<>();
        public int oppLevel;
        public int battlesUsed;
    }

    static final int RECENT_BATTLES = 5;

    public static Result parse(String playerJson, String battlelogJson) throws JSONException {
        Result r = new Result();
        JSONObject player = new JSONObject(playerJson);

        JSONArray deck = player.optJSONArray("currentDeck");
        if (deck != null) {
            for (int i = 0; i < deck.length(); i++) {
                String name = deck.getJSONObject(i).getString("name");
                Card card = Card.byId(name);
                if (card != null && !r.myDeck.contains(card)) r.myDeck.add(card);
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
                    int lvl = c.optInt("level", 1);
                    int max = c.optInt("maxLevel", 14);
                    sum += lvl + Math.max(0, 14 - max);
                    count++;
                }
                r.battlesUsed++;
            }
            if (count > 0) r.oppLevel = Math.round((float) sum / count);
        }
        return r;
    }
}
