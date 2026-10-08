package com.runetpisun.careelixir;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analyzes screen captures to detect opponent card deployments.
 *
 * Detection works by monitoring the opponent's half of the arena for new unit appearances.
 * When a significant change is detected, extracts color signature from the deployment area
 * and matches against known card color profiles.
 *
 * Screen layout (1080x2400 Samsung A52, CR fills ~1080x1920 centered):
 * - Opponent's field: roughly y=280..860 (top 30% of arena)
 * - Deployment zone: where new units first appear
 */
public final class ScreenAnalyzer {

    public interface DetectionListener {
        void onCardDetected(Card card, float confidence);
        void onDeploymentDetected(int elixirCost);
    }

    private static final int HIST_BINS = 16;
    private static final float CHANGE_THRESHOLD = 0.12f;
    private static final float MATCH_THRESHOLD = 0.65f;
    private static final long COOLDOWN_MS = 1500;

    private final Map<String, float[]> cardProfiles = new HashMap<>();
    private int[] prevPixels;
    private int prevWidth;
    private int prevHeight;
    private long lastDetectionMs;
    private DetectionListener listener;
    private boolean active;

    private int screenWidth = 1080;
    private int screenHeight = 2400;

    public void setListener(DetectionListener listener) {
        this.listener = listener;
    }

    public void setScreenSize(int w, int h) {
        this.screenWidth = w;
        this.screenHeight = h;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            prevPixels = null;
        }
    }

    public boolean isActive() { return active; }

    public void buildCardProfiles() {
        cardProfiles.clear();
        for (Card card : Card.all()) {
            cardProfiles.put(card.id, generateColorProfile(card));
        }
    }

    public void analyzeFrame(Bitmap frame, long timestampMs) {
        if (!active || listener == null || frame == null) return;
        if (timestampMs - lastDetectionMs < COOLDOWN_MS) return;

        int roiTop = (int) (screenHeight * 0.15);
        int roiBottom = (int) (screenHeight * 0.42);
        int roiLeft = (int) (screenWidth * 0.05);
        int roiRight = (int) (screenWidth * 0.95);

        int roiW = roiRight - roiLeft;
        int roiH = roiBottom - roiTop;
        if (roiW <= 0 || roiH <= 0) return;

        int sampleStep = 4;
        int sampledW = roiW / sampleStep;
        int sampledH = roiH / sampleStep;
        int[] pixels = new int[sampledW * sampledH];

        for (int sy = 0; sy < sampledH; sy++) {
            for (int sx = 0; sx < sampledW; sx++) {
                int px = roiLeft + sx * sampleStep;
                int py = roiTop + sy * sampleStep;
                if (px < frame.getWidth() && py < frame.getHeight()) {
                    pixels[sy * sampledW + sx] = frame.getPixel(px, py);
                }
            }
        }

        if (prevPixels != null && prevWidth == sampledW && prevHeight == sampledH) {
            float change = computeChange(prevPixels, pixels);
            if (change > CHANGE_THRESHOLD) {
                int[] diffRegion = extractChangedRegion(prevPixels, pixels, sampledW, sampledH);
                if (diffRegion != null) {
                    Card matched = matchCard(diffRegion);
                    if (matched != null) {
                        lastDetectionMs = timestampMs;
                        listener.onCardDetected(matched, MATCH_THRESHOLD);
                    } else {
                        int estimatedCost = estimateCostFromChangeSize(change);
                        if (estimatedCost > 0) {
                            lastDetectionMs = timestampMs;
                            listener.onDeploymentDetected(estimatedCost);
                        }
                    }
                }
            }
        }

        prevPixels = pixels;
        prevWidth = sampledW;
        prevHeight = sampledH;
    }

    private float computeChange(int[] prev, int[] curr) {
        if (prev.length != curr.length) return 0;
        int changed = 0;
        for (int i = 0; i < prev.length; i++) {
            int dr = Math.abs(Color.red(prev[i]) - Color.red(curr[i]));
            int dg = Math.abs(Color.green(prev[i]) - Color.green(curr[i]));
            int db = Math.abs(Color.blue(prev[i]) - Color.blue(curr[i]));
            if (dr + dg + db > 60) changed++;
        }
        return (float) changed / prev.length;
    }

    private int[] extractChangedRegion(int[] prev, int[] curr, int w, int h) {
        int minX = w, maxX = 0, minY = h, maxY = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int dr = Math.abs(Color.red(prev[i]) - Color.red(curr[i]));
                int dg = Math.abs(Color.green(prev[i]) - Color.green(curr[i]));
                int db = Math.abs(Color.blue(prev[i]) - Color.blue(curr[i]));
                if (dr + dg + db > 60) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX <= minX || maxY <= minY) return null;

        int rw = maxX - minX + 1;
        int rh = maxY - minY + 1;
        if (rw < 5 || rh < 5) return null;

        int[] region = new int[rw * rh];
        for (int y = 0; y < rh; y++) {
            for (int x = 0; x < rw; x++) {
                region[y * rw + x] = curr[(minY + y) * w + (minX + x)];
            }
        }
        return region;
    }

    private Card matchCard(int[] regionPixels) {
        float[] regionHist = computeHistogram(regionPixels);
        Card best = null;
        float bestScore = 0;

        for (Map.Entry<String, float[]> entry : cardProfiles.entrySet()) {
            float score = histogramSimilarity(regionHist, entry.getValue());
            if (score > bestScore) {
                bestScore = score;
                best = Card.byId(entry.getKey());
            }
        }

        return bestScore >= MATCH_THRESHOLD ? best : null;
    }

    private float[] computeHistogram(int[] pixels) {
        float[] hist = new float[HIST_BINS * 3];
        for (int pixel : pixels) {
            int r = Color.red(pixel) * HIST_BINS / 256;
            int g = Color.green(pixel) * HIST_BINS / 256;
            int b = Color.blue(pixel) * HIST_BINS / 256;
            hist[Math.min(r, HIST_BINS - 1)]++;
            hist[HIST_BINS + Math.min(g, HIST_BINS - 1)]++;
            hist[2 * HIST_BINS + Math.min(b, HIST_BINS - 1)]++;
        }
        float sum = pixels.length;
        if (sum > 0) {
            for (int i = 0; i < hist.length; i++) hist[i] /= sum;
        }
        return hist;
    }

    private float histogramSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        float intersection = 0;
        for (int i = 0; i < a.length; i++) {
            intersection += Math.min(a[i], b[i]);
        }
        return intersection / 3.0f;
    }

    private int estimateCostFromChangeSize(float changeRatio) {
        if (changeRatio > 0.35f) return 5;
        if (changeRatio > 0.25f) return 4;
        if (changeRatio > 0.18f) return 3;
        if (changeRatio > 0.13f) return 2;
        return 0;
    }

    private float[] generateColorProfile(Card card) {
        String name = card.name.toLowerCase();
        float[] profile = new float[HIST_BINS * 3];

        int dominantR = 128, dominantG = 128, dominantB = 128;

        if (name.contains("skeleton") || name.contains("graveyard")) {
            dominantR = 230; dominantG = 230; dominantB = 210;
        } else if (name.contains("goblin") || name.contains("dart")) {
            dominantR = 80; dominantG = 180; dominantB = 60;
        } else if (name.contains("fire") || name.contains("fireball") || name.contains("furnace")) {
            dominantR = 240; dominantG = 140; dominantB = 40;
        } else if (name.contains("ice") || name.contains("freeze") || name.contains("snowball")) {
            dominantR = 140; dominantG = 200; dominantB = 240;
        } else if (name.contains("electro") || name.contains("zap") || name.contains("lightning") || name.contains("sparky")) {
            dominantR = 100; dominantG = 160; dominantB = 240;
        } else if (name.contains("dragon") || name.contains("baby")) {
            dominantR = 100; dominantG = 200; dominantB = 100;
        } else if (name.contains("giant") && !name.contains("goblin")) {
            dominantR = 200; dominantG = 160; dominantB = 100;
        } else if (name.contains("knight") || name.contains("prince")) {
            dominantR = 180; dominantG = 180; dominantB = 200;
        } else if (name.contains("hog")) {
            dominantR = 220; dominantG = 160; dominantB = 140;
        } else if (name.contains("witch")) {
            dominantR = 140; dominantG = 80; dominantB = 180;
        } else if (name.contains("pekka") || name.contains("mega knight")) {
            dominantR = 60; dominantG = 60; dominantB = 140;
        } else if (name.contains("balloon") || name.contains("lava")) {
            dominantR = 180; dominantG = 80; dominantB = 60;
        } else if (name.contains("golem")) {
            dominantR = 120; dominantG = 100; dominantB = 80;
        } else if (name.contains("barbarian") || name.contains("barb")) {
            dominantR = 220; dominantG = 190; dominantB = 130;
        } else if (name.contains("minion") || name.contains("bat")) {
            dominantR = 100; dominantG = 80; dominantB = 160;
        } else if (name.contains("miner")) {
            dominantR = 160; dominantG = 140; dominantB = 100;
        } else if (name.contains("log") || name.contains("tornado") || name.contains("earthquake")) {
            dominantR = 140; dominantG = 120; dominantB = 80;
        } else if (name.contains("rocket")) {
            dominantR = 200; dominantG = 60; dominantB = 60;
        } else if (name.contains("poison") || name.contains("clone")) {
            dominantR = 120; dominantG = 200; dominantB = 120;
        } else if (name.contains("rage")) {
            dominantR = 200; dominantG = 60; dominantB = 200;
        } else if (name.contains("royal")) {
            dominantR = 60; dominantG = 80; dominantB = 180;
        } else if (name.contains("cannon") || name.contains("mortar") || name.contains("tesla") || name.contains("tower") || name.contains("x-bow")) {
            dominantR = 160; dominantG = 160; dominantB = 160;
        } else if (name.contains("archer") || name.contains("musket")) {
            dominantR = 200; dominantG = 160; dominantB = 200;
        } else if (name.contains("wizard")) {
            dominantR = 200; dominantG = 100; dominantB = 50;
        } else if (name.contains("bowler")) {
            dominantR = 60; dominantG = 100; dominantB = 200;
        } else if (name.contains("phoenix")) {
            dominantR = 240; dominantG = 200; dominantB = 60;
        } else if (name.contains("monk")) {
            dominantR = 240; dominantG = 200; dominantB = 100;
        }

        int rBin = Math.min(dominantR * HIST_BINS / 256, HIST_BINS - 1);
        int gBin = Math.min(dominantG * HIST_BINS / 256, HIST_BINS - 1);
        int bBin = Math.min(dominantB * HIST_BINS / 256, HIST_BINS - 1);

        for (int i = 0; i < HIST_BINS; i++) {
            float distR = Math.abs(i - rBin) / (float) HIST_BINS;
            float distG = Math.abs(i - gBin) / (float) HIST_BINS;
            float distB = Math.abs(i - bBin) / (float) HIST_BINS;
            profile[i] = Math.max(0, 1.0f - distR * 3);
            profile[HIST_BINS + i] = Math.max(0, 1.0f - distG * 3);
            profile[2 * HIST_BINS + i] = Math.max(0, 1.0f - distB * 3);
        }

        float sum = 0;
        for (int ch = 0; ch < 3; ch++) {
            sum = 0;
            for (int i = 0; i < HIST_BINS; i++) sum += profile[ch * HIST_BINS + i];
            if (sum > 0) {
                for (int i = 0; i < HIST_BINS; i++) profile[ch * HIST_BINS + i] /= sum;
            }
        }

        return profile;
    }
}
