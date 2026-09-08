package com.apextechlabs.wakeremote;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private WakeKeyStore keyStore;
    private EnrollmentStore enrollmentStore;
    private ExecutorService executor;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private StatusCard status;
    private LinearLayout actionButton;
    private TextView actionLabel;
    private ImageView actionIcon;
    private Spinner targetSpinner;
    private LinearLayout column;

    private int cooldownSeconds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(ApexUi.black(this));
        getWindow().setNavigationBarColor(ApexUi.black(this));
        keyStore = new WakeKeyStore();
        enrollmentStore = new EnrollmentStore(this);
        executor = Executors.newSingleThreadExecutor();
        setContentView(buildScreen());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rebuilt on every resume, so returning from enrollment reflects the new state.
        // The previous version only built in onCreate, which is why the target picker
        // never appeared until the process restarted.
        // No auto-jump to enrollment. The home screen IS the pre-enrollment state now:
        // red status dot, "Setup required", and a "Set up secure key" primary action.
        // Launching straight into enrollment meant that screen was never seen.
        setContentView(buildScreen());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdownNow();
    }

    /**
     * Enrolled means a key AND the metadata to use it. A key with no stored service URL
     * would otherwise report "Ready" and then POST to an empty host.
     */
    private boolean enrolled() {
        try {
            return keyStore.hasKey() && enrollmentStore.isEnrolled();
        } catch (Exception exception) {
            return false;
        }
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ApexUi.background(this));

        int gutter = ApexUi.dimen(this, R.dimen.gutter);
        int space2 = ApexUi.dimen(this, R.dimen.space_2);
        int space3 = ApexUi.dimen(this, R.dimen.space_3);

        LinearLayout header = ApexUi.header(this, false, null, true,
            R.drawable.ic_settings, R.string.settings,
            () -> startActivity(new Intent(this, EnrollmentActivity.class)));
        header.setPadding(space2, 0, space2, 0);
        root.addView(header, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ApexUi.dimen(this, R.dimen.header_height)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Centred, width-capped column with consistent gutters.
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(body, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(gutter, space3, gutter, space3);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        body.addView(column, columnParams);

        int logo = ApexUi.dimen(this, R.dimen.logo_size);
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.wake_remote_logo);
        mark.setContentDescription(getString(R.string.app_logo));
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        column.addView(mark, new LinearLayout.LayoutParams(logo, logo));

        TextView eyebrow = ApexUi.text(this, getString(R.string.home_eyebrow), 11, ApexUi.blue(this), Typeface.BOLD);
        eyebrow.setLetterSpacing(.16f);
        eyebrow.setGravity(Gravity.CENTER);
        addSpaced(eyebrow, space3);

        TextView title = ApexUi.text(this, getString(R.string.home_title), 32, ApexUi.white(this), Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        addSpaced(title, ApexUi.dimen(this, R.dimen.space_1));

        TextView subtitle = ApexUi.text(this, getString(R.string.home_subtitle), 14, ApexUi.muted(this), Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(0, 1.2f);
        addSpaced(subtitle, ApexUi.dimen(this, R.dimen.space_1));

        status = new StatusCard(this);
        addSpaced(status, space3);

        List<EnrollmentStore.Target> targets = enrollmentStore.targets();
        if (targets.size() > 1) {
            targetSpinner = new Spinner(this);
            ArrayAdapter<EnrollmentStore.Target> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, targets);
            targetSpinner.setAdapter(adapter);
            for (int i = 0; i < targets.size(); i++) {
                if (targets.get(i).alias.equals(enrollmentStore.selected())) targetSpinner.setSelection(i);
            }
            targetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    enrollmentStore.select(targets.get(position).alias);
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
            addSpaced(targetSpinner, space2);
        }

        // Pre-enrollment the action is "Set up secure key" and carries NO wake icon:
        // showing a power symbol before a key exists implies an action that cannot work.
        boolean ready = enrolled();
        actionButton = ApexUi.primaryButton(this,
            getString(ready ? R.string.wake_computer : R.string.setup_secure_key),
            ready ? R.drawable.ic_power : null,
            ready ? this::beginWake : () -> startActivity(new Intent(this, EnrollmentActivity.class)));
        actionLabel = findLabel(actionButton);
        actionIcon = findIcon(actionButton);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ApexUi.dimen(this, R.dimen.button_height));
        actionParams.topMargin = space2;
        column.addView(actionButton, actionParams);

        LinearLayout footer = ApexUi.footer(this);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        footerParams.bottomMargin = space2;
        footerParams.topMargin = space2;
        root.addView(footer, footerParams);

        showReadyState();
        return root;
    }

    private void addSpaced(View view, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        column.addView(view, params);
    }

    private static TextView findLabel(LinearLayout button) {
        for (int i = 0; i < button.getChildCount(); i++) {
            if (button.getChildAt(i) instanceof TextView) return (TextView) button.getChildAt(i);
        }
        return null;
    }

    private static ImageView findIcon(LinearLayout button) {
        for (int i = 0; i < button.getChildCount(); i++) {
            if (button.getChildAt(i) instanceof ImageView) return (ImageView) button.getChildAt(i);
        }
        return null;
    }

    private void showReadyState() {
        if (enrolled()) {
            String target = enrollmentStore.selectedLabel();
            status.set(getString(R.string.status_ready),
                target == null ? getString(R.string.status_ready_detail) : getString(R.string.target_label, target),
                true);
        } else {
            // Not enrolled is a NOT-connected state, so the dot is red.
            status.set(getString(R.string.status_setup_required), getString(R.string.status_setup_detail), false);
        }
    }

    private void beginWake() {
        if (!enrolled()) {
            status.set(getString(R.string.status_setup_required), getString(R.string.status_setup_detail), false);
            return;
        }
        setBusy(true);
        status.working(getString(R.string.status_signing), getString(R.string.status_signing_detail));

        String serverUrl = enrollmentStore.url();
        String keyId = enrollmentStore.keyId();
        String target = enrollmentStore.selected();

        executor.execute(() -> {
            WakeApiClient.Result result;
            try {
                result = new WakeApiClient().send(keyStore.getKey(), serverUrl, keyId, target);
            } catch (Exception exception) {
                result = WakeApiClient.Result.error(getString(R.string.status_failed));
            }
            WakeApiClient.Result delivered = result;
            handler.post(() -> onWakeResult(delivered));
        });
    }

    private void onWakeResult(WakeApiClient.Result result) {
        setBusy(false);
        switch (result.kind) {
            case SUCCESS:
                status.set(getString(R.string.status_sent), result.message, true);
                break;
            case RATE_LIMITED:
                status.set(getString(R.string.status_waiting), result.message, false);
                startCooldown(result.retryAfterSeconds);
                break;
            case NETWORK:
                status.set(getString(R.string.status_offline), result.message, false);
                break;
            default:
                status.set(getString(R.string.status_failed), result.message, false);
                break;
        }
    }

    private void setBusy(boolean busy) {
        if (actionButton != null) ApexUi.setEnabledAppearance(actionButton, !busy);
        if (actionLabel != null) {
            actionLabel.setText(busy ? getString(R.string.sending) : getString(R.string.wake_computer));
        }
        if (actionIcon != null) actionIcon.setVisibility(busy ? View.GONE : View.VISIBLE);
    }

    private void startCooldown(int seconds) {
        cooldownSeconds = Math.max(1, seconds);
        updateCooldown();
    }

    private void updateCooldown() {
        if (actionButton == null || actionLabel == null) return;
        if (cooldownSeconds <= 0) {
            ApexUi.setEnabledAppearance(actionButton, true);
            actionLabel.setText(getString(R.string.wake_computer));
            if (actionIcon != null) actionIcon.setVisibility(View.VISIBLE);
            return;
        }
        ApexUi.setEnabledAppearance(actionButton, false);
        actionLabel.setText(getString(R.string.try_again_in, cooldownSeconds));
        if (actionIcon != null) actionIcon.setVisibility(View.GONE);
        cooldownSeconds--;
        handler.postDelayed(this::updateCooldown, 1000);
    }
}
