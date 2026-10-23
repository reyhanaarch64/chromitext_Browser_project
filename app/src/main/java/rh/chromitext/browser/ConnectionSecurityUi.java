package rh.chromitext.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.net.Uri;
import android.widget.TextView;

/** Presents HTTP/HTTPS transport-security state independently from the browser Activity. */
public final class ConnectionSecurityUi {
    private static final int SCHEME_OTHER = 0;
    private static final int SCHEME_HTTP = 1;
    private static final int SCHEME_HTTPS = 2;

    private ConnectionSecurityUi() { }

    private static int schemeType(String url) {
        try {
            String scheme = Uri.parse(url == null ? "" : url).getScheme();
            if ("https".equalsIgnoreCase(scheme)) return SCHEME_HTTPS;
            if ("http".equalsIgnoreCase(scheme)) return SCHEME_HTTP;
        } catch (Exception ignored) { }
        return SCHEME_OTHER;
    }

    public static void update(Activity activity, TextView button, String url, int defaultTextColor) {
        update(activity, button, url, defaultTextColor, false);
    }

    public static void update(Activity activity, TextView button, String url, int defaultTextColor, boolean certificateInvalid) {
        if (activity == null || button == null) return;
        int type = schemeType(url);
        if (type == SCHEME_HTTPS && certificateInvalid) {
            FontAwesome.apply(activity, button, "solid", 0xf09c, "\uD83D\uDD13");
            button.setTextColor(Color.rgb(210, 90, 55));
            button.setContentDescription(LanguageManager.get("address.lock.invalid_certificate", "Sertifikat HTTPS tidak valid"));
        } else if (type == SCHEME_HTTPS) {
            FontAwesome.apply(activity, button, "solid", 0xf023, "\uD83D\uDD12");
            button.setTextColor(Color.rgb(45, 155, 92));
            button.setContentDescription(LanguageManager.get("address.lock.secure", "Koneksi HTTPS terenkripsi"));
        } else if (type == SCHEME_HTTP) {
            FontAwesome.apply(activity, button, "solid", 0xf09c, "\uD83D\uDD13");
            button.setTextColor(Color.rgb(210, 90, 55));
            button.setContentDescription(LanguageManager.get("address.lock.insecure", "Koneksi HTTP tidak terenkripsi"));
        } else {
            FontAwesome.apply(activity, button, "solid", 0xf0ac, "\uD83C\uDF10");
            button.setTextColor(defaultTextColor);
            button.setContentDescription(LanguageManager.get("address.lock.other", "Status keamanan tidak tersedia untuk skema ini"));
        }
    }

    public static void showInfo(Activity activity, String url) {
        showInfo(activity, url, false);
    }

    public static void showInfo(Activity activity, String url, boolean certificateInvalid) {
        int type = schemeType(url);
        String title;
        String message;
        if (type == SCHEME_HTTPS && certificateInvalid) {
            title = LanguageManager.get("security.certificate_invalid.title", "Sertifikat HTTPS tidak valid");
            message = LanguageManager.get("security.certificate_invalid.message", "Sertifikat keamanan situs tidak dapat diverifikasi. Chromitext membatalkan koneksi ini. Jangan masukkan informasi sensitif sampai masalah sertifikat terselesaikan.");
        } else if (type == SCHEME_HTTPS) {
            title = LanguageManager.get("security.secure.title", "Koneksi aman (HTTPS)");
            message = LanguageManager.get("security.secure.message", "Koneksi ke situs ini menggunakan HTTPS, sehingga data antara browser dan situs dienkripsi saat dikirim. Ini membantu melindungi data dari penyadapan saat transit, tetapi tidak menjamin situs itu sendiri tepercaya atau bebas dari penipuan.");
        } else if (type == SCHEME_HTTP) {
            title = LanguageManager.get("security.insecure.title", "Koneksi tidak aman (HTTP)");
            message = LanguageManager.get("security.insecure.message", "Situs ini menggunakan HTTP tanpa enkripsi transport. Informasi yang dikirim atau diterima dapat disadap atau diubah oleh pihak di jaringan. Hindari memasukkan kata sandi, data pembayaran, atau informasi sensitif. Jika tersedia, gunakan versi HTTPS situs ini.");
        } else {
            title = LanguageManager.get("security.other.title", "Status koneksi tidak diketahui");
            message = LanguageManager.get("security.other.message", "Halaman ini bukan alamat web HTTP atau HTTPS. Indikator keamanan hanya dapat menjelaskan koneksi HTTP/HTTPS.");
        }
        new AlertDialog.Builder(activity).setTitle(title).setMessage(message)
            .setPositiveButton(LanguageManager.get("action.ok", "OK"), null).show();
    }
}
