package app.nimarkogram.messenger.preferences;

import android.content.Context;
import android.view.View;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

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

    private View createAyuGramHeader(Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, AndroidUtilities.dp(18), 0, AndroidUtilities.dp(14));

        ImageView logo = new ImageView(context);
        logo.setImageResource(org.telegram.messenger.R.drawable.AyuGram);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        header.addView(logo, new LinearLayout.LayoutParams(AndroidUtilities.dp(88), AndroidUtilities.dp(88)));

        TextView title = new TextView(context);
        title.setText("AyuGram");
        title.setTextSize(22);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = AndroidUtilities.dp(8);
        header.addView(title, titleParams);

        TextView version = new TextView(context);
        version.setText("Версия 2026.11.04");
        version.setTextSize(13);
        version.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        version.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        versionParams.topMargin = AndroidUtilities.dp(3);
        header.addView(version, versionParams);
        return header;
    }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(createAyuGramHeader(getContext()), 164));
        items.add(asSettingsLink(GHOST, IconBackgroundColors.PURPLE,
                org.telegram.messenger.R.drawable.ghost, "Режим Призрака",
                "Настройки приватности и скрытия активности"));
        items.add(asSettingsLink(10, IconBackgroundColors.BLUE,
                org.telegram.messenger.R.drawable.msg_settings, "Шпион",
                "Раздел будет добавлен позже"));
        items.add(asSettingsLink(11, IconBackgroundColors.CYAN,
                org.telegram.messenger.R.drawable.msg_settings, "Фильтры",
                "Раздел будет добавлен позже"));
        items.add(asSettingsLink(CUSTOMIZATION, IconBackgroundColors.BLUE,
                org.telegram.messenger.R.drawable.msg_customize, "Кастомизация",
                "Удалённые сообщения и персональные статусы"));
        items.add(UItem.asHeader("Ссылки"));
        items.add(UItem.asButton(20, "Канал", "@ayugram"));
        items.add(UItem.asButton(21, "Чаты", "@ayugramchat"));
        items.add(UItem.asButton(22, "Перевод", "Crowdin"));
        items.add(UItem.asButton(23, "Документация", "ayugram.one"));
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == GHOST) {
            presentFragment(ChatsPreferencesActivity.forAyuGram());
        } else if (item.id == CUSTOMIZATION) {
            presentFragment(new AyuGramCustomizationActivity());
        }
    }

    @Override
    public boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
