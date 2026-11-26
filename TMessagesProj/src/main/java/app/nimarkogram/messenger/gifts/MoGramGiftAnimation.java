package app.nimarkogram.messenger.gifts;

import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLoader;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;

import java.io.File;

/** Loads the exact catalog TGS animation into Telegram's native RLottie renderer. */
public final class MoGramGiftAnimation {
    private MoGramGiftAnimation() {
    }

    public static void bind(Context context, RLottieImageView image, String id, String fileUrl, int sizeDp) {
        if (image == null || fileUrl == null || fileUrl.length() == 0) return;
        final String key = "mogram-tgs:" + id + ":" + fileUrl;
        Object current = image.getTag();
        if (key.equals(current)) return;
        image.setTag(key);
        image.setAutoRepeat(true);
        new Thread(() -> {
            try {
                File file = MoGramGiftsApi.ensureFile(context.getApplicationContext(), id, fileUrl);
                String json = ImageLoader.decompressGzip(file);
                final int size = AndroidUtilities.dp(sizeDp);
                RLottieDrawable drawable = new RLottieDrawable(file, json, size, size, null, true, null, 0, false);
                AndroidUtilities.runOnUIThread(() -> {
                    if (!key.equals(image.getTag())) return;
                    image.setAnimation(drawable);
                    image.playAnimation();
                });
            } catch (Exception e) {
                // Keep the card usable if a remote animation is temporarily unavailable.
            }
        }, "MoGramTgs-" + id).start();
    }
}