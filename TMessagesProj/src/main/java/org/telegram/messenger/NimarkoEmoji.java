package org.telegram.messenger;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

/** Local animated seahorse emoji. The private-use marker is never sent as a Unicode emoji. */
public final class NimarkoEmoji {
    public static final String SEAHORSE_CODE = "\\uE000";

    private NimarkoEmoji() {}

    public static boolean isSeahorse(CharSequence value) {
        return value != null && SEAHORSE_CODE.contentEquals(value);
    }

    public static final class SeahorseDrawable extends Emoji.EmojiDrawable implements Runnable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path body = new Path();
        private final Path shape = new Path();
        private final RectF r = new RectF();
        private long startedAt;
        private boolean scheduled;
        private int alpha = 255;
        private ColorFilter colorFilter;

        private void fill(Canvas c, Path path, int top, int middle, int bottom) {
            Rect b = getBounds();
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(16, 8, 51, 62,
                    new int[]{top, middle, bottom}, new float[]{0f, .52f, 1f}, Shader.TileMode.CLAMP));
            p.setAlpha(alpha);
            p.setColorFilter(colorFilter);
            c.drawPath(path, p);
            p.setShader(null);
        }

        private void stroke(Canvas c, Path path, int color, float width) {
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(width);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(color);
            p.setAlpha(alpha);
            p.setColorFilter(colorFilter);
            c.drawPath(path, p);
            p.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            if (startedAt == 0) startedAt = SystemClock.uptimeMillis();
            float t = (SystemClock.uptimeMillis() - startedAt) / 1900f;
            float bob = (float) Math.sin(t * Math.PI * 2) * 1.05f;
            float finWave = (float) Math.sin(t * Math.PI * 4) * 2.2f;
            int save = canvas.save();
            float scale = Math.min(b.width(), b.height()) / 64f;
            canvas.translate(b.centerX() - 32f * scale, b.centerY() - 32f * scale + bob * scale);
            canvas.scale(scale, scale);

            // Soft ambient shadow and floating bubbles.
            p.setColor(0x26351B22); p.setAlpha(Math.round(38 * alpha / 255f));
            canvas.drawOval(17, 57, 48, 62, p);

            for (int i = 0; i < 3; i++) {
                float phase = (t * .52f + i * .34f) % 1f;
                float x = 51f + (float) Math.sin(phase * 6.2f + i) * 2.2f;
                float y = 49f - phase * 43f;
                float radius = 1.1f + i * .45f;
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.85f);
                p.setColor(0xAA9CEBFF); p.setAlpha(Math.round(170 * alpha / 255f));
                canvas.drawCircle(x, y, radius, p);
                p.setStyle(Paint.Style.FILL); p.setColor(0xDFFFFFFF);
                canvas.drawCircle(x - radius * .32f, y - radius * .32f, .48f, p);
            }
            p.setStyle(Paint.Style.FILL);

            // Back fin: translucent, softly shaded fan.
            shape.reset();
            shape.moveTo(25, 28);
            shape.cubicTo(17, 22 - finWave, 8, 20 - finWave, 7, 27);
            shape.cubicTo(6, 34 + finWave, 14, 39 + finWave, 25, 36);
            shape.close();
            p.setShader(new LinearGradient(8, 22, 24, 37,
                    new int[]{0xFFFFD47B, 0xFFFF8C62, 0xFFE95D61}, null, Shader.TileMode.CLAMP));
            p.setAlpha(alpha); p.setColorFilter(colorFilter);
            canvas.drawPath(shape, p); p.setShader(null);
            stroke(canvas, shape, 0x66A94735, 1.0f);

            // Tail and body are one continuous sculpted silhouette.
            body.reset();
            body.moveTo(38, 8);
            body.cubicTo(46, 7, 51, 12, 49, 18);
            body.cubicTo(48, 21, 45, 23, 42, 24);
            body.cubicTo(44, 30, 41, 35, 37, 39);
            body.cubicTo(34, 43, 36, 47, 42, 50);
            body.cubicTo(49, 53, 50, 59, 45, 62);
            body.cubicTo(39, 66, 30, 62, 30, 56);
            body.cubicTo(30, 52, 33, 49, 37, 51);
            body.cubicTo(40, 53, 44, 55, 46, 52);
            body.cubicTo(47, 49, 43, 47, 38, 44);
            body.cubicTo(31, 40, 29, 34, 33, 27);
            body.cubicTo(35, 23, 35, 19, 32, 17);
            body.cubicTo(28, 15, 23, 18, 21, 23);
            body.cubicTo(19, 28, 22, 32, 27, 34);
            body.cubicTo(22, 38, 15, 35, 13, 29);
            body.cubicTo(10, 20, 20, 10, 29, 9);
            body.cubicTo(32, 8, 35, 8, 38, 8);
            body.close();

            // Dark contour gives the form a polished sticker-like edge.
            stroke(canvas, body, 0x803F2635, 2.1f);
            fill(canvas, body, 0xFFFFE89B, 0xFFFFA63F, 0xFFE65B38);
            stroke(canvas, body, 0x66FFF1C2, .65f);

            // Belly plate with individual curved ridges.
            shape.reset();
            shape.moveTo(28, 31);
            shape.cubicTo(31, 35, 31, 39, 35, 43);
            shape.cubicTo(39, 47, 45, 49, 45, 53);
            shape.cubicTo(45, 57, 39, 58, 36, 54);
            shape.cubicTo(32, 50, 29, 46, 28, 41);
            shape.cubicTo(26, 37, 25, 34, 28, 31);
            shape.close();
            p.setShader(new LinearGradient(27, 31, 44, 56,
                    new int[]{0xFFFFE8A6, 0xFFFFC45D, 0xFFE98A39}, null, Shader.TileMode.CLAMP));
            p.setAlpha(alpha); p.setColorFilter(colorFilter);
            canvas.drawPath(shape, p); p.setShader(null);
            stroke(canvas, shape, 0x55A84D2D, .7f);

            for (int i = 0; i < 4; i++) {
                float y = 37 + i * 4.1f;
                shape.reset();
                shape.moveTo(29 - i * .35f, y);
                shape.cubicTo(33, y + 1.3f, 37, y + 2.2f, 41 + i * .45f, y + 1.7f);
                stroke(canvas, shape, 0xBFFFF1B0, 1.25f);
                stroke(canvas, shape, 0x55B95A31, .55f);
            }

            // Crown-like dorsal spines.
            shape.reset();
            shape.moveTo(26, 15);
            shape.lineTo(22, 5); shape.lineTo(29, 10);
            shape.lineTo(34, 3); shape.lineTo(36, 12);
            shape.lineTo(41, 7); shape.lineTo(41, 15);
            shape.cubicTo(36, 13, 31, 13, 26, 15);
            shape.close();
            p.setShader(new LinearGradient(24, 4, 39, 16,
                    new int[]{0xFFFFF0A6, 0xFFFFB84D, 0xFFE87838}, null, Shader.TileMode.CLAMP));
            p.setAlpha(alpha); p.setColorFilter(colorFilter);
            canvas.drawPath(shape, p); p.setShader(null);
            stroke(canvas, shape, 0x77B95C35, .8f);

            // Snout highlight and rounded muzzle.
            p.setShader(new LinearGradient(43, 15, 59, 21,
                    new int[]{0xFFFFF1B1, 0xFFFFB84C, 0xFFE97836}, null, Shader.TileMode.CLAMP));
            r.set(41, 14, 59, 22);
            p.setAlpha(alpha); p.setColorFilter(colorFilter);
            canvas.drawRoundRect(r, 3.8f, 3.8f, p); p.setShader(null);
            stroke(canvas, new Path() {{ moveTo(46, 15.2f); lineTo(56, 15.7f); }}, 0x88FFFFFF, .9f);

            // Eye: dark socket, ivory rim, teal iris and twin specular highlights.
            p.setColor(0xFF9C4B2D); canvas.drawCircle(42, 19, 4.2f, p);
            p.setColor(0xFFFFF8E8); canvas.drawCircle(42, 18.7f, 3.15f, p);
            p.setShader(new RadialGradient(41.4f, 18, 3.1f,
                    new int[]{0xFFBDF7F4, 0xFF27B9C7, 0xFF17617C}, null, Shader.TileMode.CLAMP));
            canvas.drawCircle(42.4f, 18.7f, 2.55f, p); p.setShader(null);
            p.setColor(0xFF123A57); canvas.drawCircle(43, 18.8f, 1.25f, p);
            p.setColor(0xFFFFFFFF); canvas.drawCircle(41.3f, 17.5f, .95f, p);
            p.setColor(0xBFFFFFFF); canvas.drawCircle(44.1f, 20.1f, .42f, p);

            // Glossy rim light on the head and shoulder.
            shape.reset();
            shape.moveTo(25, 18);
            shape.cubicTo(28, 12, 35, 10, 41, 11);
            stroke(canvas, shape, 0xBFFFF8D5, 1.35f);
            shape.reset();
            shape.moveTo(19, 24);
            shape.cubicTo(17, 29, 20, 33, 24, 33);
            stroke(canvas, shape, 0x88FFFFFF, .8f);

            canvas.restoreToCount(save);
            if (!scheduled) {
                scheduled = true;
                scheduleSelf(this, SystemClock.uptimeMillis() + 33);
            }
        }

        @Override public void run() {
            scheduled = false;
            invalidateSelf();
        }

        @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { colorFilter = filter; invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

        @Override public boolean setVisible(boolean visible, boolean restart) {
            boolean changed = super.setVisible(visible, restart);
            if (!visible) {
                unscheduleSelf(this);
                scheduled = false;
            } else if (restart) {
                startedAt = SystemClock.uptimeMillis();
            }
            return changed;
        }
    }
}
