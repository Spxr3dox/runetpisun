package com.runetpisun.croverlay;

import java.util.HashMap;
import java.util.Map;

/** Card levels for both sides: exact per-card where known, otherwise a default level. */
public final class AppLevels {
    private final Map<String, Integer> mine;
    private final int myDefault;
    private final int oppDefault;

    public AppLevels(Map<String, Integer> mine, int myDefault, int oppDefault) {
        this.mine = mine == null ? new HashMap<String, Integer>() : mine;
        this.myDefault = myDefault;
        this.oppDefault = oppDefault;
    }

    public int mine(Card card) {
        Integer l = mine.get(card.name);
        return l != null ? l : myDefault;
    }

    public int opponent(Card card) {
        return oppDefault;
    }

    public int oppDefault() {
        return oppDefault;
    }

    static int clamp(int level) {
        return Math.max(1, Math.min(Stats.MAX_LEVEL, level));
    }
}
