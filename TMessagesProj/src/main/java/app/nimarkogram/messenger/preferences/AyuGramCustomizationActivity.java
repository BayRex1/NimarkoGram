package app.nimarkogram.messenger.preferences;

import android.content.Context;
import android.view.View;

import app.nimarkogram.messenger.AppConfig;
import app.nimarkogram.messenger.ayugram.AyuConfig;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import app.nimarkogram.messenger.preferences.helpers.SettingsHelper;

import java.util.ArrayList;

public class AyuGramCustomizationActivity extends UniversalPreferencesActivity {
    private static final int DELETED_MESSAGES = 1;
    private static final int CUSTOM_STATUS = 2;
    private static final int TRANSLUCENT_DELETED = 3;
    private static final int DELETED_MARK_STYLE = 4;
    private static final int LOCAL_PREMIUM = 5;
    private static final int KEEP_ALIVE = 6;

    private static final String[] DELETED_MARKS = {"Корзина", "Крестик", "Глаз"};

    @Override
    protected CharSequence getTitle() {
        return "Кастомизация";
    }

    @Override
    public View createView(Context context) {
        setMD3(true);
        return super.createView(context);
    }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("Удалённые сообщения"));
        items.add(SettingsHelper.asSwitchCG(DELETED_MESSAGES,
                "Сохранять удалённые сообщения",
                "Сохраняет на устройстве сообщения, которые уже были загружены в переписку.")
                .setChecked(AyuConfig.saveDeletedMessages));
        items.add(SettingsHelper.asSwitchCG(TRANSLUCENT_DELETED,
                "Полупрозрачные удалёнки",
                "Отображает сохранённые удалённые сообщения полупрозрачными.")
                .setChecked(AppConfig.translucentDeletedMessages));
        items.add(UItem.asButton(DELETED_MARK_STYLE, "Метка удалёнок",
                getDeletedMarkLabel()));
        items.add(UItem.asShadow("Сохранённые сообщения остаются на устройстве. Сообщения, удалённые до включения сохранения, восстановить нельзя."));

        items.add(UItem.asHeader("Полезные функции"));
        items.add(SettingsHelper.asSwitchCG(LOCAL_PREMIUM,
                "Локальный Telegram Premium",
                "Локальное отображение Premium-эмодзи без изменения подписки Telegram.")
                .setChecked(AyuConfig.localPremium));
        items.add(SettingsHelper.asSwitchCG(KEEP_ALIVE,
                "Работать в фоне",
                "Разрешает фоновому заданию поддерживать активность сетевых запросов.")
                .setChecked(AyuConfig.keepAliveService));

        items.add(UItem.asHeader("Персонализация"));
        items.add(asPlainSettingsRowWithSubtitle(CUSTOM_STATUS, "Кастомный статус",
                "Назначай Premium Emoji выбранным пользователям. Статусы видны только тебе."));
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == DELETED_MESSAGES) {
            AyuConfig.editor.putBoolean("saveDeletedMessages", AyuConfig.saveDeletedMessages = !AyuConfig.saveDeletedMessages).apply();
            SettingsHelper.updateCheckState(view, AyuConfig.saveDeletedMessages);
        } else if (item.id == TRANSLUCENT_DELETED) {
            AppConfig.setTranslucentDeletedMessages(!AppConfig.translucentDeletedMessages);
            SettingsHelper.updateCheckState(view, AppConfig.translucentDeletedMessages);
        } else if (item.id == DELETED_MARK_STYLE) {
            String[] marks = {"🗑️", "✕", "◉"};
            int next = (getDeletedMarkIndex() + 1) % marks.length;
            AyuConfig.editor.putString("deletedMarkText", marks[next]).apply();
            AppConfig.setDeletedMessageMarkStyle(next);
            SettingsHelper.updateButtonValue(view, DELETED_MARKS[next]);
        } else if (item.id == LOCAL_PREMIUM) {
            AyuConfig.editor.putBoolean("localPremium", AyuConfig.localPremium = !AyuConfig.localPremium).apply();
            getMessagesController().updatePremium(AyuConfig.localPremium);
            SettingsHelper.updateCheckState(view, AyuConfig.localPremium);
        } else if (item.id == KEEP_ALIVE) {
            AyuConfig.editor.putBoolean("keepAliveService", AyuConfig.keepAliveService = !AyuConfig.keepAliveService).apply();
            SettingsHelper.updateCheckState(view, AyuConfig.keepAliveService);
        } else if (item.id == CUSTOM_STATUS) {
            presentFragment(new CustomStatusPreferencesActivity());
        }
    }

    private String getDeletedMarkLabel() {
        return DELETED_MARKS[getDeletedMarkIndex()];
    }

    private int getDeletedMarkIndex() {
        return Math.max(0, Math.min(2, AppConfig.deletedMessageMarkStyle));
    }

    @Override
    public boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
