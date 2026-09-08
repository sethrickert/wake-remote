package com.apextechlabs.wakeremote;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The shared UI kit. Previously this was eight colour constants and four primitives, so
 * every header, footer, card and button was hand-rolled per screen and drifted. Screens
 * now compose these, which is what keeps controls in the same position everywhere.
 */
final class ApexUi {
    private ApexUi() {}

    // --- Palette. Resolved from colors.xml so the values live in exactly one place. ---
    static int background(Context c) { return color(c, R.color.apex_background); }
    static int surfaceSunken(Context c) { return color(c, R.color.apex_surface_sunken); }
    static int surface(Context c) { return color(c, R.color.apex_surface); }
    static int border(Context c) { return color(c, R.color.apex_border); }
    static int muted(Context c) { return color(c, R.color.apex_muted); }
    static int white(Context c) { return color(c, R.color.apex_white); }
    static int blue(Context c) { return color(c, R.color.apex_blue); }
    static int red(Context c) { return color(c, R.color.apex_red); }
    static int black(Context c) { return color(c, R.color.apex_black); }

    private static int color(Context context, int id) {
        return context.getResources().getColor(id, context.getTheme());
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static int dimen(Context context, int id) {
        return context.getResources().getDimensionPixelSize(id);
    }

    // --- Primitives ---

    static TextView text(Context context, CharSequence value, float sizeSp, int color, int style) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans", style));
        return view;
    }

    static GradientDrawable rounded(Context context, int fill, float radiusDp, int stroke, float strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(context, strokeDp), stroke);
        return drawable;
    }

    static GradientDrawable dot(Context context, int fill) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        return drawable;
    }

    static ColorStateList buttonTextColors(Context context) {
        return new ColorStateList(
            new int[][] { new int[] {-android.R.attr.state_enabled}, new int[] {} },
            new int[] { muted(context), black(context) }
        );
    }

    static void setEnabledAppearance(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.48f);
    }

    // --- Components ---

    /**
     * The persistent header: fixed height, with stable nav-left / title-centre /
     * action-right slots. Both controls are icon-only with 48dp touch targets, and an
     * unused slot keeps its space so the title never shifts between screens.
     */
    static LinearLayout header(Activity activity, boolean showBack, Runnable onBack,
                               boolean showAction, int actionIcon, int actionLabel, Runnable onAction) {
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        bar.addView(slot(activity, showBack, R.drawable.ic_arrow_back, R.string.back, onBack));

        TextView title = text(activity, activity.getString(R.string.app_name), 15, blue(activity), Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(.04f);
        LinearLayout.LayoutParams titleParams =
            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(title, titleParams);

        bar.addView(slot(activity, showAction, actionIcon, actionLabel, onAction));
        return bar;
    }

    /** A fixed-size header slot. Invisible rather than absent, so the title stays centred. */
    private static View slot(Activity activity, boolean visible, int icon, int label, Runnable onClick) {
        int size = dimen(activity, R.dimen.touch_target);
        ImageButton button = new ImageButton(activity);
        button.setImageResource(icon);
        button.setBackground(null);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setContentDescription(activity.getString(label));
        button.setColorFilter(white(activity));
        if (onClick != null) button.setOnClickListener(v -> onClick.run());
        if (!visible) {
            button.setVisibility(View.INVISIBLE);
            button.setClickable(false);
        }
        button.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return button;
    }

    /** The footer: the apex-shield mark followed by "Apex Tech Labs". Nothing else. */
    static LinearLayout footer(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        ImageView shield = new ImageView(context);
        shield.setImageResource(R.drawable.apex_shield);
        shield.setAlpha(.75f);
        shield.setContentDescription(null);
        int size = dp(context, 16);
        LinearLayout.LayoutParams shieldParams = new LinearLayout.LayoutParams(size, size);
        shieldParams.rightMargin = dp(context, 8);
        row.addView(shield, shieldParams);

        TextView label = text(context, context.getString(R.string.attribution), 11, muted(context), Typeface.NORMAL);
        label.setLetterSpacing(.06f);
        row.addView(label);
        return row;
    }

    /**
     * The primary action. Icon and label are a single centred group; the previous version
     * used a compound drawable, which pins the icon to the far-left edge of a full-width
     * button while the text stays centred, leaving them visually divorced.
     */
    static LinearLayout primaryButton(Context context, CharSequence label, Integer icon, Runnable onClick) {
        LinearLayout button = new LinearLayout(context);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(context, blue(context), 14, blue(context), 0));
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(label);
        if (onClick != null) button.setOnClickListener(v -> onClick.run());

        if (icon != null) {
            ImageView view = new ImageView(context);
            view.setImageResource(icon);
            view.setColorFilter(black(context));
            int size = dp(context, 20);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(size, size);
            iconParams.rightMargin = dp(context, 10);
            button.addView(view, iconParams);
        }

        TextView text = text(context, label, 15, black(context), Typeface.BOLD);
        text.setLetterSpacing(.04f);
        button.addView(text);
        return button;
    }

    static Button secondaryButton(Context context, CharSequence label, Runnable onClick) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setTextColor(blue(context));
        button.setBackground(rounded(context, surface(context), 12, border(context), 1));
        button.setStateListAnimator(null);
        if (onClick != null) button.setOnClickListener(v -> onClick.run());
        return button;
    }

    /** A flat text link, for the secondary path away from the primary action. */
    static Button linkButton(Context context, CharSequence label, Runnable onClick) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setTextColor(blue(context));
        button.setBackground(null);
        button.setStateListAnimator(null);
        if (onClick != null) button.setOnClickListener(v -> onClick.run());
        return button;
    }

    static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dimen(context, R.dimen.space_2);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(rounded(context, surfaceSunken(context), 16, border(context), 1));
        return card;
    }

    /** Wraps content in a centred, width-capped column so nothing sits flush to an edge. */
    static FrameLayout contentFrame(Context context, View child) {
        FrameLayout frame = new FrameLayout(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
        child.setLayoutParams(params);
        frame.addView(child);
        return frame;
    }
}
