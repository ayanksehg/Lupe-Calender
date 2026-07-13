package com.calendar.lupe;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.core.content.ContextCompat;

import java.util.Random;


public class LoaderView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private LoaderVariant variant = LoaderVariant.CLOCK;
    private ValueAnimator animator;
    private float fraction;

    private int blue, blueDark, accent, page;

    public LoaderView(Context c) { super(c); init(); }
    public LoaderView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        blue = ContextCompat.getColor(getContext(), R.color.watch_blue);
        blueDark = ContextCompat.getColor(getContext(), R.color.watch_blue_dark);
        accent = ContextCompat.getColor(getContext(), R.color.watch_accent);
        page = ContextCompat.getColor(getContext(), R.color.watch_input_bg);
        variant = LoaderVariant.pick(random);
    }


    public void randomize() {
        variant = LoaderVariant.pick(random);
        if (animator != null) { animator.cancel(); animator.start(); }
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2200);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> { fraction = (float) a.getAnimatedValue(); invalidate(); });
        animator.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) { animator.cancel(); animator = null; }
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float size = Math.min(w, h) * 0.7f;
        if (variant == LoaderVariant.CLOCK) drawClock(canvas, cx, cy, size / 2f);
        else drawCalendar(canvas, cx, cy, size);
    }

    private void drawClock(Canvas canvas, float cx, float cy, float r) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(r * 0.12f);
        paint.setColor(blue);
        canvas.drawCircle(cx, cy, r, paint);

        paint.setStrokeCap(Paint.Cap.ROUND);

        drawHand(canvas, cx, cy, r * 0.5f, fraction * 360f, blueDark, r * 0.12f);
        drawHand(canvas, cx, cy, r * 0.82f, fraction * 360f * 6f, accent, r * 0.09f);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blueDark);
        canvas.drawCircle(cx, cy, r * 0.1f, paint);
    }

    private void drawHand(Canvas canvas, float cx, float cy, float len, float deg, int color, float width) {
        paint.setColor(color);
        paint.setStrokeWidth(width);
        double rad = Math.toRadians(deg - 90);
        float ex = cx + (float) Math.cos(rad) * len;
        float ey = cy + (float) Math.sin(rad) * len;
        canvas.drawLine(cx, cy, ex, ey, paint);
    }

    private void drawCalendar(Canvas canvas, float cx, float cy, float size) {
        float half = size / 2f;
        RectF body = new RectF(cx - half, cy - half, cx + half, cy + half);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(body, size * 0.12f, size * 0.12f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * 0.045f);
        paint.setColor(blueDark);
        canvas.drawRoundRect(body, size * 0.12f, size * 0.12f, paint);

        float headerH = size * 0.28f;
        RectF header = new RectF(body.left, body.top, body.right, body.top + headerH);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blue);
        canvas.drawRoundRect(header, size * 0.12f, size * 0.12f, paint);
        canvas.drawRect(header.left, header.top + headerH * 0.5f, header.right, header.bottom, paint);

    
        float pageTop = body.top + headerH;
        float flip = Math.min(1f, fraction / 0.55f);
        float scaleY = (float) Math.cos(Math.PI * flip); // 1 -> -1
        int shade = (int) (40 * Math.abs(1 - scaleY));
        canvas.save();
        canvas.translate(0, pageTop);
        canvas.scale(1f, scaleY, cx, 0);
        paint.setColor(scaleY >= 0 ? page : blend(page, Color.BLACK, shade / 255f));
        canvas.drawRect(body.left + size * 0.06f, size * 0.02f,
                body.right - size * 0.06f, size - headerH - size * 0.02f, paint);
        canvas.restore();
    }

    private int blend(int a, int b, float t) {
        return Color.rgb(
            (int) (Color.red(a) * (1 - t) + Color.red(b) * t),
            (int) (Color.green(a) * (1 - t) + Color.green(b) * t),
            (int) (Color.blue(a) * (1 - t) + Color.blue(b) * t));
    }
}
