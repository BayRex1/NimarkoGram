package app.nimarkogram.messenger.ui;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;
import android.view.animation.LinearInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.SimpleTextView;

/**
 * Main chat-list title: bold periwinkle text with a sparkle icon, inside an island-style pill
 * whose border turns around like a light.
 */
public final class MoGramTitle {

    private static final int TEXT_COLOR = 0xFFA3B7FF;
    private static final int GLOW_COLOR = 0xFF5B7CFF;
    private static final int FILL_COLOR = 0xFF0E131B;

    private MoGramTitle() {
    }

    /** Title with a sparkle in front of the text. Keeps the text itself unchanged. */
    public static SpannableStringBuilder style(CharSequence title) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        // Placeholder character replaced by the sparkle icon.
        ssb.append("\uFFFC");
        ssb.setSpan(new ImageSpan(new SparkleDrawable(), ImageSpan.ALIGN_CENTER), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.append(" ");
        int start = ssb.length();
        ssb.append(title);
        ssb.setSpan(new ForegroundColorSpan(TEXT_COLOR), start, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new StyleSpan(Typeface.BOLD), start, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ssb;
    }

    /** Puts the animated island pill behind the title view. Call after the title text is set. */
    public static void installPill(SimpleTextView titleView) {
        if (titleView == null) {
            return;
        }
        titleView.setBackgroundDrawable(new PillDrawable(titleView));
    }

    /** Four-point sparkle, same shape as the star in the reference design. */
    private static final class SparkleDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int size = AndroidUtilities.dp(16);

        SparkleDrawable() {
            paint.setColor(TEXT_COLOR);
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            float cx = b.centerX();
            float cy = b.centerY();
            float r = Math.min(b.width(), b.height()) / 2f;
            path.reset();
            path.moveTo(cx, cy - r);
            path.quadTo(cx, cy, cx + r, cy);
            path.quadTo(cx, cy, cx, cy + r);
            path.quadTo(cx, cy, cx - r, cy);
            path.quadTo(cx, cy, cx, cy - r);
            path.close();
            canvas.drawPath(path, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Dark pill with a light that runs around its border. Redraws the title view while animating. */
    private static final class PillDrawable extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final RectF bounds = new RectF();
        private final android.graphics.Matrix matrix = new android.graphics.Matrix();
        private final float strokeWidth = AndroidUtilities.dp(1.5f);
        private float angle;

        PillDrawable(SimpleTextView view) {
            fill.setColor(FILL_COLOR);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(strokeWidth);

            ValueAnimator animator = ValueAnimator.ofFloat(0f, 360f);
            animator.setDuration(3200);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a -> {
                angle = (float) a.getAnimatedValue();
                invalidateSelf();
            });
            animator.start();
        }

        @Override
        protected void onBoundsChange(android.graphics.Rect bounds) {
            super.onBoundsChange(bounds);
            this.bounds.set(bounds);
        }

        @Override
        public void draw(Canvas canvas) {
            RectF outer = bounds;
            float radius = outer.height() / 2f;
            canvas.drawRoundRect(outer, radius, radius, fill);

            rect.set(outer);
            rect.inset(strokeWidth / 2f, strokeWidth / 2f);
            SweepGradient sweep = new SweepGradient(rect.centerX(), rect.centerY(),
                    new int[]{0x00000000, GLOW_COLOR, TEXT_COLOR, 0x00000000},
                    new float[]{0f, 0.25f, 0.5f, 1f});
            matrix.reset();
            matrix.postRotate(angle, rect.centerX(), rect.centerY());
            sweep.setLocalMatrix(matrix);
            border.setShader(sweep);
            canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, border);
        }

        @Override
        public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            border.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            fill.setColorFilter(colorFilter);
            border.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
