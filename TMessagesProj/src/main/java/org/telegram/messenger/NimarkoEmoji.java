package org.telegram.messenger;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import java.io.InputStream;

/** Static local seahorse emoji rendered from the app's bundled PNG asset. */
public final class NimarkoEmoji {
    public static final String SEAHORSE_CODE = "\\uE000";
    private static volatile Bitmap seahorseBitmap;

    private NimarkoEmoji() {}

    public static boolean isSeahorse(CharSequence value) {
        return value != null && SEAHORSE_CODE.contentEquals(value);
    }

    private static Bitmap getBitmap() {
        Bitmap bitmap = seahorseBitmap;
        if (bitmap != null && !bitmap.isRecycled()) {
            return bitmap;
        }
        synchronized (NimarkoEmoji.class) {
            bitmap = seahorseBitmap;
            if (bitmap == null || bitmap.isRecycled()) {
                try (InputStream input = ApplicationLoader.applicationContext.getAssets().open("seahorse.png")) {
                    bitmap = BitmapFactory.decodeStream(input);
                    seahorseBitmap = bitmap;
                } catch (Throwable error) {
                    FileLog.e(error);
                    return null;
                }
            }
        }
        return bitmap;
    }

    public static final class SeahorseDrawable extends Emoji.EmojiDrawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private int alpha = 255;
        private ColorFilter colorFilter;

        @Override
        public void draw(Canvas canvas) {
            Bitmap bitmap = getBitmap();
            Rect bounds = getBounds();
            if (bitmap == null || bitmap.isRecycled() || bounds.isEmpty()) {
                return;
            }
            int save = canvas.save();
            float scale = Math.min(bounds.width() / (float) bitmap.getWidth(),
                    bounds.height() / (float) bitmap.getHeight());
            float width = bitmap.getWidth() * scale;
            float height = bitmap.getHeight() * scale;
            float left = bounds.centerX() - width / 2f;
            float top = bounds.centerY() - height / 2f;
            paint.setAlpha(alpha);
            paint.setColorFilter(colorFilter);
            canvas.drawBitmap(bitmap, null,
                    new android.graphics.RectF(left, top, left + width, top + height), paint);
            canvas.restoreToCount(save);
        }

        @Override
        public void setAlpha(int value) {
            alpha = value;
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            colorFilter = filter;
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
