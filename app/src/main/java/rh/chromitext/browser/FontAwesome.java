package rh.chromitext.browser;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;
import java.io.FileNotFoundException;

public final class FontAwesome {
    private static Typeface solid;
    private static Typeface regular;
    private static Typeface brands;
    private static boolean checked;

    private FontAwesome() { }

    public static synchronized void apply(Context context, TextView view, String family, int codePoint, String fallback) {
        load(context);
        Typeface typeface = solid;
        if ("regular".equals(family)) typeface = regular;
        if ("brands".equals(family)) typeface = brands;
        if (typeface != null) {
            view.setTypeface(typeface);
            view.setText(new String(Character.toChars(codePoint)));
        } else {
            view.setTypeface(Typeface.DEFAULT);
            view.setText(fallback);
        }
    }

    private static void load(Context context) {
        if (checked) return;
        checked = true;
        try { solid = Typeface.createFromAsset(context.getAssets(), "fonts/fa-solid-900.ttf"); }
        catch (Exception ignored) { solid = null; }
        try { regular = Typeface.createFromAsset(context.getAssets(), "fonts/fa-regular-400.ttf"); }
        catch (Exception ignored) { regular = null; }
        try { brands = Typeface.createFromAsset(context.getAssets(), "fonts/fa-brands-400.ttf"); }
        catch (Exception ignored) { brands = null; }
    }
}
