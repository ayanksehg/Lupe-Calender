package com.calendar.lupe;

import android.content.Context;
import android.content.SharedPreferences;

import com.calendar.lupe.ui.home.OccurrenceScope;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;


public final class FavoritesHelper {

    private static final String PREFS_NAME = "lupe_prefs";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_NOTIFY_ONLY = "notify_only_favorites";
    private static final String DATE_FMT = "MM/dd/yyyy";

    private FavoritesHelper() {}



    public static boolean isNotifyOnlyFavorites(Context ctx) {
        return prefs(ctx).getBoolean(KEY_NOTIFY_ONLY, false);
    }

    public static void setNotifyOnlyFavorites(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean(KEY_NOTIFY_ONLY, value).apply();
    }




    public static boolean isFavorite(Context ctx, String id, String date) {
        if (id == null) return false;
        Set<String> set = read(ctx);
        if (set.contains(id)) return true;                       // whole series
        if (date != null && set.contains(id + "|@|" + date)) return true; // this instance
        long target = parse(date);
        if (target >= 0) {
            String prefix = id + "|>|";
            for (String entry : set) {
                if (entry.startsWith(prefix)) {
                    long from = parse(entry.substring(prefix.length()));
                    if (from >= 0 && target >= from) return true; // forward
                }
            }
        }
        return false;
    }

    public static void addFavorite(Context ctx, String id, String date, OccurrenceScope scope) {
        if (id == null) return;
        Set<String> set = read(ctx);
        switch (scope) {
            case SERIES:   set.add(id); break;
            case FORWARD:  if (date != null) set.add(id + "|>|" + date); break;
            case INSTANCE:
            default:       if (date != null) set.add(id + "|@|" + date); break;
        }
        write(ctx, set);
    }


    public static void removeFavoriteSeries(Context ctx, String id) {
        if (id == null) return;
        Set<String> set = read(ctx);
        Set<String> kept = new HashSet<>();
        String instPrefix = id + "|@|";
        String fwdPrefix = id + "|>|";
        for (String entry : set) {
            if (entry.equals(id) || entry.startsWith(instPrefix) || entry.startsWith(fwdPrefix)) {
                continue;
            }
            kept.add(entry);
        }
        write(ctx, kept);
    }



    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static Set<String> read(Context ctx) {

        return new HashSet<>(prefs(ctx).getStringSet(KEY_FAVORITES, new HashSet<>()));
    }

    private static void write(Context ctx, Set<String> set) {
        prefs(ctx).edit().putStringSet(KEY_FAVORITES, set).apply();
    }

    private static long parse(String date) {
        if (date == null) return -1;
        try {
            Date d = new SimpleDateFormat(DATE_FMT, Locale.getDefault()).parse(date.trim());
            return d == null ? -1 : d.getTime();
        } catch (Exception e) {
            return -1;
        }
    }
}
