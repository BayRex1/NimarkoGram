package app.nimarkogram.messenger.preferences;

import android.content.Context;
import android.view.View;

import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.IconBackgroundColors;

import java.util.ArrayList;

public class AyuGramPreferencesActivity extends UniversalPreferencesActivity {
    private static final int GHOST = 1;
    private static final int CUSTOMIZATION = 2;

    @Override
    protected CharSequence getTitle() {
        return "Настройки AyuGram";
    }

    @Override
    public View createView(Context context) {
        setMD3(true);
        return super.createView(context);
    }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(asSettingsLink(99, IconBackgroundColors.PURPLE,
                org.telegram.messenger.R.drawable.ayu_logo, "AyuGram", "2026.11.04"));
        items.add(asSettingsLink(GHOST, IconBackgroundColors.PURPLE,
                org.telegram.messenger.R.drawable.ghost, "Режим Призрака",
                "Настройки приватности и скрытия активности"));
        items.add(asSettingsLink(CUSTOMIZATION, IconBackgroundColors.BLUE,
                org.telegram.messenger.R.drawable.msg_customize, "Кастомизация",
                "Удалённые сообщения и персональные статусы"));
        items.add(UItem.asShadow("Версия функций AyuGram: 2026.11.04"));
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == GHOST) {
            presentFragment(ChatsPreferencesActivity.forAyuGram());
        } else if (item.id == CUSTOMIZATION) {
            presentFragment(new AyuGramCustomizationActivity());
        }
    }
}
