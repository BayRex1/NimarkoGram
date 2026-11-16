package app.nimarkogram.messenger.preferences;

import android.content.Context;
import android.view.View;

import app.nimarkogram.messenger.AppConfig;
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
                "Сохраняет сообщения на этом устройстве и показывает их в переписке с отметкой «Удалено».")
                .setChecked(AppConfig.showDeletedMessages));
        items.add(SettingsHelper.asSwitchCG(TRANSLUCENT_DELETED,
                "Полупрозрачные удалёнки",
                "Отображает сохранённые удалённые сообщения полупрозрачными.")
                .setChecked(AppConfig.translucentDeletedMessages));
        items.add(UItem.asButton(DELETED_MARK_STYLE, "Метка удалёнок",
                DELETED_MARKS[Math.max(0, Math.min(2, AppConfig.deletedMessageMarkStyle))]));
        items.add(UItem.asShadow("Сохранённые сообщения остаются на устройстве. Сообщения, удалённые до включения сохранения, восстановить нельзя."));

        items.add(UItem.asHeader("Полезные функции"));
        items.add(SettingsHelper.asSwitchCG(LOCAL_PREMIUM,
                "Локальный Telegram Premium",
                "Локальное отображение Premium-эмодзи без изменения подписки Telegram.")
                .setChecked(AppConfig.localPremiumEmojis));

        items.add(UItem.asHeader("Персонализация"));
        items.add(asPlainSettingsRowWithSubtitle(CUSTOM_STATUS, "Кастомный статус",
                "Назначай Premium Emoji выбранным пользователям. Статусы видны только тебе."));
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == DELETED_MESSAGES) {
            AppConfig.toggleShowDeletedMessages();
            SettingsHelper.updateCheckState(view, AppConfig.showDeletedMessages);
        } else if (item.id == TRANSLUCENT_DELETED) {
            AppConfig.setTranslucentDeletedMessages(!AppConfig.translucentDeletedMessages);
            SettingsHelper.updateCheckState(view, AppConfig.translucentDeletedMessages);
        } else if (item.id == DELETED_MARK_STYLE) {
            AppConfig.setDeletedMessageMarkStyle((AppConfig.deletedMessageMarkStyle + 1) % DELETED_MARKS.length);
            SettingsHelper.updateButtonValue(view, DELETED_MARKS[AppConfig.deletedMessageMarkStyle]);
        } else if (item.id == LOCAL_PREMIUM) {
            AppConfig.toggleLocalPremiumEmojis();
            SettingsHelper.updateCheckState(view, AppConfig.localPremiumEmojis);
        } else if (item.id == CUSTOM_STATUS) {
            presentFragment(new CustomStatusPreferencesActivity());
        }
    }

    @Override
    public boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
