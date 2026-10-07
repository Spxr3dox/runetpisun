package com.runetpisun.croverlay;

import java.util.List;

/** Plain-JVM checks for Tracker (run by tools/build.sh, no JUnit needed). */
public final class TrackerTest {
    private static int failures;

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-6;
    }

    public static void main(String[] args) {
        Card hog = Card.byId("Hog Rider");
        Card log = Card.byId("The Log");
        Card skel = Card.byId("Skeletons");
        Card cannon = Card.byId("Cannon");
        Card fireball = Card.byId("Fireball");
        Card mirror = Card.byId("Mirror");
        Card aq = Card.byId("Archer Queen");

        Tracker t = new Tracker();
        t.start(0);
        t.tick(2800);
        check(near(t.getElixir(), 6.0), "regen 1 per 2.8s at x1: " + t.getElixir());
        t.tick(20_000);
        check(near(t.getElixir(), 10.0), "capped at 10");

        t.play(hog, 20_000);
        check(near(t.getElixir(), 6.0), "hog costs 4");
        Card[] q = t.getQueue();
        check(q[0] == null && q[3] == hog, "first play goes to back of queue");
        check(t.queuePosition(hog) == 4, "hog queue pos 4");

        t.play(log, 20_000);
        t.play(skel, 20_000);
        t.play(cannon, 20_000);
        q = t.getQueue();
        check(q[0] == hog && q[1] == log && q[2] == skel && q[3] == cannon, "queue order after 4 plays");
        check(t.getHand().isEmpty(), "no known hand yet");

        t.play(fireball, 20_000);
        check(t.queuePosition(hog) == 0, "hog back in hand after 4 more plays");
        check(t.getHand().size() == 1 && t.getHand().get(0) == hog, "hand = [hog]");
        check(t.getDeck().size() == 5, "deck size 5");

        double before = t.getElixir();
        t.play(fireball, 20_000); // impossible repeat (mis-tap) must not corrupt the queue
        check(t.getQueue()[3] == fireball, "repeat keeps fireball at back");
        t.undo(20_000);
        check(near(t.getElixir(), before), "undo restores elixir");
        check(t.getDeck().size() == 5, "undo of known card keeps deck");

        // Clamp: under-counted elixir calibrates to 0, undo restores the applied delta only.
        Tracker c = new Tracker();
        c.start(0);
        c.play(Card.byId("Golem"), 0);
        check(near(c.getElixir(), 0.0), "clamped at 0");
        c.undo(0);
        check(near(c.getElixir(), 5.0), "undo after clamp -> 5");
        check(c.getDeck().isEmpty(), "undo removes newly added card");

        // Mirror costs previous card + 1.
        Tracker m = new Tracker();
        m.start(0);
        m.tick(14_000);
        m.play(fireball, 14_000);
        check(m.costOf(mirror) == 5, "mirror fireball = 5");
        m.play(mirror, 14_000);
        check(near(m.getElixir(), 1.0), "10 - 4 - 5 = 1: " + m.getElixir());

        // Multipliers.
        Tracker x = new Tracker();
        x.start(0);
        x.tick(Tracker.DOUBLE_FROM_MS);
        check(x.getMultiplier() == 2, "x2 after 2:00");
        x.tick(Tracker.TRIPLE_FROM_MS);
        check(x.getMultiplier() == 3, "x3 in late overtime");
        x.cycleMultiplier();
        check(x.getMultiplier() == 1 && !x.isAutoMultiplier(), "override x1");

        // Champion ability.
        Tracker ch = new Tracker();
        ch.start(0);
        ch.play(aq, 0);
        check(ch.getChampion() == aq, "champion detected");
        ch.useAbility(aq, 0);
        check(near(ch.getElixir(), 0.0), "5 - 5 - 1 clamps to 0");
        check(ch.getPlayCount() == 1, "ability is not a play");

        // Leak: sitting at 10 wastes elixir.
        Tracker l = new Tracker();
        l.start(0);
        l.tick(14_000 + 5_600);
        check(near(l.getLeaked(), 2.0), "leaked 2 after 5.6s at cap: " + l.getLeaked());

        // Clock sync: battle started 2.8s earlier than tapped -> +1 elixir.
        Tracker s = new Tracker();
        s.start(0);
        s.shiftClock(2_800, 0);
        check(near(s.getElixir(), 6.0) && s.getElapsedMs() == 2_800, "shiftClock +2.8s");
        s.shiftClock(-2_800, 0);
        check(near(s.getElixir(), 5.0) && s.getElapsedMs() == 0, "shiftClock back");

        // Elixir Collector: 1 per 9s after 1s deploy, 7 ticks in 65s, +1 on death.
        Tracker p = new Tracker();
        p.start(0);
        p.tick(2_800); // 6.0
        p.play(Card.byId("Elixir Collector"), 2_800); // 0.0
        check(p.hasActivePump(), "pump active");
        p.pause(2_800);
        p.cycleMultiplier(); // irrelevant while paused; restore auto below
        p.cycleMultiplier();
        p.cycleMultiplier();
        p.cycleMultiplier();
        p.start(2_800);
        p.adjust(-10, 2_800);
        p.tick(2_800 + 10_000); // regen 10s = 3.571 + pump 1
        check(near(p.getElixir(), 10_000 / 2_800.0 + 1), "pump first elixir after 10s: " + p.getElixir());
        p.destroyPump(2_800 + 10_000);
        check(!p.hasActivePump() && near(p.getElixir(), 10_000 / 2_800.0 + 2), "pump death +1");
        p.undo(2_800 + 10_000); // undo adjust(-10)
        p.undo(2_800 + 10_000); // undo pump play removes its 2 elixir
        check(!p.hasActivePump(), "undo removes pump");

        // Stats + insights on the bundled data.
        try {
            Stats st = Stats.parse(new java.io.FileReader(args.length > 0 ? args[0] : "app/src/main/assets/stats.tsv"));
            Stats.Spell fb = st.spell(fireball);
            check(fb != null && fb.totalDamage(11) == 689, "fireball lvl11 = 689");
            check(fb.towerDamage(11) == 207, "fireball tower lvl11 = 207: " + fb.towerDamage(11));
            Stats.Unit musk = st.units(Card.byId("Musketeer")).get(0);
            check(musk.hp(11) == 720, "musketeer lvl11 = 720");
            check(fb.hpLeft(11, musk, 11) == 31, "fireball leaves musketeer at 31");
            check(fb.hpLeft(12, musk, 11) <= 0, "lvl12 fireball kills lvl11 musketeer");
            Stats.Unit goblin = st.units(Card.byId("Goblins")).get(0);
            check(st.spell(log).hpLeft(11, goblin, 11) <= 0, "log kills goblins");
            Stats.Unit guard = st.units(Card.byId("Guards")).get(0);
            check(guard.shield(11) > 0 && st.spell(log).hpLeft(16, guard, 11) > 0, "shield absorbs the log");
            check(st.spell(Card.byId("Arrows")).hpLeft(11, guard, 11) <= 0, "arrows (3 waves) kill guards");

            Tracker it = new Tracker();
            it.start(0);
            it.play(Card.byId("Goblin Barrel"), 0);
            it.play(fireball, 0);
            List<Card> mine = new java.util.ArrayList<>();
            mine.add(log);
            mine.add(Card.byId("Musketeer"));
            Insights in = new Insights(it, st, mine, new AppLevels(null, 11, 11));
            List<String> lines = in.lines();
            check(lines.size() == 2, "2 insight lines (last play is a spell): " + lines);
            check(lines.get(0).contains("через 3") && lines.get(0).contains("Log✔"), "barrel line: " + lines.get(0));
            check(lines.get(1).contains("Musket(31)"), "fireball line: " + lines.get(1));
            it.play(Card.byId("Knight"), 0);
            check(in.status(Card.byId("Goblin Barrel")).startsWith("через 2"), "barrel in 2");
            lines = in.lines();
            check(lines.get(lines.size() - 1).startsWith("🎯 Knight"), "last troop line: " + lines);
            it.play(Card.byId("Archers"), 0);
            check(in.status(Card.byId("Goblin Barrel")).startsWith("через 1"), "barrel next");
            it.play(Card.byId("Zap"), 0);
            check(in.status(Card.byId("Goblin Barrel")).startsWith("в руці"), "barrel back in hand, elixir short");
        } catch (java.io.IOException e) {
            check(false, "stats load: " + e);
        }

        // API parsing: per-rarity levels -> in-game levels, deck, opponents' average.
        try {
            String player = "{\"cards\":["
                    + "{\"name\":\"Knight\",\"level\":14,\"maxLevel\":16},"
                    + "{\"name\":\"Musketeer\",\"level\":12,\"maxLevel\":14},"
                    + "{\"name\":\"The Log\",\"level\":5,\"maxLevel\":8}],"
                    + "\"currentDeck\":[{\"name\":\"Knight\",\"level\":14,\"maxLevel\":16},"
                    + "{\"name\":\"The Log\",\"level\":5,\"maxLevel\":8},{\"name\":\"Unknown New Card\",\"level\":1,\"maxLevel\":6}]}";
            String battlelog = "[{\"opponent\":[{\"cards\":[{\"name\":\"Hog Rider\",\"level\":11,\"maxLevel\":14},"
                    + "{\"name\":\"Fireball\",\"level\":11,\"maxLevel\":14}]}]},"
                    + "{\"opponent\":[{\"cards\":[{\"name\":\"Golem\",\"level\":8,\"maxLevel\":11}]}]}]";
            ApiParser.Result r = ApiParser.parse(player, battlelog);
            check(r.myLevels.get("Knight") == 14 && r.myLevels.get("Musketeer") == 14 && r.myLevels.get("The Log") == 13,
                    "api levels: " + r.myLevels);
            check(r.myDeck.size() == 2, "unknown cards skipped in deck");
            check(r.oppLevel == 13 && r.battlesUsed == 2, "opp level 13 from (13,13,13): " + r.oppLevel);
            check(r.lastOpponent.get("Fireball") == 13, "last opponent fireball 13");
            check(ApiParser.normalizeTag(" #2pp0l ").equals("2PP0L"), "tag normalize");
            AppLevels lv = new AppLevels(r.myLevels, 11, r.oppLevel);
            check(lv.mine(Card.byId("Knight")) == 14 && lv.mine(Card.byId("Zap")) == 11, "levels lookup");
        } catch (Exception e) {
            check(false, "api parse: " + e);
        }

        if (failures > 0) {
            System.out.println(failures + " check(s) failed");
            System.exit(1);
        }
        System.out.println("TrackerTest: all checks passed");
    }
}
