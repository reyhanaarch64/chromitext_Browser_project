package rh.chromitext.browser;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared builders for the browser's full-screen internal pages and action rows. */
public final class BrowserPageLayout {
    public static final class Page {
        public final LinearLayout root;
        public final LinearLayout body;

        private Page(LinearLayout root, LinearLayout body) {
            this.root = root;
            this.body = body;
        }
    }

    private final Activity activity;
    private final int panelColor;
    private final int buttonColor;
    private final int borderColor;
    private final int textColor;

    public BrowserPageLayout(Activity activity, int panelColor, int buttonColor, int borderColor, int textColor) {
        this.activity = activity;
        this.panelColor = panelColor;
        this.buttonColor = buttonColor;
        this.borderColor = borderColor;
        this.textColor = textColor;
    }

    public Page createPage(String title, View.OnClickListener backListener) {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(5), dp(5), dp(5), dp(5));
        page.setBackgroundColor(panelColor);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView back = new TextView(activity);
        FontAwesome.apply(activity, back, "solid", 0xf060, "<");
        back.setTextColor(textColor);
        back.setTextSize(16);
        back.setGravity(Gravity.CENTER);
        back.setBackground(background(buttonColor, borderColor));
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        back.setOnClickListener(backListener);

        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextColor(textColor);
        heading.setTextSize(18);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setPadding(dp(10), 0, dp(4), 0);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(44), 1));
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(44)));

        ScrollView scroll = new ScrollView(activity);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(6), 0, dp(14));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return new Page(page, body);
    }

    public void addAction(LinearLayout body, int iconCode, String title, String description, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(9), dp(8), dp(9));
        row.setBackground(background(isDark() ? Color.rgb(30, 34, 41) : Color.rgb(255, 255, 255), borderColor));

        TextView icon = new TextView(activity);
        FontAwesome.apply(activity, icon, "solid", iconCode, "•");
        icon.setTextColor(textColor);
        icon.setGravity(Gravity.CENTER);
        icon.setTextSize(17);
        row.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(40)));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextColor(textColor);
        heading.setTextSize(14);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        texts.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        if (description != null && description.length() > 0) {
            TextView detail = new TextView(activity);
            detail.setText(description);
            detail.setTextColor(isDark() ? Color.rgb(175, 183, 193) : Color.rgb(90, 90, 90));
            detail.setTextSize(11);
            detail.setPadding(0, dp(2), 0, 0);
            texts.addView(detail, new LinearLayout.LayoutParams(-1, -2));
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(5);
        body.addView(row, params);
    }

    public void addNote(LinearLayout body, String value) {
        TextView note = new TextView(activity);
        note.setText(value);
        note.setTextColor(isDark() ? Color.rgb(190, 197, 206) : Color.rgb(75, 75, 75));
        note.setTextSize(12);
        note.setPadding(dp(5), dp(8), dp(5), dp(10));
        body.addView(note, new LinearLayout.LayoutParams(-1, -2));
    }

    private boolean isDark() { return Color.red(panelColor) < 80 && Color.green(panelColor) < 80 && Color.blue(panelColor) < 90; }

    private int dp(float value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable background(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(0);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }
}
