package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class FontScaleHelper {

    private static final String PREFS_NAME = "circle_events_prefs";
    private static final String KEY_FONT_SCALE = "font_scale";
    private static final float DEFAULT_SCALE = 1.2f;
    private static final int TAG_ORIGINAL_SIZE = "original_text_size".hashCode();

    public static float getFontScale(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getFloat(KEY_FONT_SCALE, DEFAULT_SCALE);
    }

    public static void saveFontScale(Context context, float scale) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putFloat(KEY_FONT_SCALE, scale)
                .apply();
    }

    public static void applyFontScale(View rootView, Context context) {
        float scale = getFontScale(context);
        applyScaleToView(rootView, scale);
    }

    public static void applyScaleToView(View view, float scale) {
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            Float originalSize = (Float) tv.getTag(TAG_ORIGINAL_SIZE);
            if (originalSize == null) {
                originalSize = tv.getTextSize();
                tv.setTag(TAG_ORIGINAL_SIZE, originalSize);
            }
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, originalSize * scale);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyScaleToView(group.getChildAt(i), scale);
            }
        }
    }
}
