/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package app.nimarkogram.messenger.ayugram.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import app.nimarkogram.messenger.ayugram.database.dao.DeletedMessageDao;
import app.nimarkogram.messenger.ayugram.database.dao.EditedMessageDao;
import app.nimarkogram.messenger.ayugram.database.entities.DeletedMessage;
import app.nimarkogram.messenger.ayugram.database.entities.DeletedMessageReaction;
import app.nimarkogram.messenger.ayugram.database.entities.EditedMessage;

@Database(entities = {
        EditedMessage.class,
        DeletedMessage.class,
        DeletedMessageReaction.class
}, version = 21)
public abstract class AyuDatabase extends RoomDatabase {
    public abstract EditedMessageDao editedMessageDao();

    public abstract DeletedMessageDao deletedMessageDao();
}