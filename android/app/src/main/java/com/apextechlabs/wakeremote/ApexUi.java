package com.apextechlabs.wakeremote;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

final class ApexUi {
    static final int BLACK = Color.rgb(0, 0, 0);
    static final int BACKGROUND = Color.rgb(12, 12, 12);
    static final int CARD = Color.rgb(24, 24, 24);
    static final int FIELD = Color.rgb(30, 30, 30);
    static final int BORDER = Color.rgb(38, 38, 38);
    static final int MUTED = Color.rgb(146, 146, 146);
    static final int WHITE = Color.WHITE;
    static final int APEX_BLUE = Color.rgb(38, 167, 225);

    private ApexUi() {}

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context context, CharSequence value, float sizeSp, int color, int style) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
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

    static ColorStateList buttonTextColors() {
        return new ColorStateList(
            new int[][] { new int[] {-android.R.attr.state_enabled}, new int[] {} },
            new int[] { MUTED, BLACK }
        );
    }

    static void setEnabledAppearance(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.48f);
    }
}
