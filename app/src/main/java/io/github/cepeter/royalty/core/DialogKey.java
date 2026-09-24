package io.github.cepeter.royalty.core;

import java.util.Objects;
import java.util.Optional;

public final class DialogKey implements Comparable<DialogKey> {
    private static final int MAX_ACCOUNT = 15;

    private final int account;
    private final long dialogId;

    private DialogKey(int account, long dialogId) {
        this.account = account;
        this.dialogId = dialogId;
    }

    public static DialogKey of(int account, long dialogId) {
        if (account < 0 || account > MAX_ACCOUNT) {
            throw new IllegalArgumentException("account must be between 0 and " + MAX_ACCOUNT);
        }
        if (dialogId == 0) {
            throw new IllegalArgumentException("dialogId must be non-zero");
        }
        return new DialogKey(account, dialogId);
    }

    public static DialogKey parse(String value) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(':');
        if (separator <= 0 || separator != value.lastIndexOf(':')) {
            throw new IllegalArgumentException("dialog key must be account:dialogId");
        }

        final int account;
        final long dialogId;
        try {
            account = Integer.parseInt(value.substring(0, separator));
            dialogId = Long.parseLong(value.substring(separator + 1));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("invalid dialog key", error);
        }

        DialogKey key = of(account, dialogId);
        if (!key.toString().equals(value)) {
            throw new IllegalArgumentException("dialog key is not canonical");
        }
        return key;
    }

    public static Optional<DialogKey> tryParse(String value) {
        try {
            return Optional.of(parse(value));
        } catch (IllegalArgumentException | NullPointerException error) {
            return Optional.empty();
        }
    }

    public int account() {
        return account;
    }

    public long dialogId() {
        return dialogId;
    }

    @Override
    public int compareTo(DialogKey other) {
        int accountOrder = Integer.compare(account, other.account);
        return accountOrder != 0 ? accountOrder : Long.compare(dialogId, other.dialogId);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DialogKey)) return false;
        DialogKey key = (DialogKey) other;
        return account == key.account && dialogId == key.dialogId;
    }

    @Override
    public int hashCode() {
        return 31 * account + Long.hashCode(dialogId);
    }

    @Override
    public String toString() {
        return account + ":" + dialogId;
    }
}
