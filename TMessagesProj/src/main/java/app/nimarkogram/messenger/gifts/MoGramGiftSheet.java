package app.nimarkogram.messenger.gifts;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;

import java.io.File;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * Bottom sheet opened from a user's profile: sends a catalog gift to that user
 * and lists recent gifts received by them, with sender and recipient names.
 */
public class MoGramGiftSheet extends BottomSheet {

    private final Context ctx;
    private final int currentAccount;
    private final long targetUserId;
    private final LinearLayout list;
    private final LinearLayout catalogList;
    private final LinearLayout historyList;
    private final Set<String> sendingGiftIds = new HashSet<>();

    public MoGramGiftSheet(Context context, int account, long userId) {
        super(context, false);
        this.ctx = context;
        this.currentAccount = account;
        this.targetUserId = userId;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        root.addView(makeText(LocaleController.getString(R.string.NM_MogramGifts), 18, true));

        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        catalogList = new LinearLayout(context);
        catalogList.setOrientation(LinearLayout.VERTICAL);
        historyList = new LinearLayout(context);
        historyList.setOrientation(LinearLayout.VERTICAL);
        list.addView(catalogList);
        list.addView(historyList);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(460)));

        setCustomView(root);
        load();
    }

    private long myId() {
        return UserConfig.getInstance(currentAccount).getClientUserId();
    }

    private void load() {
        catalogList.removeAllViews();
        historyList.removeAllViews();
        addSection(catalogList, LocaleController.getString(R.string.NM_MogramGiftsSend));
        new Thread(() -> {
            try {
                List<MoGramGiftsApi.Gift> gifts = MoGramGiftsApi.fetchCatalog();
                AndroidUtilities.runOnUIThread(() -> renderCatalog(gifts));
            } catch (Exception e) {
                AndroidUtilities.runOnUIThread(() -> addInfo(catalogList, LocaleController.getString(R.string.NM_MogramGiftsError)));
            }
        }).start();

        addSection(historyList, LocaleController.getString(R.string.NM_MogramGiftsHistory));
        new Thread(() -> {
            try {
                List<MoGramGiftsApi.Transfer> transfers = MoGramGiftsApi.fetchReceived(targetUserId);
                AndroidUtilities.runOnUIThread(() -> renderHistory(transfers));
            } catch (Exception e) {
                AndroidUtilities.runOnUIThread(() -> addInfo(historyList, LocaleController.getString(R.string.NM_MogramGiftsError)));
            }
        }).start();
    }

    private void renderCatalog(List<MoGramGiftsApi.Gift> gifts) {
        if (gifts.isEmpty()) {
            addInfo(catalogList, LocaleController.getString(R.string.NM_MogramGiftsEmpty));
            return;
        }
        for (MoGramGiftsApi.Gift gift : gifts) {
            catalogList.addView(giftRow(gift));
        }
    }

    private void renderHistory(List<MoGramGiftsApi.Transfer> transfers) {
        if (transfers.isEmpty()) {
            addInfo(historyList, LocaleController.getString(R.string.NM_MogramGiftsEmpty));
            return;
        }
        for (MoGramGiftsApi.Transfer t : transfers) {
            String from = displayName(t.fromId);
            String to = displayName(t.toId);
            TextView historyRow = makeText(from + " → " + to + ": " + t.giftTitle, 15, false);
            historyRow.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
            historyList.addView(historyRow);
        }
    }

    private String displayName(String id) {
        try {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(Long.parseLong(id));
            if (user != null) {
                return UserObject.getUserName(user);
            }
        } catch (NumberFormatException ignored) {
        }
        return id;
    }

    private View giftRow(MoGramGiftsApi.Gift gift) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));
        row.setClickable(true);

        RLottieImageView icon = new RLottieImageView(ctx);
        icon.setAutoRepeat(true);
        row.addView(icon, new LinearLayout.LayoutParams(AndroidUtilities.dp(56), AndroidUtilities.dp(56)));

        TextView title = makeText(gift.title + "  ·  " + gift.priceStars + " ★", 16, false);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = AndroidUtilities.dp(12);
        row.addView(title, tp);

        row.setOnClickListener(v -> {
            if (!sendingGiftIds.contains(gift.id)) send(gift, row);
        });

        new Thread(() -> {
            try {
                File file = MoGramGiftsApi.ensureFile(ctx, gift);
                String json = ImageLoader.decompressGzip(file);
                int size = AndroidUtilities.dp(56);
                RLottieDrawable drawable = new RLottieDrawable(file, json, size, size, null, true, null, 0, false);
                AndroidUtilities.runOnUIThread(() -> {
                    icon.setAnimation(drawable);
                    icon.playAnimation();
                });
            } catch (Exception ignored) {
                // Without the animation the row still works as a text button.
            }
        }).start();
        return row;
    }

    private void send(MoGramGiftsApi.Gift gift, View giftRow) {
        final long from = myId();
        synchronized (sendingGiftIds) {
            if (!sendingGiftIds.add(gift.id)) return;
        }
        giftRow.setAlpha(0.65f);
        giftRow.setScaleX(0.97f);
        giftRow.setScaleY(0.97f);
        new Thread(() -> {
            try {
                MoGramGiftsApi.Transfer transfer = MoGramGiftsApi.send(from, gift.id, targetUserId);
                AndroidUtilities.runOnUIThread(() -> {
                    sendingGiftIds.remove(gift.id);
                    giftRow.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start();
                    giftRow.animate().scaleX(1.04f).scaleY(1.04f).setDuration(130).withEndAction(() -> giftRow.animate().scaleX(1f).scaleY(1f).setDuration(180).start()).start();
                    toast(LocaleController.getString(R.string.NM_MogramGiftsSent));
                    load();
                });
            } catch (MoGramGiftsApi.ApiException e) {
                final int msg = "daily_send_limit".equals(e.getMessage())
                        ? R.string.NM_MogramGiftsLimit
                        : R.string.NM_MogramGiftsError;
                AndroidUtilities.runOnUIThread(() -> {
                    sendingGiftIds.remove(gift.id);
                    giftRow.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start();
                    toast(LocaleController.getString(msg));
                });
            } catch (Exception e) {
                AndroidUtilities.runOnUIThread(() -> {
                    sendingGiftIds.remove(gift.id);
                    giftRow.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start();
                    toast(LocaleController.getString(R.string.NM_MogramGiftsError));
                });
            }
        }).start();
    }

    private void toast(String text) {
        Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show();
    }

    private void addSection(LinearLayout target, String title) {
        TextView t = makeText(title, 13, true);
        t.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(4));
        t.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        target.addView(t);
    }

    private void addInfo(LinearLayout target, String text) {
        TextView t = makeText(text, 14, false);
        t.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        target.addView(t);
    }

    private TextView makeText(String text, int sizeSp, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        if (bold) {
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        t.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        return t;
    }
}
