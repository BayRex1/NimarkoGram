package org.telegram.messenger;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.LinearGradient;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

/** Built-in animated seahorse emoji. Its PUA marker is local to NimarkoGram. */
public final class NimarkoEmoji {
    public static final String SEAHORSE_CODE = "\uE000";
    private NimarkoEmoji() {}
    public static boolean isSeahorse(CharSequence value) {
        return value != null && SEAHORSE_CODE.contentEquals(value);
    }

    public static final class SeahorseDrawable extends Emoji.EmojiDrawable implements Runnable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF oval = new RectF();
        private long start;
        private boolean scheduled;

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            if (start == 0) start = SystemClock.uptimeMillis();
            float t = ((SystemClock.uptimeMillis() - start) % 2400L) / 2400f;
            float bob = (float)Math.sin(t * Math.PI * 2) * b.height() * .035f;
            int save = canvas.save();
            float scale = Math.min(b.width(), b.height()) / 64f;
            canvas.translate(b.centerX() - 32f * scale, b.centerY() - 32f * scale + bob);
            canvas.scale(scale, scale);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(7f);
            paint.setColor(0x553B2440);
            path.reset(); path.moveTo(35,43); path.cubicTo(47,49,51,58,42,59);
            path.cubicTo(33,60,34,51,42,53);
            canvas.drawPath(path,paint);
            paint.setStrokeWidth(5.5f); paint.setColor(0xFFFFA52E); canvas.drawPath(path,paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFB85B24); oval.set(19,10,48,49); canvas.drawOval(oval,paint);
            paint.setShader(new LinearGradient(18,10,45,49,
                new int[]{0xFFFFE18A,0xFFFFA52E,0xFFE96A32},null,Shader.TileMode.CLAMP));
            oval.set(17,8,45,47); canvas.drawOval(oval,paint); paint.setShader(null);

            paint.setColor(0xFFFFC65A);
            path.reset(); path.moveTo(36,17); path.cubicTo(43,11,54,14,54,21);
            path.cubicTo(54,26,47,27,43,24); path.cubicTo(39,22,36,23,34,27); path.close();
            canvas.drawPath(path,paint);
            paint.setColor(0xFFFF8D39);
            path.reset(); path.moveTo(24,12); path.lineTo(20,4); path.lineTo(29,9);
            path.lineTo(35,2); path.lineTo(37,13); path.close(); canvas.drawPath(path,paint);

            float fin = (float)Math.sin(t*Math.PI*4)*2.5f;
            paint.setColor(0xFFFF6F61);
            path.reset(); path.moveTo(20,24); path.cubicTo(8,18-fin,8,31+fin,18,34); path.close(); canvas.drawPath(path,paint);
            path.reset(); path.moveTo(25,39); path.cubicTo(13,37+fin,13,47-fin,23,48); path.close(); canvas.drawPath(path,paint);

            paint.setColor(0xFFFFD77A);
            for(int i=0;i<4;i++){ oval.set(25,27+i*4.5f,38,29.5f+i*4.5f); canvas.drawRoundRect(oval,2,2,paint); }
            paint.setColor(Color.WHITE); canvas.drawCircle(39,20,5.2f,paint);
            paint.setColor(0xFF35B9D5); canvas.drawCircle(40,20,3.6f,paint);
            paint.setColor(0xFF173B58); canvas.drawCircle(41,20,2.1f,paint);
            paint.setColor(Color.WHITE); canvas.drawCircle(41.5f,18.7f,1.1f,paint);

            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.7f);
            for(int i=0;i<3;i++){
                float p=(t*1.8f+i*.34f)%1f, y=45f-p*43f, x=49f+(float)Math.sin(p*5+i)*3f, r=1.4f+i*.55f;
                paint.setColor(0xAA8DEBFF); canvas.drawCircle(x,y,r,paint);
                paint.setStyle(Paint.Style.FILL); paint.setColor(0x88FFFFFF); canvas.drawCircle(x-r*.35f,y-r*.35f,.55f,paint);
                paint.setStyle(Paint.Style.STROKE);
            }
            canvas.restoreToCount(save);
            if(!scheduled){ scheduled=true; scheduleSelf(this,SystemClock.uptimeMillis()+40); }
        }
        @Override public void run(){ scheduled=false; invalidateSelf(); }
        @Override public void setAlpha(int alpha){ paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter){ paint.setColorFilter(filter); }
        @Override public int getOpacity(){ return PixelFormat.TRANSLUCENT; }
        @Override public boolean setVisible(boolean visible, boolean restart){
            boolean changed = super.setVisible(visible, restart);
            if(!visible){ unscheduleSelf(this); scheduled=false; }
            else if(restart) start=SystemClock.uptimeMillis();
            return changed;
        }
    }
}
