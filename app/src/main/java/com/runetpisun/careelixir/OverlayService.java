package com.runetpisun.careelixir;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class OverlayService extends AccessibilityService {
    static final String GAME_PACKAGE = "com.supercell.clashroyale";

    private static final Set<String> TRANSPARENT = new HashSet<>(Arrays.asList(
        "android",
        "com.android.systemui",
        "com.samsung.android.game.gametools",
        "com.samsung.android.game.gos",
        "com.samsung.android.app.cocktailbarservice",
        "com.samsung.android.honeyboard",
        "com.google.android.inputmethod.latin"));

    private OverlayController overlay;
    private final Set<String> ignored = new HashSet<>();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        overlay = new OverlayController(this);
        ElixirBridge.setOverlayController(overlay);
        ignored.clear();
        ignored.addAll(TRANSPARENT);
        ignored.add(getPackageName());
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            for (InputMethodInfo ime : imm.getEnabledInputMethodList())
                ignored.add(ime.getPackageName());
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (overlay == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        String name = pkg.toString();
        if (GAME_PACKAGE.equals(name)) {
            overlay.show();
            ElixirBridge.getAnalyzer().setActive(true);
        } else if (!ignored.contains(name)) {
            overlay.hide();
            ElixirBridge.getAnalyzer().setActive(false);
        }
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        if (overlay != null) overlay.hide();
        ElixirBridge.setOverlayController(null);
        overlay = null;
        super.onDestroy();
    }
}
