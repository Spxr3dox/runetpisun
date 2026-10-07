package com.runetpisun.croverlay;

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

        if (failures > 0) {
            System.out.println(failures + " check(s) failed");
            System.exit(1);
        }
        System.out.println("TrackerTest: all checks passed");
    }
}
