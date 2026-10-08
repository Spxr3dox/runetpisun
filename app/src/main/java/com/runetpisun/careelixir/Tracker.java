package com.runetpisun.careelixir;

import java.util.ArrayList;
import java.util.List;

public final class Tracker {
    public static final double MAX_ELIXIR = 10.0;
    public static final double START_ELIXIR = 5.0;
    public static final double SECONDS_PER_ELIXIR = 2.8;
    public static final long REGULAR_MS = 180_000;
    public static final long DOUBLE_FROM_MS = 120_000;
    public static final long TRIPLE_FROM_MS = 240_000;
    public static final int DECK_SIZE = 8;
    public static final int QUEUE_SIZE = 4;

    static final long PUMP_DEPLOY_MS = 1_000;
    static final long PUMP_INTERVAL_MS = 9_000;
    static final long PUMP_LIFE_MS = 65_000;
    static final String PUMP_ID = "Elixir Collector";

    private enum Kind { PLAY, ABILITY, ADJUST }

    private static final class Action {
        final Kind kind;
        final Card card;
        final double appliedDelta;
        final boolean addedToDeck;
        Pump pump;

        Action(Kind kind, Card card, double appliedDelta, boolean addedToDeck) {
            this.kind = kind;
            this.card = card;
            this.appliedDelta = appliedDelta;
            this.addedToDeck = addedToDeck;
        }
    }

    private static final class Pump {
        long placedAtMs;
        int produced;
        boolean dead;
        double applied;

        Pump(long placedAtMs) { this.placedAtMs = placedAtMs; }
    }

    private final List<Action> actions = new ArrayList<>();
    private final List<Card> deck = new ArrayList<>();
    private final List<Pump> pumps = new ArrayList<>();
    private double leaked;
    private boolean running;
    private long elapsedMs;
    private long lastTickMs;
    private double elixir = START_ELIXIR;
    private int multiplierOverride;

    public void reset() {
        actions.clear();
        deck.clear();
        pumps.clear();
        leaked = 0;
        running = false;
        elapsedMs = 0;
        elixir = START_ELIXIR;
        multiplierOverride = 0;
    }

    public void start(long nowMs) {
        if (running) return;
        running = true;
        lastTickMs = nowMs;
    }

    public void pause(long nowMs) {
        tick(nowMs);
        running = false;
    }

    public boolean isRunning() { return running; }

    public void tick(long nowMs) {
        if (!running) return;
        long dt = nowMs - lastTickMs;
        lastTickMs = nowMs;
        if (dt <= 0) return;
        regen(dt);
        elapsedMs += dt;
        runPumps();
    }

    private void regen(long dt) {
        double next = elixir + dt / 1000.0 * getMultiplier() / SECONDS_PER_ELIXIR;
        if (next > MAX_ELIXIR) leaked += next - MAX_ELIXIR;
        elixir = Math.min(MAX_ELIXIR, next);
    }

    private void runPumps() {
        for (Pump p : pumps) {
            if (p.dead) continue;
            long alive = elapsedMs - p.placedAtMs - PUMP_DEPLOY_MS;
            int due = (int) Math.max(0, Math.min(alive, PUMP_LIFE_MS) / PUMP_INTERVAL_MS);
            while (p.produced < due) {
                p.produced++;
                p.applied += gain(1);
            }
            if (alive >= PUMP_LIFE_MS) killPump(p);
        }
    }

    private void killPump(Pump p) {
        p.dead = true;
        p.applied += gain(1);
    }

    private double gain(double amount) {
        double before = elixir;
        double next = elixir + amount;
        if (next > MAX_ELIXIR) leaked += next - MAX_ELIXIR;
        elixir = Math.min(MAX_ELIXIR, next);
        return elixir - before;
    }

    public void shiftClock(long deltaMs, long nowMs) {
        tick(nowMs);
        long target = Math.max(0, elapsedMs + deltaMs);
        long d = target - elapsedMs;
        if (d > 0) regen(d);
        else elixir = clamp(elixir + d / 1000.0 * getMultiplier() / SECONDS_PER_ELIXIR);
        elapsedMs = target;
        for (Pump p : pumps) p.placedAtMs += d;
        runPumps();
    }

    public double getLeaked() { return leaked; }

    public boolean hasActivePump() {
        for (Pump p : pumps) if (!p.dead) return true;
        return false;
    }

    public void destroyPump(long nowMs) {
        tick(nowMs);
        for (Pump p : pumps) {
            if (!p.dead) { killPump(p); return; }
        }
    }

    public double getElixir() { return elixir; }
    public long getElapsedMs() { return elapsedMs; }

    public int getMultiplier() {
        if (multiplierOverride != 0) return multiplierOverride;
        if (elapsedMs >= TRIPLE_FROM_MS) return 3;
        if (elapsedMs >= DOUBLE_FROM_MS) return 2;
        return 1;
    }

    public boolean isAutoMultiplier() { return multiplierOverride == 0; }

    public void cycleMultiplier() {
        multiplierOverride = (multiplierOverride + 1) % 4;
    }

    public void play(Card card, long nowMs) {
        start(nowMs);
        tick(nowMs);
        int cost = costOf(card);
        boolean added = false;
        if (!deck.contains(card) && deck.size() < DECK_SIZE) {
            deck.add(card);
            added = true;
        }
        Action action = new Action(Kind.PLAY, card, applyDelta(-cost), added);
        if (PUMP_ID.equals(card.id)) {
            action.pump = new Pump(elapsedMs);
            pumps.add(action.pump);
        }
        actions.add(action);
    }

    public int costOf(Card card) {
        if (!card.isMirror()) return card.cost;
        for (int i = actions.size() - 1; i >= 0; i--) {
            Action a = actions.get(i);
            if (a.kind == Kind.PLAY && !a.card.isMirror()) return a.card.cost + 1;
        }
        return 0;
    }

    public void useAbility(Card champion, long nowMs) {
        tick(nowMs);
        actions.add(new Action(Kind.ABILITY, champion, applyDelta(-champion.abilityCost), false));
    }

    public void adjust(double delta, long nowMs) {
        tick(nowMs);
        actions.add(new Action(Kind.ADJUST, null, applyDelta(delta), false));
    }

    public boolean canUndo() { return !actions.isEmpty(); }

    public void undo(long nowMs) {
        if (actions.isEmpty()) return;
        tick(nowMs);
        Action a = actions.remove(actions.size() - 1);
        elixir = clamp(elixir - a.appliedDelta);
        if (a.addedToDeck) deck.remove(a.card);
        if (a.pump != null) {
            pumps.remove(a.pump);
            elixir = clamp(elixir - a.pump.applied);
        }
    }

    private double applyDelta(double delta) {
        double before = elixir;
        elixir = clamp(elixir + delta);
        return elixir - before;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(MAX_ELIXIR, v));
    }

    public List<Card> getDeck() { return new ArrayList<>(deck); }

    public Card getLastPlayed() {
        for (int i = actions.size() - 1; i >= 0; i--) {
            if (actions.get(i).kind == Kind.PLAY) return actions.get(i).card;
        }
        return null;
    }

    public int getPlayCount() {
        int n = 0;
        for (Action a : actions) if (a.kind == Kind.PLAY) n++;
        return n;
    }

    public Card[] getQueue() {
        List<Card> recent = new ArrayList<>();
        for (int i = actions.size() - 1; i >= 0 && recent.size() < QUEUE_SIZE; i--) {
            Action a = actions.get(i);
            if (a.kind == Kind.PLAY && !recent.contains(a.card)) recent.add(0, a.card);
        }
        Card[] queue = new Card[QUEUE_SIZE];
        int offset = QUEUE_SIZE - recent.size();
        for (int i = 0; i < recent.size(); i++) queue[offset + i] = recent.get(i);
        return queue;
    }

    public int queuePosition(Card card) {
        Card[] queue = getQueue();
        for (int i = 0; i < queue.length; i++) {
            if (queue[i] == card) return i + 1;
        }
        return 0;
    }

    public List<Card> getHand() {
        List<Card> hand = new ArrayList<>();
        for (Card c : deck) if (queuePosition(c) == 0) hand.add(c);
        return hand;
    }

    public Card getChampion() {
        for (Card c : deck) if (c.isChampion()) return c;
        return null;
    }
}
