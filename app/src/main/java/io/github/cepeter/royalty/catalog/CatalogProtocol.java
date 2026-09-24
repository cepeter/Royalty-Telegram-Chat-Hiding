package io.github.cepeter.royalty.catalog;

public final class CatalogProtocol {
    public static final String MODULE_PACKAGE = "io.github.cepeter.royalty";
    public static final String TELEGRAM_PACKAGE = "org.telegram.messenger";
    public static final String REQUEST_PERMISSION = MODULE_PACKAGE + ".permission.CATALOG_REQUEST";
    public static final String ACTION_REQUEST = MODULE_PACKAGE + ".action.REQUEST_CATALOG";
    public static final String ACTION_RESULT = MODULE_PACKAGE + ".action.CATALOG_RESULT";
    public static final String ACTION_UPDATED = MODULE_PACKAGE + ".action.CATALOG_UPDATED";
    public static final String EXTRA_CALLBACK = "callback";
    public static final String EXTRA_NONCE = "nonce";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_ACCOUNT = "account";
    public static final String EXTRA_IDS = "ids";
    public static final String EXTRA_TITLES = "titles";
    public static final String EXTRA_STATUS_HOOKS = "status_hooks";
    public static final String EXTRA_STATUS_VALUES = "status_values";
    public static final String EXTRA_STATUS_DETAILS = "status_details";
    public static final String TYPE_ACCOUNT = "account";
    public static final String TYPE_STATUS = "status";
    public static final String TYPE_COMPLETE = "complete";
    public static final int MAX_STATUS_NAME_LENGTH = 32;
    public static final int MAX_STATUS_DETAIL_LENGTH = 256;
    public static final int MAX_STATUS_COUNT = 16;
    public static final long NONCE_LIFETIME_MS = 15_000L;

    private CatalogProtocol() {}
}
