package com.apextechlabs.wakeremote;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * The status card: a state dot, a title and a detail line.
 *
 * The dot carries the connection state and is the thing the user reads first, so it must
 * be honest. Not enrolled is a NOT-connected state and shows red; blue means enrolled and
 * ready. Previously it was a text glyph hardcoded to brand blue in every state, including
 * while the card said "Setup required".
 */
final class StatusCard extends LinearLayout {
    private final View dot;
    private final ProgressBar spinner;
    private final TextView title;
    private final TextView detail;

    StatusCard(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int pad = ApexUi.dimen(context, R.dimen.space_2);
        setPadding(pad, pad, pad, pad);
        setBackground(ApexUi.rounded(context, ApexUi.surfaceSunken(context), 16, ApexUi.border(context), 1));

        int dotSize = ApexUi.dimen(context, R.dimen.status_dot);
        dot = new View(context);
        dot.setBackground(ApexUi.dot(context, ApexUi.red(context)));
        LayoutParams dotParams = new LayoutParams(dotSize, dotSize);
        dotParams.rightMargin = ApexUi.dimen(context, R.dimen.space_2);
        addView(dot, dotParams);

        spinner = new ProgressBar(context);
        spinner.setIndeterminate(true);
        spinner.setVisibility(GONE);
        LayoutParams spinnerParams = new LayoutParams(dotSize * 2, dotSize * 2);
        spinnerParams.rightMargin = ApexUi.dimen(context, R.dimen.space_2);
        addView(spinner, spinnerParams);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(VERTICAL);
        addView(column, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        title = ApexUi.text(context, "", 16, ApexUi.white(context), Typeface.BOLD);
        column.addView(title);

        detail = ApexUi.text(context, "", 13, ApexUi.muted(context), Typeface.NORMAL);
        detail.setLineSpacing(0, 1.15f);
        LayoutParams detailParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = ApexUi.dp(context, 2);
        column.addView(detail, detailParams);
    }

    /** @param connected true for enrolled/ready/success (blue), false for not-connected (red). */
    void set(CharSequence titleText, CharSequence detailText, boolean connected) {
        busy(false);
        title.setText(titleText);
        detail.setText(detailText);
        int accent = connected ? ApexUi.blue(getContext()) : ApexUi.red(getContext());
        dot.setBackground(ApexUi.dot(getContext(), accent));
        setBackground(ApexUi.rounded(getContext(), ApexUi.surfaceSunken(getContext()), 16, accent, 1));
    }

    void busy(boolean value) {
        dot.setVisibility(value ? GONE : VISIBLE);
        spinner.setVisibility(value ? VISIBLE : GONE);
    }

    void working(CharSequence titleText, CharSequence detailText) {
        title.setText(titleText);
        detail.setText(detailText);
        setBackground(ApexUi.rounded(getContext(), ApexUi.surfaceSunken(getContext()), 16, ApexUi.blue(getContext()), 1));
        busy(true);
    }
}
