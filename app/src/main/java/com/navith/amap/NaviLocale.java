package com.navith.amap;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

/** App-owned UI locale; AMAP may still return names from its regional dataset. */
public final class NaviLocale {
    private NaviLocale() {}
    public static String language(Context c) {
        return c.getSharedPreferences("navith_view", Context.MODE_PRIVATE)
            .getString("language", "th");
    }
    public static boolean thai(Context c) { return !"en".equals(language(c)); }
    public static String text(Context c, String th, String en) { return thai(c) ? th : en; }
    public static Context wrap(Context base) {
        String language = language(base);
        Configuration conf = new Configuration(base.getResources().getConfiguration());
        Locale locale = Locale.forLanguageTag(language);
        conf.setLocale(locale);
        return base.createConfigurationContext(conf);
    }
}
