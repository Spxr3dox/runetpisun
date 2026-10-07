package com.runetpisun.croverlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Setup screen: explains how to enable the accessibility service and opens the right settings. */
public final class MainActivity extends Activity {
    private TextView status;
    private TextView myDeckText;
    private AppSettings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new AppSettings(this);
        int pad = dp(20);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title);

        status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        status.setPadding(0, dp(12), 0, dp(12));
        box.addView(status);

        TextView help = new TextView(this);
        help.setText(R.string.setup_help);
        help.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        box.addView(help);

        box.addView(button(R.string.btn_restricted, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null)));
            }
        }));
        box.addView(button(R.string.btn_accessibility, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        }));
        box.addView(button(R.string.btn_launch_game, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent launch = getPackageManager().getLaunchIntentForPackage(OverlayService.GAME_PACKAGE);
                if (launch != null) startActivity(launch);
                else status.setText(R.string.game_missing);
            }
        }));

        TextView deckTitle = new TextView(this);
        deckTitle.setText(R.string.my_deck_title);
        deckTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        deckTitle.setTypeface(Typeface.DEFAULT_BOLD);
        deckTitle.setPadding(0, dp(20), 0, dp(4));
        box.addView(deckTitle);
        myDeckText = new TextView(this);
        box.addView(myDeckText);
        box.addView(button(R.string.btn_pick_deck, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickDeck();
            }
        }));
        box.addView(levelRow(true));
        box.addView(levelRow(false));
        refreshDeck();

        TextView usage = new TextView(this);
        usage.setText(R.string.usage_help);
        usage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        usage.setPadding(0, dp(16), 0, 0);
        box.addView(usage);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (myDeckText != null) refreshDeck();
        boolean on = isServiceEnabled();
        status.setText(on ? R.string.status_on : R.string.status_off);
        status.setTextColor(on ? 0xFF2E7D32 : Color.RED);
    }

    private void refreshDeck() {
        List<Card> deck = settings.myDeck();
        StringBuilder sb = new StringBuilder();
        for (Card c : deck) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(c.name);
        }
        myDeckText.setText(deck.isEmpty() ? getString(R.string.my_deck_empty) : deck.size() + "/8: " + sb);
    }

    private void pickDeck() {
        final List<Card> all = Card.all();
        final String[] names = new String[all.size()];
        final boolean[] checked = new boolean[all.size()];
        List<Card> current = settings.myDeck();
        for (int i = 0; i < all.size(); i++) {
            Card c = all.get(i);
            names[i] = c.name + (c.isMirror() ? "" : "  (" + c.cost + ")");
            checked[i] = current.contains(c);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.btn_pick_deck)
                .setMultiChoiceItems(names, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        int count = 0;
                        for (boolean b : checked) if (b) count++;
                        if (isChecked && count > Tracker.DECK_SIZE) {
                            checked[which] = false;
                            ((AlertDialog) dialog).getListView().setItemChecked(which, false);
                            Toast.makeText(MainActivity.this, R.string.deck_full, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        List<Card> deck = new ArrayList<>();
                        for (int i = 0; i < all.size(); i++) if (checked[i]) deck.add(all.get(i));
                        settings.setMyDeck(deck);
                        refreshDeck();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private View levelRow(final boolean mine) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        final TextView label = new TextView(this);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        final Runnable update = new Runnable() {
            @Override
            public void run() {
                label.setText(getString(mine ? R.string.my_level : R.string.opp_level,
                        mine ? settings.myLevel() : settings.oppLevel()));
            }
        };
        update.run();
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        for (final int delta : new int[] {-1, 1}) {
            Button b = new Button(this);
            b.setText(delta < 0 ? "−" : "+");
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mine) settings.setMyLevel(settings.myLevel() + delta);
                    else settings.setOppLevel(settings.oppLevel() + delta);
                    update.run();
                }
            });
            row.addView(b, new LinearLayout.LayoutParams(dp(56), LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        return row;
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName me = new ComponentName(this, OverlayService.class);
        for (String s : enabled.split(":")) {
            ComponentName c = ComponentName.unflattenFromString(s);
            if (me.equals(c)) return true;
        }
        return false;
    }

    private Button button(int label, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
