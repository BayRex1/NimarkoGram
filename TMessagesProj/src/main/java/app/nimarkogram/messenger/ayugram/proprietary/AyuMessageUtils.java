package app.nimarkogram.messenger.ayugram.proprietary;

import android.text.TextUtils;
import app.nimarkogram.messenger.ayugram.database.entities.AyuMessageBase;
import app.nimarkogram.messenger.ayugram.database.entities.EditedMessage;
import app.nimarkogram.messenger.ayugram.messages.AyuSavePreferences;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.io.File;

public class AyuMessageUtils {

    public static void map(AyuSavePreferences prefs, AyuMessageBase entity) {
        TLRPC.Message msg = prefs.getMessage();
        if (msg == null) return;
        entity.userId = prefs.getUserId();
        entity.dialogId = prefs.getDialogId();
        entity.topicId = prefs.getTopicId();
        entity.messageId = msg.id;
        entity.date = msg.date;
        entity.editDate = msg.edit_date;
        entity.views = msg.views;
        entity.flags = msg.flags;
        entity.groupedId = msg.grouped_id;

        if (msg.peer_id != null) {
            entity.peerId = MessageObject.getPeerId(msg.peer_id);
        }
        if (msg.from_id != null) {
            entity.fromId = MessageObject.getPeerId(msg.from_id);
        }
        if (msg.fwd_from != null) {
            entity.fwdFlags = msg.fwd_from.flags;
            if (msg.fwd_from.from_id != null) {
                entity.fwdFromId = MessageObject.getPeerId(msg.fwd_from.from_id);
            }
            entity.fwdName = msg.fwd_from.from_name;
            entity.fwdDate = msg.fwd_from.date;
            entity.fwdPostAuthor = msg.fwd_from.post_author;
        }
        if (msg.reply_to != null) {
            entity.replyFlags = msg.reply_to.flags;
            entity.replyMessageId = msg.reply_to.reply_to_msg_id;
            if (msg.reply_to.reply_to_peer_id != null) {
                entity.replyPeerId = MessageObject.getPeerId(msg.reply_to.reply_to_peer_id);
            }
            entity.replyTopId = msg.reply_to.reply_to_top_id;
            entity.replyForumTopic = msg.reply_to.forum_topic;
        }

        entity.text = msg.message;
        if (msg.entities != null && !msg.entities.isEmpty()) {
            try {
                SerializedData data = new SerializedData();
                data.writeInt32(msg.entities.size());
                for (int a = 0; a < msg.entities.size(); a++) {
                    msg.entities.get(a).serializeToStream(data);
                }
                entity.textEntities = data.toByteArray();
                data.cleanup();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public static void mapMedia(AyuSavePreferences prefs, AyuMessageBase entity, boolean copyMedia) {
        TLRPC.Message msg = prefs.getMessage();
        if (msg == null || msg.media == null) return;

        if (msg.media instanceof TLRPC.TL_messageMediaPhoto && msg.media.photo != null) {
            entity.documentType = MessageObject.TYPE_PHOTO;
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(msg.media.photo.sizes, 1280);
            if (size != null) {
                File path = FileLoader.getInstance(prefs.getAccountId()).getPathToAttach(size, true);
                if (path != null && path.exists()) {
                    entity.mediaPath = path.getAbsolutePath();
                }
            }
        } else if (msg.media instanceof TLRPC.TL_messageMediaDocument && msg.media.document != null) {
            TLRPC.Document document = msg.media.document;
            entity.mimeType = document.mime_type;
            if (MessageObject.isVoiceDocument(document)) {
                entity.documentType = MessageObject.TYPE_VOICE;
            } else if (MessageObject.isVideoDocument(document)) {
                entity.documentType = MessageObject.TYPE_VIDEO;
            } else if (MessageObject.isStickerDocument(document)) {
                entity.documentType = MessageObject.TYPE_STICKER;
            } else {
                entity.documentType = 1;
            }
            File path = FileLoader.getInstance(prefs.getAccountId()).getPathToAttach(document, true);
            if (path != null && path.exists()) {
                entity.mediaPath = path.getAbsolutePath();
            }
        }
    }

    public static void map(EditedMessage editedMessage, MessageObject msg, int currentAccount) {
        if (editedMessage == null || msg == null) return;
        msg.messageOwner.id = editedMessage.messageId;
        msg.messageOwner.date = editedMessage.date;
        msg.messageOwner.edit_date = editedMessage.editDate;
        msg.messageOwner.message = editedMessage.text;
        msg.messageText = editedMessage.text;
    }

    public static void mapMedia(EditedMessage editedMessage, MessageObject msg) {
        if (editedMessage == null || msg == null) return;
        if (!TextUtils.isEmpty(editedMessage.mediaPath)) {
            msg.messageOwner.attachPath = editedMessage.mediaPath;
        }
    }

    public static void map(EditedMessage editedMessage, TLRPC.Message msg, int currentAccount) {
        if (editedMessage == null || msg == null) return;
        msg.id = editedMessage.messageId;
        msg.date = editedMessage.date;
        msg.edit_date = editedMessage.editDate;
        msg.message = editedMessage.text;
    }

    public static void mapMedia(EditedMessage editedMessage, TLRPC.Message msg) {
        if (editedMessage == null || msg == null) return;
        if (!TextUtils.isEmpty(editedMessage.mediaPath)) {
            msg.attachPath = editedMessage.mediaPath;
        }
    }
}
