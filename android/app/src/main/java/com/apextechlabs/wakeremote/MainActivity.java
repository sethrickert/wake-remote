package com.apextechlabs.wakeremote;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final WakeKeyStore keyStore = new WakeKeyStore();
    private EnrollmentStore enrollment;
    private ExecutorService executor;
    private Button wakeButton;
    private TextView statusTitle;
    private TextView statusDetail;
    private LinearLayout statusCard;
    private ProgressBar progress;
    private boolean busy;
    private long cooldownEndsAt;
    private boolean promptedForEnrollment;
    private final Runnable cooldownTick = this::updateCooldown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(ApexUi.BLACK);
        getWindow().setNavigationBarColor(ApexUi.BLACK);
        executor = Executors.newSingleThreadExecutor();
        enrollment = new EnrollmentStore(this);
        setContentView(buildScreen());
        showReadyState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (wakeButton == null) return;
        boolean enrolled = hasKey();
        if (!enrolled && !promptedForEnrollment) {
            promptedForEnrollment = true;
            startActivity(new Intent(this, EnrollmentActivity.class));
        }
        if (!busy && cooldownEndsAt == 0) showReadyState();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(cooldownTick);
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildScreen() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        int heightDp = getResources().getConfiguration().screenHeightDp;
        boolean horizontal = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
            || widthDp >= 600;
        boolean compact = heightDp < 560;
        boolean veryCompact = heightDp < 400;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ApexUi.BACKGROUND);
        root.setPadding(dp(horizontal ? 22 : 20), dp(compact ? 8 : 14), dp(horizontal ? 22 : 20), dp(compact ? 8 : 16));
        root.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(compact ? 38 : 44)));

        if (horizontal) {
            LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.HORIZONTAL);
            body.setGravity(Gravity.CENTER);
            root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

            FrameLayout hero = buildHero();
            LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(0, -1, widthDp >= 720 ? .95f : .72f);
            heroParams.rightMargin = dp(18);
            body.addView(hero, heroParams);

            LinearLayout controls = buildControls(true, compact, veryCompact);
            body.addView(controls, new LinearLayout.LayoutParams(0, -2, 1f));
        } else {
            FrameLayout hero = buildHero();
            LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, 0, 1f);
            heroParams.topMargin = dp(compact ? 2 : 8);
            heroParams.bottomMargin = dp(compact ? 2 : 8);
            root.addView(hero, heroParams);
            root.addView(buildControls(false, compact, veryCompact), new LinearLayout.LayoutParams(-1, -2));
        }
        return root;
    }

    private LinearLayout buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView brand = ApexUi.text(this, "APEX  /  WAKE REMOTE", 11, ApexUi.APEX_BLUE, Typeface.BOLD);
        brand.setLetterSpacing(.14f);
        bar.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));

        Button setup = new Button(this);
        setup.setText(R.string.setup_button);
        setup.setContentDescription(getString(R.string.open_secure_setup));
        setup.setTextSize(11);
        setup.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        setup.setTextColor(ApexUi.APEX_BLUE);
        setup.setBackground(ApexUi.rounded(this, ApexUi.CARD, 11, ApexUi.BORDER, 1));
        setup.setStateListAnimator(null);
        setup.setPadding(dp(12), 0, dp(12), 0);
        setup.setOnClickListener(view -> startActivity(new Intent(this, EnrollmentActivity.class)));
        bar.addView(setup, new LinearLayout.LayoutParams(-2, dp(36)));
        return bar;
    }

    private FrameLayout buildHero() {
        FrameLayout hero = new FrameLayout(this);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.apex_wol_logo);
        logo.setContentDescription(getString(R.string.apex_logo_description));
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setAdjustViewBounds(true);
        logo.setMaxWidth(dp(128));
        logo.setMaxHeight(dp(128));
        FrameLayout.LayoutParams logoParams = new FrameLayout.LayoutParams(dp(128), dp(128), Gravity.CENTER);
        logoParams.setMargins(dp(8), dp(4), dp(8), dp(4));
        hero.addView(logo, logoParams);
        return hero;
    }

    private LinearLayout buildControls(boolean horizontal, boolean compact, boolean veryCompact) {
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(horizontal ? Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL);
        if (horizontal) controls.setPadding(0, dp(4), 0, dp(4));

        TextView eyebrow = ApexUi.text(this, "REMOTE POWER", 10, ApexUi.APEX_BLUE, Typeface.BOLD);
        eyebrow.setLetterSpacing(.18f);
        controls.addView(eyebrow, new LinearLayout.LayoutParams(-2, -2));

        TextView title = ApexUi.text(this, "Wake your PC", veryCompact ? 24 : (compact ? 26 : 31), ApexUi.WHITE, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-2, -2);
        titleParams.topMargin = dp(compact ? 2 : 4);
        controls.addView(title, titleParams);

        TextView subtitle = ApexUi.text(this,
            veryCompact ? "Secure wake from anywhere." : "Securely send a wake signal from anywhere.",
            veryCompact ? 11 : (compact ? 12 : 14), ApexUi.MUTED, Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(3);
        controls.addView(subtitle, subtitleParams);

        statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.setPadding(dp(14), dp(compact ? 10 : 12), dp(14), dp(compact ? 10 : 12));
        statusCard.setBackground(ApexUi.rounded(this, ApexUi.CARD, 17, ApexUi.BORDER, 1));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(veryCompact ? 6 : (compact ? 10 : 14));
        controls.addView(statusCard, cardParams);

        progress = new ProgressBar(this);
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ApexUi.APEX_BLUE));
        progress.setVisibility(View.GONE);
        statusCard.addView(progress, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView statusGlyph = ApexUi.text(this, "●", 17, ApexUi.APEX_BLUE, Typeface.BOLD);
        statusGlyph.setGravity(Gravity.CENTER);
        statusGlyph.setTag("glyph");
        statusCard.addView(statusGlyph, new LinearLayout.LayoutParams(dp(24), dp(24)));

        LinearLayout statusText = new LinearLayout(this);
        statusText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams statusTextParams = new LinearLayout.LayoutParams(0, -2, 1f);
        statusTextParams.leftMargin = dp(11);
        statusCard.addView(statusText, statusTextParams);

        statusTitle = ApexUi.text(this, "Ready", compact ? 13 : 14, ApexUi.WHITE, Typeface.BOLD);
        statusText.addView(statusTitle);
        statusDetail = ApexUi.text(this, "Secure service available", compact ? 11 : 12, ApexUi.MUTED, Typeface.NORMAL);
        statusText.addView(statusDetail);

        java.util.List<EnrollmentStore.Target> availableTargets = enrollment.targets();
        if (availableTargets.size() > 1) {
            Spinner targetPicker = new Spinner(this);
            ArrayAdapter<EnrollmentStore.Target> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, availableTargets);
            targetPicker.setAdapter(adapter);
            for (int i = 0; i < availableTargets.size(); i++) if (availableTargets.get(i).alias.equals(enrollment.selected())) targetPicker.setSelection(i);
            targetPicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() { public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { enrollment.select(availableTargets.get(pos).alias); } public void onNothingSelected(android.widget.AdapterView<?> p) {} });
            LinearLayout.LayoutParams pickerParams = new LinearLayout.LayoutParams(-1, dp(48)); pickerParams.topMargin = dp(8); controls.addView(targetPicker, pickerParams);
        }

        wakeButton = new Button(this);
        wakeButton.setText(R.string.wake_computer);
        wakeButton.setContentDescription(getString(R.string.wake_computer_description));
        wakeButton.setTextSize(14);
        wakeButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        wakeButton.setLetterSpacing(.09f);
        wakeButton.setTextColor(ApexUi.buttonTextColors());
        wakeButton.setBackground(ApexUi.rounded(this, ApexUi.APEX_BLUE, 16, ApexUi.APEX_BLUE, 0));
        wakeButton.setStateListAnimator(null);
        wakeButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_power, 0, 0, 0);
        wakeButton.setCompoundDrawablePadding(dp(10));
        wakeButton.setOnClickListener(view -> beginWake());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(veryCompact ? 48 : (compact ? 52 : 58)));
        buttonParams.topMargin = dp(veryCompact ? 6 : (compact ? 9 : 12));
        controls.addView(wakeButton, buttonParams);

        TextView footer = ApexUi.text(this, "Apex Tech Labs", veryCompact ? 9 : 10, ApexUi.MUTED, Typeface.NORMAL);
        footer.setLetterSpacing(.08f);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = dp(veryCompact ? 3 : (compact ? 6 : 9));
        controls.addView(footer, footerParams);
        return controls;
    }

    private void beginWake() {
        if (busy || cooldownEndsAt > System.currentTimeMillis()) return;
        if (!hasKey()) {
            showStatus("Setup required", "Install the secure key before sending a wake request.", false);
            startActivity(new Intent(this, EnrollmentActivity.class));
            return;
        }
        setBusy(true);
        executor.execute(() -> {
            WakeApiClient.Result result;
            try {
                result = new WakeApiClient().send(keyStore.getKey(), enrollment.url(), enrollment.keyId(), enrollment.selected());
            } catch (Exception exception) {
                result = WakeApiClient.Result.error("The secure key could not be opened. Install it again in Setup.");
            }
            WakeApiClient.Result finalResult = result;
            runOnUiThread(() -> handleResult(finalResult));
        });
    }

    private void handleResult(WakeApiClient.Result result) {
        if (isFinishing() || isDestroyed()) return;
        setBusy(false);
        if (result.kind == WakeApiClient.Result.Kind.SUCCESS) {
            showStatus("Wake signal sent", result.message, true);
        } else if (result.kind == WakeApiClient.Result.Kind.RATE_LIMITED) {
            showStatus("Please wait", result.message, false);
            cooldownEndsAt = System.currentTimeMillis() + result.retryAfterSeconds * 1000L;
            updateCooldown();
        } else if (result.kind == WakeApiClient.Result.Kind.NETWORK) {
            showStatus("Connection unavailable", result.message, false);
        } else {
            showStatus("Request not sent", result.message, false);
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        ApexUi.setEnabledAppearance(wakeButton, !value);
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        View glyph = statusCard.findViewWithTag("glyph");
        if (glyph != null) glyph.setVisibility(value ? View.GONE : View.VISIBLE);
        if (value) {
            wakeButton.setText(R.string.sending);
            showStatus("Signing request", "Creating a fresh, secure wake command…", true);
        } else {
            wakeButton.setText(R.string.wake_computer);
        }
    }

    private void showReadyState() {
        if (hasKey()) {
            ApexUi.setEnabledAppearance(wakeButton, true);
            wakeButton.setText(R.string.wake_computer);
            showStatus("Ready", "Secure key installed  •  " + enrollment.selected(), true);
        } else {
            ApexUi.setEnabledAppearance(wakeButton, true);
            wakeButton.setText(R.string.setup_secure_key);
            showStatus("Setup required", "Install the server key once to continue.", false);
        }
    }

    private void showStatus(String title, String detail, boolean accent) {
        statusTitle.setText(title);
        statusDetail.setText(detail);
        statusCard.setBackground(ApexUi.rounded(this, ApexUi.CARD, 17, accent ? ApexUi.APEX_BLUE : ApexUi.BORDER, 1));
    }

    private void updateCooldown() {
        handler.removeCallbacks(cooldownTick);
        long remainingMillis = cooldownEndsAt - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            cooldownEndsAt = 0;
            showReadyState();
            return;
        }
        int seconds = (int) Math.ceil(remainingMillis / 1000.0);
        ApexUi.setEnabledAppearance(wakeButton, false);
        wakeButton.setText(getString(R.string.try_again_in, seconds));
        handler.postDelayed(cooldownTick, Math.min(1000, remainingMillis));
    }

    private boolean hasKey() {
        try {
            return keyStore.hasKey();
        } catch (Exception exception) {
            return false;
        }
    }

    private int dp(float value) {
        return ApexUi.dp(this, value);
    }
}
