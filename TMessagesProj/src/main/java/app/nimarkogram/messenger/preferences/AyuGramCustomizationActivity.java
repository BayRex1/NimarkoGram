package app.nimarkogram.messenger.preferences;

import android.content.Context;
import android.view.View;

import app.nimarkogram.messenger.AppConfig;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.messenger.R;
import app.nimarkogram.messenger.preferences.helpers.SettingsHelper;

import java.util.ArrayList;

public class AyuGramCustomizationActivity extends UniversalPreferencesActivity {
    private static final int DELETED_MESSAGES = 1;
    private static final int CUSTOM_STATUS = 2;

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
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == DELETED_MESSAGES) {
            AppConfig.toggleShowDeletedMessages();
            updateCheckState(view, AppConfig.showDeletedMessages);
        } else if (item.id == CUSTOM_STATUS) {
            presentFragment(new CustomStatusPreferencesActivity());
        }
    }

    @Override
    public boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
