package com.runetpisun.croverlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns tracker state + card stats into short prediction lines for the overlay:
 * when a surprise card (Goblin Barrel etc.) can come, what the opponent's spells kill
 * in my deck, and whether my spells kill the troop the opponent just played.
 */
public final class Insights {
    /** Surprise cards -> card whose units they drop (for "does my spell kill it"). */
    private static final Map<String, String> SURPRISES = new HashMap<>();

    static {
        SURPRISES.put("Goblin Barrel", "Goblins");
        SURPRISES.put("Goblin Drill", "Goblins");
        SURPRISES.put("Graveyard", "Skeletons");
        SURPRISES.put("Miner", "Miner");
    }

    private final Tracker tracker;
    private final Stats stats;
    private final List<Card> myDeck;
    private final int myLevel;
    private final int oppLevel;

    public Insights(Tracker tracker, Stats stats, List<Card> myDeck, int myLevel, int oppLevel) {
        this.tracker = tracker;
        this.stats = stats;
        this.myDeck = myDeck;
        this.myLevel = myLevel;
        this.oppLevel = oppLevel;
    }

    public List<String> lines() {
        List<String> ready = new ArrayList<>();
        List<String> later = new ArrayList<>();
        for (Card card : tracker.getDeck()) {
            String line = null;
            if (SURPRISES.containsKey(card.name)) line = surpriseLine(card);
            else if (stats != null && stats.spell(card) != null) line = spellLine(card);
            if (line == null) continue;
            (tracker.queuePosition(card) == 0 ? ready : later).add(line);
        }
        List<String> out = new ArrayList<>(ready);
        out.addAll(later);
        String vs = lastTroopLine();
        if (vs != null) out.add(vs);
        if (myDeck.isEmpty() && stats != null && !out.isEmpty()) {
            out.add("ℹ Вкажи свою колоду в додатку — покажу, кого вб'ють спели");
        }
        return out;
    }

    /** "В РУЦІ ✅", "в руці, 💧 1.4с" or "через 2 ходи". */
    public String status(Card card) {
        int pos = tracker.queuePosition(card);
        if (pos > 0) return "через " + pos + (pos == 1 ? " хід" : " ходи");
        double missing = tracker.costOf(card) - tracker.getElixir();
        if (missing <= 0) return "В РУЦІ ✅";
        double sec = missing * Tracker.SECONDS_PER_ELIXIR / tracker.getMultiplier();
        return String.format(Locale.US, "в руці, 💧 через %.1fс", sec);
    }

    private String surpriseLine(Card card) {
        StringBuilder sb = new StringBuilder("🛢 ").append(card.name).append(": ").append(status(card));
        if (stats == null) return sb.toString();
        Card dropped = Card.byId(SURPRISES.get(card.name));
        List<Stats.Unit> units = dropped == null ? null : stats.units(dropped);
        if (units == null || units.isEmpty()) return sb.toString();
        Stats.Unit unit = units.get(0);
        StringBuilder answers = new StringBuilder();
        for (Card mine : myDeck) {
            Stats.Spell spell = stats.spell(mine);
            if (spell == null) continue;
            int left = spell.hpLeft(myLevel, unit, oppLevel);
            if (left == Integer.MAX_VALUE) continue;
            answers.append(' ').append(mine.shortName).append(left <= 0 ? "✔" : "✗");
        }
        if (answers.length() > 0) sb.append(" ·").append(answers);
        return sb.toString();
    }

    private String spellLine(Card card) {
        Stats.Spell spell = stats.spell(card);
        StringBuilder sb = new StringBuilder("🔥 ").append(card.shortName)
                .append(' ').append(spell.totalDamage(oppLevel))
                .append(" [").append(status(card)).append(']');
        List<String> kills = new ArrayList<>();
        List<String> survives = new ArrayList<>();
        for (Card mine : myDeck) {
            int left = hpLeftAfter(spell, oppLevel, mine, myLevel);
            if (left == Integer.MAX_VALUE) continue;
            if (left <= 0) kills.add(mine.shortName);
            else survives.add(mine.shortName + "(" + left + ")");
        }
        if (!kills.isEmpty()) sb.append(" вб'є: ").append(join(kills));
        if (!survives.isEmpty()) sb.append(" · ні: ").append(join(survives));
        if (spell.towerPercent > 0) sb.append(" · вежа −").append(spell.towerDamage(oppLevel));
        return sb.toString();
    }

    private String lastTroopLine() {
        Card last = tracker.getLastPlayed();
        if (last == null || stats == null || stats.units(last).isEmpty()) return null;
        Stats.Unit unit = stats.units(last).get(0);
        StringBuilder sb = new StringBuilder("🎯 ").append(last.shortName)
                .append(' ').append(unit.hp(oppLevel)).append("HP");
        if (unit.shield(oppLevel) > 0) sb.append("+🛡").append(unit.shield(oppLevel));
        int shown = 0;
        for (Card mine : myDeck) {
            Stats.Spell spell = stats.spell(mine);
            if (spell == null) continue;
            int left = hpLeftAfter(spell, myLevel, last, oppLevel);
            if (left == Integer.MAX_VALUE) continue;
            sb.append(' ').append(mine.shortName).append(left <= 0 ? "✔" : "✗(" + left + ")");
            shown++;
        }
        return shown > 0 ? sb.toString() : null;
    }

    /** HP left on the toughest unit type of a card (all must die for a kill). */
    private int hpLeftAfter(Stats.Spell spell, int spellLevel, Card target, int targetLevel) {
        int worst = Integer.MIN_VALUE;
        for (Stats.Unit unit : stats.units(target)) {
            int left = spell.hpLeft(spellLevel, unit, targetLevel);
            if (left == Integer.MAX_VALUE) return left;
            worst = Math.max(worst, left);
        }
        return worst == Integer.MIN_VALUE ? Integer.MAX_VALUE : worst;
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p);
        }
        return sb.toString();
    }
}
