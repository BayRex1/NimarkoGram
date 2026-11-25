package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
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
        // Measure with the same bold weight that draw() uses, so the pill is wide enough.
        Paint measurePaint = new Paint(paint);
        measurePaint.setFakeBoldText(true);
        measurePaint.getFontMetricsInt(fm);
        float textWidth = measurePaint.measureText(text, start, end);
        return Math.round(textWidth + dp(38));
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top,
                     int y, int bottom, Paint sourcePaint) {
        TextPaint textPaint = sourcePaint instanceof TextPaint
                ? new TextPaint((TextPaint) sourcePaint) : new TextPaint(sourcePaint);
        float textWidth = textPaint.measureText(text, start, end);
        float starSize = dp(11);
        float leftPadding = dp(10);
        float gap = dp(6);
        float rightPadding = dp(10);
        float width = leftPadding + starSize + gap + textWidth + rightPadding;
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textHeight = fm.descent - fm.ascent;
        // Same height as the title view, otherwise the bottom of the pill is clipped.
        float pillHeight = Math.min(dp(24), Math.max(dp(20), textHeight));
        float centerY = y + (fm.ascent + fm.descent) * 0.5f;

        pill.set(x, centerY - pillHeight * 0.5f, x + width, centerY + pillHeight * 0.5f);

        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setShader(null);
        shapePaint.setColor(0x142D3A70);
        canvas.drawRoundRect(pill, pillHeight * 0.5f, pillHeight * 0.5f, shapePaint);

        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(dp(2.2f));
        LinearGradient outlineGradient = new LinearGradient(
                pill.left, pill.top, pill.right, pill.bottom,
                new int[] { 0xFFB0A8FF, 0xFFE4EAFF, 0xFF8FEBFF, 0xFFC9B3FF },
                new float[] { 0f, 0.34f, 0.68f, 1f }, Shader.TileMode.CLAMP);
        Matrix gradientMatrix = new Matrix();
        gradientMatrix.setTranslate((phase - 0.5f) * dp(34), 0f);
        outlineGradient.setLocalMatrix(gradientMatrix);
        shapePaint.setShader(outlineGradient);
        shapePaint.setShadowLayer(dp(6f), 0f, 0f, 0xCC9CB8FF);
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
        textPaint.setFakeBoldText(true);
        LinearGradient textGradient = new LinearGradient(
                x + leftPadding + starSize + gap, pill.top,
                x + width - rightPadding, pill.bottom,
                new int[] { 0xFFB6C5FF, 0xFFD0C6FF, 0xFF91D7F5 },
                null, Shader.TileMode.CLAMP);
        Matrix textMatrix = new Matrix();
        textMatrix.setTranslate((phase - 0.5f) * dp(18), 0f);
        textGradient.setLocalMatrix(textMatrix);
        textPaint.setShader(textGradient);
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
