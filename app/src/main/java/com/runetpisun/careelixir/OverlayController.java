package com.runetpisun.careelixir;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

public final class OverlayController {
    private static final long TICK_MS = 100;
    private static final int BG = 0xD9101820;
    private static final int ELIXIR_COLOR = 0xFFE040FB;
    private static final int HAND = 0xFF2E7D32;
    private static final int NEXT = 0xFFF9A825;
    private static final int QUEUE = 0xFF455A64;
    private static final int UNKNOWN = 0xFF1E272C;
    private static final int BTN = 0xFF37474F;
    private static final int AUTO_BADGE = 0xFF00BCD4;

    private final Context ctx;
    private final WindowManager wm;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Tracker tracker;
    private final ScreenAnalyzer analyzer;

    private WindowManager.LayoutParams params;
    private View root;
    private LinearLayout panel;
    private TextView pill;
    private TextView elixirText;
    private TextView clockText;
    private TextView playPauseBtn;
    private TextView undoBtn;
    private TextView abilityBtn;
    private TextView pumpBtn;
    private TextView cycleText;
    private TextView autoStatusText;
    private LinearLayout battlePanel;
    private ElixirBar elixirBar;
    private final TextView[] slots = new TextView[Tracker.DECK_SIZE];
    private LinearLayout picker;
    private LinearLayout pickerGrid;
    private final TextView[] tabs = new TextView[9];
    private int pickerTab = 4;
    private boolean shown;
    private boolean collapsed;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tracker.tick(SystemClock.elapsedRealtime());
            refreshLive();
            handler.postDelayed(this, TICK_MS);
        }
    };

    public OverlayController(Context ctx) {
        this.ctx = ctx;
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        this.prefs = ctx.getSharedPreferences("overlay", Context.MODE_PRIVATE);
        this.tracker = ElixirBridge.getTracker();
        this.analyzer = ElixirBridge.getAnalyzer();
        setupAnalyzer();
    }

    private void setupAnalyzer() {
        analyzer.buildCardProfiles();
        analyzer.setListener(new ScreenAnalyzer.DetectionListener() {
            @Override
            public void onCardDetected(final Card card, float confidence) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        tracker.play(card, SystemClock.elapsedRealtime());
                        refreshAll();
                    }
                });
            }

            @Override
            public void onDeploymentDetected(final int elixirCost) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        tracker.adjust(-elixirCost, SystemClock.elapsedRealtime());
                        refreshAll();
                    }
                });
            }
        });
    }

    void show() {
        if (shown) return;
        if (root == null) build();
        wm.addView(root, params);
        shown = true;
        refreshAll();
        handler.post(ticker);
    }

    void hide() {
        if (!shown) return;
        handler.removeCallbacks(ticker);
        wm.removeViewImmediate(root);
        shown = false;
    }

    private void build() {
        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = prefs.getInt("x", dp(8));
        params.y = prefs.getInt("y", dp(140));
        collapsed = prefs.getBoolean("collapsed", false);

        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        root = container;

        pill = text("", 16, Color.WHITE);
        pill.setTypeface(Typeface.DEFAULT_BOLD);
        pill.setBackground(rounded(BG, 18));
        pill.setPadding(dp(12), dp(6), dp(12), dp(6));
        pill.setOnTouchListener(new DragListener(new Runnable() {
            @Override
            public void run() { setCollapsed(false); }
        }));
        container.addView(pill);

        panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(rounded(BG, 12));
        panel.setPadding(dp(6), dp(4), dp(6), dp(6));
        container.addView(panel, new LinearLayout.LayoutParams(dp(268), LinearLayout.LayoutParams.WRAP_CONTENT));

        panel.addView(buildHeader());

        autoStatusText = text("", 10, AUTO_BADGE);
        autoStatusText.setPadding(dp(2), dp(2), dp(2), dp(2));
        panel.addView(autoStatusText);

        battlePanel = buildBattlePanel();
        battlePanel.setVisibility(View.GONE);
        panel.addView(battlePanel);

        elixirBar = new ElixirBar(ctx);
        panel.addView(elixirBar, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(10)));
        panel.addView(buildDeckRow(0));
        panel.addView(buildDeckRow(4));

        cycleText = text("", 11, 0xFFCFD8DC);
        cycleText.setPadding(dp(2), dp(3), dp(2), dp(3));
        panel.addView(cycleText);

        panel.addView(buildActionRow());
        picker = buildPicker();
        picker.setVisibility(View.GONE);
        panel.addView(picker);

        applyCollapsed();
    }

    private View buildHeader() {
        LinearLayout row = hRow();
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView handle = text("⠿", 18, 0xFF90A4AE);
        handle.setPadding(dp(2), 0, dp(6), 0);
        handle.setOnTouchListener(new DragListener(null));
        row.addView(handle);

        elixirText = text("5.0", 22, ELIXIR_COLOR);
        elixirText.setTypeface(Typeface.DEFAULT_BOLD);
        elixirText.setOnTouchListener(new DragListener(null));
        row.addView(elixirText, new LinearLayout.LayoutParams(dp(54), LinearLayout.LayoutParams.WRAP_CONTENT));

        clockText = text("3:00 x1", 11, Color.WHITE);
        clockText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tracker.cycleMultiplier();
                refreshLive();
            }
        });
        clockText.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                battlePanel.setVisibility(
                    battlePanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                return true;
            }
        });
        row.addView(clockText, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        playPauseBtn = button("▶", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long now = SystemClock.elapsedRealtime();
                if (tracker.isRunning()) tracker.pause(now); else tracker.start(now);
                refreshAll();
            }
        });
        row.addView(playPauseBtn);

        undoBtn = button("↶", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tracker.undo(SystemClock.elapsedRealtime());
                refreshAll();
            }
        });
        row.addView(undoBtn);

        TextView reset = button("⟲", null);
        reset.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                tracker.reset();
                showPicker(false);
                refreshAll();
                return true;
            }
        });
        row.addView(reset);

        row.addView(button("–", new View.OnClickListener() {
            @Override
            public void onClick(View v) { setCollapsed(true); }
        }));

        return row;
    }

    private View buildDeckRow(int from) {
        LinearLayout row = hRow();
        for (int i = from; i < from + 4; i++) {
            final int index = i;
            TextView slot = text("?", 11, Color.WHITE);
            slot.setGravity(Gravity.CENTER);
            slot.setMaxLines(2);
            slot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { onSlotTap(index); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1);
            lp.setMargins(dp(2), dp(3), dp(2), 0);
            row.addView(slot, lp);
            slots[i] = slot;
        }
        return row;
    }

    private View buildActionRow() {
        LinearLayout row = hRow();
        row.addView(button("-1", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tracker.adjust(-1, SystemClock.elapsedRealtime());
                refreshAll();
            }
        }), weighted());
        row.addView(button("+1", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tracker.adjust(1, SystemClock.elapsedRealtime());
                refreshAll();
            }
        }), weighted());
        abilityBtn = button("⚡", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Card champ = tracker.getChampion();
                if (champ != null) tracker.useAbility(champ, SystemClock.elapsedRealtime());
                refreshAll();
            }
        });
        row.addView(abilityBtn, weighted());
        pumpBtn = button("💀Pump", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tracker.destroyPump(SystemClock.elapsedRealtime());
                refreshAll();
            }
        });
        pumpBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        pumpBtn.setVisibility(View.GONE);
        row.addView(pumpBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2));
        row.addView(button("+ карта", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPicker(picker.getVisibility() != View.VISIBLE);
            }
        }), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2));
        return row;
    }

    private LinearLayout buildBattlePanel() {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);

        TextView hint = text("Синхронізуй з таймером гри:", 10, 0xFFB0BEC5);
        box.addView(hint);
        LinearLayout time = hRow();
        long[] shifts = {-5_000, -1_000, 1_000, 5_000};
        String[] labels = {"−5с", "−1с", "+1с", "+5с"};
        for (int i = 0; i < shifts.length; i++) {
            final long shift = shifts[i];
            time.addView(button(labels[i], new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tracker.shiftClock(shift, SystemClock.elapsedRealtime());
                    refreshAll();
                }
            }), weighted());
        }
        box.addView(time);
        return box;
    }

    private LinearLayout buildPicker() {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(4), 0, 0);

        LinearLayout tabRow = hRow();
        String[] labels = {"★", "1", "2", "3", "4", "5", "6", "7", "8+"};
        for (int i = 0; i < labels.length; i++) {
            final int cost = i;
            TextView tab = button(labels[i], new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pickerTab = cost;
                    fillPicker();
                }
            });
            tab.setPadding(0, dp(4), 0, dp(4));
            tabRow.addView(tab, weighted());
            tabs[i] = tab;
        }
        box.addView(tabRow);

        ScrollView scroll = new ScrollView(ctx);
        pickerGrid = new LinearLayout(ctx);
        pickerGrid.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(pickerGrid);
        box.addView(scroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(150)));
        return box;
    }

    private void fillPicker() {
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setBackground(rounded(i == pickerTab ? ELIXIR_COLOR : BTN, 6));
        }
        pickerGrid.removeAllViews();
        List<Card> cards = Card.withCost(pickerTab);
        LinearLayout row = null;
        for (int i = 0; i < cards.size(); i++) {
            if (i % 3 == 0) {
                row = hRow();
                pickerGrid.addView(row);
            }
            final Card card = cards.get(i);
            TextView b = button(card.name, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tracker.play(card, SystemClock.elapsedRealtime());
                    showPicker(false);
                    refreshAll();
                }
            });
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            b.setMaxLines(2);
            row.addView(b, weighted());
        }
        while (row != null && row.getChildCount() < 3) {
            row.addView(new View(ctx), weighted());
        }
    }

    private void onSlotTap(int index) {
        List<Card> deck = tracker.getDeck();
        if (index < deck.size()) {
            tracker.play(deck.get(index), SystemClock.elapsedRealtime());
            showPicker(false);
            refreshAll();
        } else {
            showPicker(true);
        }
    }

    private void showPicker(boolean visible) {
        if (visible) fillPicker();
        picker.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setCollapsed(boolean value) {
        collapsed = value;
        prefs.edit().putBoolean("collapsed", value).apply();
        applyCollapsed();
        refreshAll();
    }

    private void applyCollapsed() {
        pill.setVisibility(collapsed ? View.VISIBLE : View.GONE);
        panel.setVisibility(collapsed ? View.GONE : View.VISIBLE);
    }

    private void refreshLive() {
        double elixir = tracker.getElixir();
        String value = String.format(Locale.US, "%.1f", elixir);
        int mult = tracker.getMultiplier();
        pill.setText("💧 " + value + (mult > 1 ? "  x" + mult : ""));
        if (collapsed) return;
        elixirText.setText(value);
        elixirText.setTextColor(elixir >= Tracker.MAX_ELIXIR ? 0xFFFF5252 : ELIXIR_COLOR);
        elixirBar.setValue(elixir);
        clockText.setText(clockLabel() + "  x" + mult + (tracker.isAutoMultiplier() ? "" : "!"));

        CaptureService cs = CaptureService.getInstance();
        boolean capturing = cs != null && cs.isCapturing();
        String statusMsg = capturing ? "👁 Авто-детект ON" : "⚠ Захоплення вимкнено";
        if (!statusMsg.contentEquals(autoStatusText.getText())) {
            autoStatusText.setText(statusMsg);
            autoStatusText.setTextColor(capturing ? AUTO_BADGE : 0xFFFF5252);
        }
    }

    private String clockLabel() {
        long elapsed = tracker.getElapsedMs();
        boolean overtime = elapsed >= Tracker.REGULAR_MS;
        long remaining = overtime
            ? Math.max(0, Tracker.REGULAR_MS + 120_000 - elapsed)
            : Tracker.REGULAR_MS - elapsed;
        long sec = (remaining + 999) / 1000;
        return (overtime ? "OT " : "") + String.format(Locale.US, "%d:%02d", sec / 60, sec % 60);
    }

    private void refreshAll() {
        if (root == null) return;
        refreshLive();
        playPauseBtn.setText(tracker.isRunning() ? "⏸" : "▶");
        undoBtn.setEnabled(tracker.canUndo());
        undoBtn.setAlpha(tracker.canUndo() ? 1f : 0.4f);

        List<Card> deck = tracker.getDeck();
        for (int i = 0; i < slots.length; i++) {
            TextView slot = slots[i];
            if (i >= deck.size()) {
                slot.setText("?");
                slot.setBackground(rounded(UNKNOWN, 6));
                continue;
            }
            Card card = deck.get(i);
            int pos = tracker.queuePosition(card);
            String cost = card.isMirror() ? "M" : String.valueOf(card.cost);
            String badge = pos == 0 ? "✓" : String.valueOf(pos);
            slot.setText(card.shortName + "\n" + cost + "💧 " + badge);
            int color = pos == 0 ? HAND : (pos == 1 ? NEXT : QUEUE);
            slot.setBackground(rounded(color, 6));
        }

        Card champ = tracker.getChampion();
        abilityBtn.setVisibility(champ != null ? View.VISIBLE : View.INVISIBLE);
        if (champ != null) abilityBtn.setText("⚡" + champ.abilityCost);

        Card[] queue = tracker.getQueue();
        StringBuilder sb = new StringBuilder("Далі: ");
        for (int i = 0; i < queue.length; i++) {
            if (i > 0) sb.append(" → ");
            sb.append(queue[i] == null ? "?" : queue[i].shortName);
        }
        sb.append("   |  карт: ").append(deck.size()).append("/8");
        if (tracker.getLeaked() >= 0.1) {
            sb.append(String.format(Locale.US, "\nВтратив: %.1f💧", tracker.getLeaked()));
        }
        cycleText.setText(sb.toString());
        pumpBtn.setVisibility(tracker.hasActivePump() ? View.VISIBLE : View.GONE);
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v, ctx.getResources().getDisplayMetrics()));
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private TextView button(String label, View.OnClickListener onClick) {
        TextView b = text(label, 14, Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(8), dp(5), dp(8), dp(5));
        b.setBackground(rounded(BTN, 6));
        if (onClick != null) b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        return lp;
    }

    private LinearLayout hRow() {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private final class DragListener implements View.OnTouchListener {
        private final Runnable onTap;
        private final int slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        private float downX, downY;
        private int startX, startY;
        private boolean dragging;

        DragListener(Runnable onTap) { this.onTap = onTap; }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX(); downY = e.getRawY();
                    startX = params.x; startY = params.y;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (!dragging && Math.hypot(dx, dy) > slop) dragging = true;
                    if (dragging) {
                        params.x = Math.max(0, startX + Math.round(dx));
                        params.y = Math.max(0, startY + Math.round(dy));
                        wm.updateViewLayout(root, params);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        prefs.edit().putInt("x", params.x).putInt("y", params.y).apply();
                    } else if (onTap != null) {
                        onTap.run();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    private static final class ElixirBar extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint empty = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private double value;

        ElixirBar(Context ctx) {
            super(ctx);
            fill.setColor(ELIXIR_COLOR);
            empty.setColor(0xFF37253D);
        }

        void setValue(double v) {
            if (Math.abs(v - value) < 0.01) return;
            value = v;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float gap = getHeight() / 4f;
            float w = (getWidth() - gap * 9) / 10f;
            float r = getHeight() / 3f;
            for (int i = 0; i < 10; i++) {
                float left = i * (w + gap);
                rect.set(left, 0, left + w, getHeight());
                canvas.drawRoundRect(rect, r, r, empty);
                double part = Math.max(0, Math.min(1, value - i));
                if (part > 0) {
                    rect.set(left, 0, left + (float) (w * part), getHeight());
                    canvas.drawRoundRect(rect, r, r, fill);
                }
            }
        }
    }
}
