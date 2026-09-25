package io.github.cepeter.royalty.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import io.github.cepeter.royalty.core.DialogKey;
import org.junit.Test;

public final class TelegramObjectKeyTest {
    private static final class Dialog {
        private final long id;
        Dialog(long id) { this.id = id; }
    }

    private static final class Message {
        private final long dialogId;
        Message(long dialogId) { this.dialogId = dialogId; }
        private long getDialogId() { return dialogId; }
    }

    private static final class User {
        private final long id;
        User(long id) { this.id = id; }
    }

    private static final class Chat {
        private final long id;
        Chat(long id) { this.id = id; }
    }

    private static final class Contact {
        private final long user_id;
        Contact(long userId) { this.user_id = userId; }
    }

    @Test
    public void extractsKnownObjectsUsingExplicitAccount() {
        assertEquals(DialogKey.of(2, -4), TelegramObjectKey.fromDialog(2, new Dialog(-4)).get());
        assertEquals(DialogKey.of(1, 9), TelegramObjectKey.fromMessage(1, new Message(9)).get());
        assertEquals(DialogKey.of(3, 8), TelegramObjectKey.fromUser(3, new User(8)).get());
        assertEquals(DialogKey.of(3, -8), TelegramObjectKey.fromChat(3, new Chat(8)).get());
    }

    @Test
    public void extractsOnlyVerifiedRecentAndShareWrapperTypes() {
        assertEquals(DialogKey.of(2, -22), TelegramObjectKey.fromRecent(2, new we.a0(-22)).get());
        assertEquals(
                DialogKey.of(0, -23),
                TelegramObjectKey.fromShareSearch(
                        0,
                        new org.telegram.ui.Components.kq0(
                                new org.telegram.tgnet.TLRPC.Dialog(-23), null)).get());
        assertEquals(
                DialogKey.of(1, 24),
                TelegramObjectKey.fromShareSearch(
                        1,
                        new org.telegram.ui.Components.kq0(
                                null, new org.telegram.tgnet.TLRPC.User(24))).get());
        assertEquals(
                DialogKey.of(1, -25),
                TelegramObjectKey.fromShareSearch(
                        1,
                        new org.telegram.ui.Components.kq0(
                                null, new org.telegram.tgnet.TLRPC.Chat(25))).get());
    }

    @Test
    public void classifiesMixedSearchRowsByVerifiedFullyQualifiedType() {
        assertEquals(
                DialogKey.of(0, 31),
                TelegramObjectKey.fromSearchResult(
                        0, new org.telegram.tgnet.TLRPC.User(31)).get());
        assertEquals(
                DialogKey.of(0, -32),
                TelegramObjectKey.fromSearchResult(
                        0, new org.telegram.tgnet.TLRPC.Chat(32)).get());
        assertEquals(
                DialogKey.of(0, -33),
                TelegramObjectKey.fromSearchResult(
                        0, new org.telegram.tgnet.TLRPC.Dialog(-33)).get());
        assertEquals(
                DialogKey.of(0, 34),
                TelegramObjectKey.fromSearchResult(
                        0, new org.telegram.messenger.MessageObject(34)).get());
        assertEquals(
                DialogKey.of(0, 35),
                TelegramObjectKey.fromSearchResult(0, new we.a0(35)).get());
        assertEquals(
                DialogKey.of(2, 36),
                TelegramObjectKey.fromPickerResult(2, new Contact(36)).get());
    }

    @Test
    public void lookalikeTypesAndInvalidObjectsFailOpen() {
        assertFalse(TelegramObjectKey.fromSearchResult(0, new User(31)).isPresent());
        assertFalse(TelegramObjectKey.fromSearchResult(0, new Chat(32)).isPresent());
        assertFalse(TelegramObjectKey.fromSearchResult(0, new Dialog(-33)).isPresent());
        assertFalse(TelegramObjectKey.fromSearchResult(0, new Message(34)).isPresent());
        assertFalse(TelegramObjectKey.fromRecent(0, new Object()).isPresent());
        assertFalse(TelegramObjectKey.fromShareSearch(0, new Object()).isPresent());
        assertFalse(TelegramObjectKey.fromPickerResult(0, new Contact(0)).isPresent());
        assertFalse(TelegramObjectKey.fromDialog(0, new Object()).isPresent());
        assertFalse(TelegramObjectKey.fromUser(0, new User(0)).isPresent());
        assertFalse(TelegramObjectKey.fromChat(0, new Chat(Long.MIN_VALUE)).isPresent());
    }
}
