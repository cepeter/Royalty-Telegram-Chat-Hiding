package org.telegram.tgnet;

public final class TLRPC {
    private TLRPC() {}

    public static class Dialog {
        private final long id;

        public Dialog(long id) {
            this.id = id;
        }
    }

    public static class User {
        private final long id;

        public User(long id) {
            this.id = id;
        }
    }

    public static class Chat {
        private final long id;

        public Chat(long id) {
            this.id = id;
        }
    }
}
