/* Modifications Copyright (C) 2026 Ettacent */

package app.nimarkogram.messenger.badges;

/** Role labels supplied by the public ExteraGram compact profiles endpoint. */
public enum ExteraBadgeRole {
    DEVELOPER("Разработчик exteraGram"),
    SUPPORTER("Саппортер exteraGram"),
    OFFICIAL_CHANNEL("Официальный канал exteraGram"),
    SUPPORTER_CHANNEL("Канал поддержки exteraGram");

    private final String title;

    ExteraBadgeRole(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
