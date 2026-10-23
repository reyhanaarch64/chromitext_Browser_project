package rh.chromitext.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Toast;
import java.util.Map;

/** Language registry picker; the host decides how to refresh the visible browser UI. */
public final class LanguageSettingsController {
    public interface Listener {
        void onLanguageChanged(String languageCode);
    }

    private LanguageSettingsController() { }

    public static void show(final Activity activity, final BrowserDatabase database, final Listener listener) {
        final Map<String, String> languages = LanguageManager.getLanguages(activity);
        if (languages.size() == 0) {
            new AlertDialog.Builder(activity)
                .setTitle(LanguageManager.get("language.title", "Bahasa Aplikasi"))
                .setMessage(LanguageManager.get("language.registry_missing", "Daftar bahasa tidak tersedia."))
                .setPositiveButton(LanguageManager.get("action.ok", "OK"), null).show();
            return;
        }
        final String[] codes = languages.keySet().toArray(new String[languages.size()]);
        final String[] labels = languages.values().toArray(new String[languages.size()]);
        String active = database.getSetting("language", "id_ID");
        int selected = 0;
        for (int i = 0; i < codes.length; i++) if (codes[i].equals(active)) selected = i;
        new AlertDialog.Builder(activity)
            .setTitle(LanguageManager.get("language.title", "Bahasa Aplikasi"))
            .setSingleChoiceItems(labels, selected, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    String code = codes[which];
                    database.putSetting("language", code);
                    LanguageManager.load(activity, code);
                    dialog.dismiss();
                    Toast.makeText(activity, LanguageManager.get("language.changed", "Bahasa akan diterapkan ke tampilan aplikasi."), Toast.LENGTH_SHORT).show();
                    if (listener != null) listener.onLanguageChanged(code);
                }
            })
            .setNegativeButton(LanguageManager.get("action.cancel", "Batal"), null)
            .show();
    }
}
