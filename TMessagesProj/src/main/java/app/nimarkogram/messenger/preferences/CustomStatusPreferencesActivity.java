/* Modifications Copyright (C) 2026 Ettacent */

package app.nimarkogram.messenger.preferences;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.SelectAnimatedEmojiDialog;

import app.nimarkogram.messenger.badges.CustomStatusController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.telegram.messenger.AndroidUtilities.dp;

/** Local custom Premium Emoji statuses assigned to Telegram users. */
public class CustomStatusPreferencesActivity extends UniversalPreferencesActivity {
    private static final int ADD_ROW = 1;
    private static final int USER_ROW_BASE = 10_000;

    private final Map<Integer, CustomStatusController.Entry> rows = new HashMap<>();
    private long pendingUserId;
    private String pendingDisplayName;
    private String pendingUsername;
    private SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow emojiWindow;

    @Override
    protected CharSequence getTitle() {
        return "Кастомный статус";
    }

    @Override
    public View createView(Context context) {
        setMD3(true);
        return super.createView(context);
    }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        rows.clear();
        items.add(UItem.asHeader("Пользователи"));
        items.add(asPlainSettingsRow(ADD_ROW, "Добавить пользователя", "По @username или ID"));

        List<CustomStatusController.Entry> entries = CustomStatusController.getAll(currentAccount);
        int index = 0;
        for (CustomStatusController.Entry entry : entries) {
            int id = USER_ROW_BASE + index++;
            rows.put(id, entry);
            String value = !TextUtils.isEmpty(entry.username)
                    ? "@" + entry.username + "  ·  ID " + entry.userId
                    : "ID " + entry.userId;
            items.add(asPlainSettingsRow(id,
                    TextUtils.isEmpty(entry.displayName) ? "Пользователь " + entry.userId : entry.displayName,
                    value));
        }
        if (entries.isEmpty()) {
            items.add(UItem.asShadow("Пока нет пользователей с назначенными статусами. Нажми «Добавить пользователя», чтобы назначить Premium Emoji."));
        } else {
            items.add(UItem.asShadow("Нажми на пользователя, чтобы изменить его статус или удалить назначение."));
        }
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ADD_ROW) {
            showAddUserDialog();
            return;
        }
        CustomStatusController.Entry entry = rows.get(item.id);
        if (entry != null) showEntryActions(entry);
    }

    private void showAddUserDialog() {
        Activity activity = getParentActivity();
        if (activity == null) return;

        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("@username или числовой ID");
        input.setPadding(dp(20), dp(12), dp(20), dp(12));

        new AlertDialog.Builder(activity)
                .setTitle("Добавить пользователя")
                .setMessage("Введи username или ID пользователя Telegram.")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Продолжить", (dialog, which) -> resolveUser(input.getText().toString()))
                .show();
    }

    private void resolveUser(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.startsWith("@")) value = value.substring(1);
        if (TextUtils.isEmpty(value)) {
            showToast("Введи username или ID");
            return;
        }

        if (value.matches("[0-9]+")) {
            try {
                long userId = Long.parseLong(value);
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userId);
                if (user == null) {
                    showToast("Пользователь с таким ID пока не загружен. Попробуй найти его по @username.");
                    return;
                }
                openEmojiPicker(user);
            } catch (NumberFormatException e) {
                showToast("Некорректный ID пользователя");
            }
            return;
        }

        TLRPC.TL_contacts_resolveUsername request = new TLRPC.TL_contacts_resolveUsername();
        request.username = value;
        ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) ->
                AndroidUtilities.runOnUIThread(() -> {
                    if (error != null || !(response instanceof TLRPC.TL_contacts_resolvedPeer)) {
                        showToast("Не удалось найти пользователя. Проверь username.");
                        return;
                    }
                    TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) response;
                    MessagesController.getInstance(currentAccount).putUsers(resolved.users, false);
                    long userId = resolved.peer == null ? 0L : resolved.peer.user_id;
                    TLRPC.User user = userId == 0L
                            ? null : MessagesController.getInstance(currentAccount).getUser(userId);
                    if (user == null) {
                        showToast("Этот username не принадлежит пользователю.");
                        return;
                    }
                    openEmojiPicker(user);
                }));
    }

    private void openEmojiPicker(TLRPC.User user) {
        if (user == null || getParentActivity() == null) return;
        pendingUserId = user.id;
        pendingDisplayName = UserObject.getUserName(user);
        pendingUsername = UserObject.getPublicUsername(user);

        View anchor = getParentActivity().getWindow().getDecorView();
        SelectAnimatedEmojiDialog selector = new SelectAnimatedEmojiDialog(
                this, getParentActivity(), false, null,
                SelectAnimatedEmojiDialog.TYPE_EMOJI_STATUS, null) {
            @Override
            protected void onEmojiSelected(View view, Long documentId, TLRPC.Document document,
                                            org.telegram.tgnet.tl.TL_stars.TL_starGiftUnique gift,
                                            Integer until) {
                if (documentId == null || documentId == 0L) {
                    showToast("Выбери Premium Emoji");
                    return;
                }
                CustomStatusController.put(currentAccount, pendingUserId,
                        pendingDisplayName, pendingUsername, documentId);
                updateItemsAfterToggle();
                if (emojiWindow != null) {
                    emojiWindow.dismiss();
                    emojiWindow = null;
                }
                showToast("Кастомный статус сохранён");
            }
        };

        emojiWindow = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(
                anchor, ViewGroup.LayoutParams.MATCH_PARENT, dp(430));
        emojiWindow.setContentView(selector);
        emojiWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        emojiWindow.setOutsideTouchable(true);
        emojiWindow.showAtLocation(anchor, Gravity.CENTER, 0, 0);
    }

    private void showEntryActions(CustomStatusController.Entry entry) {
        Activity activity = getParentActivity();
        if (activity == null) return;
        String name = TextUtils.isEmpty(entry.displayName)
                ? "Пользователь " + entry.userId : entry.displayName;
        new AlertDialog.Builder(activity)
                .setTitle(name)
                .setItems(new CharSequence[]{"Изменить статус", "Удалить пользователя"}, (dialog, which) -> {
                    if (which == 0) {
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(entry.userId);
                        if (user == null) {
                            pendingUserId = entry.userId;
                            pendingDisplayName = entry.displayName;
                            pendingUsername = entry.username;
                            chooseEmojiForPendingUser();
                        } else {
                            openEmojiPicker(user);
                        }
                    } else if (which == 1) {
                        confirmRemove(entry);
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void chooseEmojiForPendingUser() {
        if (getParentActivity() == null) return;
        View anchor = getParentActivity().getWindow().getDecorView();
        SelectAnimatedEmojiDialog selector = new SelectAnimatedEmojiDialog(
                this, getParentActivity(), false, null,
                SelectAnimatedEmojiDialog.TYPE_EMOJI_STATUS, null) {
            @Override
            protected void onEmojiSelected(View view, Long documentId, TLRPC.Document document,
                                            org.telegram.tgnet.tl.TL_stars.TL_starGiftUnique gift,
                                            Integer until) {
                if (documentId == null || documentId == 0L) return;
                CustomStatusController.put(currentAccount, pendingUserId,
                        pendingDisplayName, pendingUsername, documentId);
                updateItemsAfterToggle();
                if (emojiWindow != null) {
                    emojiWindow.dismiss();
                    emojiWindow = null;
                }
                showToast("Кастомный статус обновлён");
            }
        };
        emojiWindow = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(
                anchor, ViewGroup.LayoutParams.MATCH_PARENT, dp(430));
        emojiWindow.setContentView(selector);
        emojiWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        emojiWindow.showAtLocation(anchor, Gravity.CENTER, 0, 0);
    }

    private void confirmRemove(CustomStatusController.Entry entry) {
        Activity activity = getParentActivity();
        if (activity == null) return;
        new AlertDialog.Builder(activity)
                .setTitle("Удалить кастомный статус?")
                .setMessage("Назначенный эмодзи перестанет отображаться рядом с именем этого пользователя.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (dialog, which) -> {
                    CustomStatusController.remove(currentAccount, entry.userId);
                    updateItemsAfterToggle();
                })
                .show();
    }

    private void showToast(String message) {
        Activity activity = getParentActivity();
        if (activity != null) Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFragmentDestroy() {
        if (emojiWindow != null) {
            try {
                emojiWindow.dismiss();
            } catch (Throwable ignored) {}
            emojiWindow = null;
        }
        super.onFragmentDestroy();
    }
}
