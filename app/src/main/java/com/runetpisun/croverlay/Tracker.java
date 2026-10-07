package com.runetpisun.croverlay;

import java.util.ArrayList;
import java.util.List;

/**
 * Opponent state model: elixir regeneration, discovered deck and card cycle.
 * Pure Java (no Android deps) so it can be unit-tested on the JVM.
 *
 * Cycle model: deck = 8 cards, 4 in hand + queue of 4. A played card goes to the
 * back of the queue and the front of the queue enters the hand. So after k plays the
 * queue is (4 - min(k,4)) unknown starting cards followed by the last min(k,4) plays.
 */
public final class Tracker {
    public static final double MAX_ELIXIR = 10.0;
    public static final double START_ELIXIR = 5.0;
    /** Seconds per elixir at 1x. */
    public static final double SECONDS_PER_ELIXIR = 2.8;
    public static final long REGULAR_MS = 180_000;
    public static final long DOUBLE_FROM_MS = 120_000;
    public static final long TRIPLE_FROM_MS = 240_000;
    public static final int DECK_SIZE = 8;
    public static final int QUEUE_SIZE = 4;

    private enum Kind { PLAY, ABILITY, ADJUST }

    private static final class Action {
        final Kind kind;
        final Card card;
        final double appliedDelta;
        final boolean addedToDeck;

        Action(Kind kind, Card card, double appliedDelta, boolean addedToDeck) {
            this.kind = kind;
            this.card = card;
            this.appliedDelta = appliedDelta;
            this.addedToDeck = addedToDeck;
        }
    }

    private final List<Action> actions = new ArrayList<>();
    private final List<Card> deck = new ArrayList<>();
    private boolean running;
    private long elapsedMs;
    private long lastTickMs;
    private double elixir = START_ELIXIR;
    /** 0 = auto by battle time, otherwise forced 1/2/3. */
    private int multiplierOverride;

    public void reset() {
        actions.clear();
        deck.clear();
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

    public boolean isRunning() {
        return running;
    }

    public void tick(long nowMs) {
        if (!running) return;
        long dt = nowMs - lastTickMs;
        lastTickMs = nowMs;
        if (dt <= 0) return;
        elixir = Math.min(MAX_ELIXIR, elixir + dt / 1000.0 * getMultiplier() / SECONDS_PER_ELIXIR);
        elapsedMs += dt;
    }

    public double getElixir() {
        return elixir;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public int getMultiplier() {
        if (multiplierOverride != 0) return multiplierOverride;
        if (elapsedMs >= TRIPLE_FROM_MS) return 3;
        if (elapsedMs >= DOUBLE_FROM_MS) return 2;
        return 1;
    }

    public boolean isAutoMultiplier() {
        return multiplierOverride == 0;
    }

    /** auto -> x1 -> x2 -> x3 -> auto. */
    public void cycleMultiplier() {
        multiplierOverride = (multiplierOverride + 1) % 4;
    }

    /** Opponent played a card. Starts the clock if it was not running. */
    public void play(Card card, long nowMs) {
        start(nowMs);
        tick(nowMs);
        int cost = costOf(card);
        boolean added = false;
        if (!deck.contains(card) && deck.size() < DECK_SIZE) {
            deck.add(card);
            added = true;
        }
        actions.add(new Action(Kind.PLAY, card, applyDelta(-cost), added));
    }

    /** Elixir the card costs right now (Mirror = last played card + 1). */
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

    /** Manual correction, e.g. Elixir Collector output or a missed tick. */
    public void adjust(double delta, long nowMs) {
        tick(nowMs);
        actions.add(new Action(Kind.ADJUST, null, applyDelta(delta), false));
    }

    public boolean canUndo() {
        return !actions.isEmpty();
    }

    public void undo(long nowMs) {
        if (actions.isEmpty()) return;
        tick(nowMs);
        Action a = actions.remove(actions.size() - 1);
        elixir = clamp(elixir - a.appliedDelta);
        if (a.addedToDeck) deck.remove(a.card);
    }

    private double applyDelta(double delta) {
        double before = elixir;
        // Going below zero means we under-counted: the opponent had at least the cost,
        // so clamping to 0 self-calibrates the counter.
        elixir = clamp(elixir + delta);
        return elixir - before;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(MAX_ELIXIR, v));
    }

    public List<Card> getDeck() {
        return new ArrayList<>(deck);
    }

    public int getPlayCount() {
        int n = 0;
        for (Action a : actions) if (a.kind == Kind.PLAY) n++;
        return n;
    }

    /**
     * Queue in return order: index 0 is the card that enters the hand on the next play.
     * Null entries are still-unknown starting cards.
     */
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

    /** 0 = in hand (or unknown), 1..4 = position in queue (1 = next to return). */
    public int queuePosition(Card card) {
        Card[] queue = getQueue();
        for (int i = 0; i < queue.length; i++) {
            if (queue[i] == card) return i + 1;
        }
        return 0;
    }

    /** Known cards currently in the opponent's hand. */
    public List<Card> getHand() {
        List<Card> hand = new ArrayList<>();
        for (Card c : deck) if (queuePosition(c) == 0) hand.add(c);
        return hand;
    }

    /** First champion in the discovered deck, or null. */
    public Card getChampion() {
        for (Card c : deck) if (c.isChampion()) return c;
        return null;
    }
}
