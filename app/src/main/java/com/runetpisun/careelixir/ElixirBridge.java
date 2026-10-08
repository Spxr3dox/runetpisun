package com.runetpisun.careelixir;

/**
 * Shared singleton state between CaptureService and OverlayService.
 * Both services run in the same process so static fields work.
 */
public final class ElixirBridge {
    private static final Tracker tracker = new Tracker();
    private static final ScreenAnalyzer analyzer = new ScreenAnalyzer();
    private static OverlayController overlayController;

    public static Tracker getTracker() { return tracker; }

    public static ScreenAnalyzer getAnalyzer() { return analyzer; }

    public static void setOverlayController(OverlayController c) { overlayController = c; }

    public static OverlayController getOverlayController() { return overlayController; }
}
