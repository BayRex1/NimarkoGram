package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;
import android.view.View;

import java.lang.ref.WeakReference;

/**
 * Animated, theme-friendly brand pill used for the MofGram chat-list title.
 * The animation runs only while the owning title view is attached to a window.
 */
public final class MofGramTitleSpan extends ReplacementSpan implements View.OnAttachStateChangeListener {

    private final float density;
    private final ValueAnimator animator;
    private WeakReference<View> viewRef;
    private float phase;

    private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path starPath = new Path();
    private final RectF pill = new RectF();

    public MofGramTitleSpan(float density) {
        this.density = density;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(4600L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            View view = viewRef == null ? null : viewRef.get();
            if (view != null) {
                view.invalidate();
            }
        });
    }

    public void attach(View view) {
        if (view == null) {
            return;
        }
        View previous = viewRef == null ? null : viewRef.get();
        if (previous == view) {
            if (view.isAttachedToWindow() && !animator.isStarted()) {
                animator.start();
            }
            return;
        }
        detach();
        viewRef = new WeakReference<>(view);
        view.addOnAttachStateChangeListener(this);
        if (view.isAttachedToWindow()) {
            animator.start();
        }
    }

    public void detach() {
        View view = viewRef == null ? null : viewRef.get();
        if (view != null) {
            view.removeOnAttachStateChangeListener(this);
        }
        viewRef = null;
        animator.cancel();
    }

    @Override
    public void onViewAttachedToWindow(View view) {
        if (!animator.isStarted()) {
            animator.start();
        }
    }

    @Override
    public void onViewDetachedFromWindow(View view) {
        animator.cancel();
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        paint.getFontMetricsInt(fm);
        float textWidth = paint.measureText(text, start, end);
        return Math.round(textWidth + dp(43) + dp(34));
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top,
                     int y, int bottom, Paint sourcePaint) {
        TextPaint textPaint = sourcePaint instanceof TextPaint
                ? new TextPaint((TextPaint) sourcePaint) : new TextPaint(sourcePaint);
        float textWidth = textPaint.measureText(text, start, end);
        float starSize = dp(13);
        float leftPadding = dp(13);
        float gap = dp(8);
        float rightPadding = dp(14);
        float width = leftPadding + starSize + gap + textWidth + rightPadding;
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textHeight = fm.descent - fm.ascent;
        float pillHeight = Math.max(dp(31), textHeight + dp(13));
        float centerY = y + (fm.ascent + fm.descent) * 0.5f;

        pill.set(x, centerY - pillHeight * 0.5f, x + width, centerY + pillHeight * 0.5f);

        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setShader(null);
        shapePaint.setColor(0x142D3A70);
        canvas.drawRoundRect(pill, pillHeight * 0.5f, pillHeight * 0.5f, shapePaint);

        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(dp(1.15f));
        shapePaint.setShader(new LinearGradient(
                pill.left, pill.top, pill.right, pill.bottom,
                new int[] { 0xFF8F8BFF, 0xFFB7C8FF, 0xFF79D8F2, 0xFF9D8BFA },
                new float[] { 0f, 0.34f, 0.68f, 1f }, Shader.TileMode.CLAMP));
        shapePaint.setShadowLayer(dp(3.5f), 0f, 0f, 0x558E9CFF);
        canvas.drawRoundRect(pill, pillHeight * 0.5f, pillHeight * 0.5f, shapePaint);
        shapePaint.clearShadowLayer();
        shapePaint.setShader(null);

        float starCenterX = x + leftPadding + starSize * 0.5f;
        float starCenterY = centerY;
        buildStar(starCenterX, starCenterY, starSize * 0.5f, starSize * 0.19f);
        starPaint.setStyle(Paint.Style.FILL);
        starPaint.setShader(new LinearGradient(
                starCenterX - starSize, starCenterY - starSize,
                starCenterX + starSize, starCenterY + starSize,
                new int[] { 0xFFB8C7FF, 0xFF86CFF5, 0xFFD0B8FF },
                null, Shader.TileMode.CLAMP));
        canvas.drawPath(starPath, starPaint);
        starPaint.setShader(null);

        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setShader(new LinearGradient(
                x + leftPadding + starSize + gap, pill.top,
                x + width - rightPadding, pill.bottom,
                new int[] { 0xFFB6C5FF, 0xFFD0C6FF, 0xFF91D7F5 },
                null, Shader.TileMode.CLAMP));
        canvas.drawText(text, start, end, x + leftPadding + starSize + gap,
                centerY - (fm.ascent + fm.descent) * 0.5f, textPaint);
        textPaint.setShader(null);
    }

    private void buildStar(float cx, float cy, float outer, float inner) {
        starPath.reset();
        for (int i = 0; i < 8; i++) {
            double angle = -Math.PI / 2.0 + i * Math.PI / 4.0;
            float radius = (i & 1) == 0 ? outer : inner;
            float px = cx + (float) Math.cos(angle) * radius;
            float py = cy + (float) Math.sin(angle) * radius;
            if (i == 0) {
                starPath.moveTo(px, py);
            } else {
                starPath.lineTo(px, py);
            }
        }
        starPath.close();
    }

    private float dp(float value) {
        return value * density;
    }
}
