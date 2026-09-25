package org.telegram.messenger;

public class MessageObject {
    private final long dialogId;

    public MessageObject(long dialogId) {
        this.dialogId = dialogId;
    }

    public long getDialogId() {
        return dialogId;
    }
}
