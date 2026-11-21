package com.particle.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;

/** Settings shared between the in-app preview (WebView) and the live wallpaper service. */
final class Prefs {
    static final String NAME = "particle_prefs";
    static final String COUNT = "count";
    static final String SPEED = "speed";
    static final String LINKS = "links";
    static final String THEME = "theme";
    static final String HUE = "hue";

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }
}
