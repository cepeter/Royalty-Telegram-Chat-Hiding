package org.telegram.messenger;

public final class UserConfig {
    public static final int MAX_ACCOUNT_COUNT = 4;
    private static final UserConfig[] INSTANCES = new UserConfig[4];
    static { for (int i = 0; i < 4; i++) INSTANCES[i] = new UserConfig(); }
    public boolean loaded = true;
    public long id;
    public static UserConfig getInstance(int slot) { return INSTANCES[slot]; }
    public boolean isConfigLoaded() { return loaded; }
    public boolean isClientActivated() { return id > 0; }
    public long getClientUserId() { return id; }
    public Object getCurrentUser() { return id > 0 ? new Object() : null; }
}
