package app.nimarkogram.messenger.preferences;

import android.content.Context;
import android.view.View;

import app.nimarkogram.messenger.AppConfig;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.messenger.R;
import app.nimarkogram.messenger.preferences.helpers.SettingsHelper;

import java.util.ArrayList;

public class AyuGramCustomizationActivity extends UniversalPreferencesActivity {
    private static final int DELETED_MESSAGES = 1;
    private static final int CUSTOM_STATUS = 2;
    private static final int LOCAL_PREMIUM = 3;
    private static final int KEEP_ALIVE = 4;

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
        items.add(UItem.asHeader("Персонализация"));
        items.add(SettingsHelper.asSwitchCG(DELETED_MESSAGES,
                "Сохранять удалённые сообщения",
                "Сохраняет сообщения на этом устройстве и показывает их в переписке с отметкой «Удалено».")
                .setChecked(AppConfig.showDeletedMessages));
        items.add(UItem.asShadow("Сохранённые сообщения остаются в чате после повторного входа. Сообщения, удалённые до включения функции, восстановить нельзя."));
        items.add(asPlainSettingsRowWithSubtitle(CUSTOM_STATUS, "Кастомный статус",
                "Назначай Premium Emoji выбранным пользователям. Статусы видны только тебе."));
        items.add(SettingsHelper.asSwitchCG(LOCAL_PREMIUM,
                "Локальный Telegram Premium",
                "Включает клиентские Premium-функции только на этом устройстве. Серверные ограничения не снимает.")
                .setChecked(AppConfig.localPremium));
        items.add(UItem.asShadow("Не рекомендуется включать при активной подписке Telegram Premium: возможны конфликты функций."));
        items.add(SettingsHelper.asSwitchCG(KEEP_ALIVE,
                "Работать в фоне",
                "Разрешает фоновую задачу для обработки push-уведомлений. Отключение может задерживать их обработку.")
                .setChecked(AppConfig.keepAliveService));
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == DELETED_MESSAGES) {
            AppConfig.toggleShowDeletedMessages();
            updateCheckState(view, AppConfig.showDeletedMessages);
        } else if (item.id == CUSTOM_STATUS) {
            presentFragment(new CustomStatusPreferencesActivity());
        } else if (item.id == LOCAL_PREMIUM) {
            AppConfig.toggleLocalPremium();
            UserConfig userConfig = UserConfig.getInstance(UserConfig.selectedAccount);
            if (userConfig.getCurrentUser() != null) {
                org.telegram.messenger.AccountInstance.getInstance(UserConfig.selectedAccount).getMessagesController().updatePremium(userConfig.isPremium());
                NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.currentUserPremiumStatusChanged);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.premiumStatusChangedGlobal);
                org.telegram.messenger.AccountInstance.getInstance(UserConfig.selectedAccount).getMediaDataController().loadPremiumPromo(false);
                org.telegram.messenger.AccountInstance.getInstance(UserConfig.selectedAccount).getMediaDataController().loadReactions(false, null);
            }
            SettingsHelper.updateCheckState(view, AppConfig.localPremium);
        } else if (item.id == KEEP_ALIVE) {
            AppConfig.toggleKeepAliveService();
            SettingsHelper.updateCheckState(view, AppConfig.keepAliveService);
        }
    }

    @Override
    public boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
