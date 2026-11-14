package app.nimarkogram.messenger.preferences;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.BuildVars;
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
        items.add(UItem.asHeader("AyuGram"));
        items.add(asSettingsLink(GHOST, IconBackgroundColors.PURPLE,
                org.telegram.messenger.R.drawable.ghost, "Режим Призрака",
                "Настройки приватности и скрытия активности"));
        items.add(asSettingsLink(CUSTOMIZATION, IconBackgroundColors.BLUE,
                org.telegram.messenger.R.drawable.msg_customize, "Кастомизация",
                "Удалённые сообщения и персональные статусы"));
        items.add(UItem.asShadow("AyuGram · " + BuildVars.BUILD_VERSION_STRING));
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
