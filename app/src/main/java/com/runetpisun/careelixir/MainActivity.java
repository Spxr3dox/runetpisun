package com.runetpisun.careelixir;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private TextView statusText;
    private TextView captureStatusText;
    private TextView syncText;
    private Button captureBtn;
    private AppSettings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new AppSettings(this);

        int pad = dp(20);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, pad);
        box.setBackgroundColor(0xFF121212);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFE040FB);
        box.addView(title);

        statusText = new TextView(this);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        statusText.setPadding(0, dp(12), 0, 0);
        box.addView(statusText);

        captureStatusText = new TextView(this);
        captureStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        captureStatusText.setPadding(0, dp(4), 0, dp(12));
        box.addView(captureStatusText);

        TextView help = new TextView(this);
        help.setText(R.string.setup_help);
        help.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        help.setTextColor(0xFFB0BEC5);
        box.addView(help);

        box.addView(btn(R.string.btn_accessibility, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        }));

        captureBtn = btn(R.string.btn_capture, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CaptureService cs = CaptureService.getInstance();
                if (cs != null && cs.isCapturing()) {
                    cs.stopCapture();
                    updateCaptureStatus();
                } else {
                    startActivity(new Intent(MainActivity.this, CapturePermissionActivity.class));
                }
            }
        });
        box.addView(captureBtn);

        box.addView(btn(R.string.btn_launch_game, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent launch = getPackageManager()
                    .getLaunchIntentForPackage(OverlayService.GAME_PACKAGE);
                if (launch != null) startActivity(launch);
                else statusText.setText(R.string.game_missing);
            }
        }));

        box.addView(separator());
        box.addView(apiSection());

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF121212);
        scroll.addView(box);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean on = isServiceEnabled();
        statusText.setText(on ? R.string.status_on : R.string.status_off);
        statusText.setTextColor(on ? 0xFF4CAF50 : 0xFFFF5252);
        updateCaptureStatus();
    }

    private void updateCaptureStatus() {
        CaptureService cs = CaptureService.getInstance();
        boolean capturing = cs != null && cs.isCapturing();
        captureStatusText.setText(capturing ? R.string.capture_on : R.string.capture_off);
        captureStatusText.setTextColor(capturing ? 0xFF4CAF50 : 0xFFFF5252);
        captureBtn.setText(capturing ? R.string.btn_stop_capture : R.string.btn_capture);
    }

    private View apiSection() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        TextView label = new TextView(this);
        label.setText("Clash Royale API");
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextColor(Color.WHITE);
        label.setPadding(0, dp(8), 0, dp(8));
        box.addView(label);

        final EditText tag = new EditText(this);
        tag.setHint(R.string.api_tag_hint);
        tag.setSingleLine(true);
        tag.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        tag.setTextColor(Color.WHITE);
        tag.setHintTextColor(0xFF757575);
        tag.setText(settings.tag().isEmpty() ? "" : "#" + settings.tag());
        box.addView(tag);

        final EditText key = new EditText(this);
        key.setHint(R.string.api_key_hint);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setTextColor(Color.WHITE);
        key.setHintTextColor(0xFF757575);
        key.setText(settings.apiKey());
        box.addView(key);

        box.addView(btn(R.string.btn_sync, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settings.setApi(tag.getText().toString(), key.getText().toString());
                if (!settings.hasApi()) {
                    syncText.setText(R.string.api_missing);
                    return;
                }
                syncText.setText(R.string.syncing);
                ApiSync.syncAsync(MainActivity.this, true, new ApiSync.Callback() {
                    @Override
                    public void done(boolean ok, String message) {
                        syncText.setText(message);
                    }
                });
            }
        }));

        syncText = new TextView(this);
        syncText.setText(settings.syncStatus());
        syncText.setTextColor(0xFFB0BEC5);
        syncText.setPadding(0, dp(4), 0, dp(8));
        box.addView(syncText);

        return box;
    }

    private View separator() {
        View line = new View(this);
        line.setBackgroundColor(0xFF333333);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(16);
        lp.bottomMargin = dp(8);
        line.setLayoutParams(lp);
        return line;
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

    private Button btn(int label, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(0xFF37474F);
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
