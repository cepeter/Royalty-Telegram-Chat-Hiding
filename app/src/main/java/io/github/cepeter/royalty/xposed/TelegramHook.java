package io.github.cepeter.royalty.xposed;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.cepeter.royalty.core.CatalogSubmission;
import io.github.cepeter.royalty.core.DialogFilter;
import io.github.cepeter.royalty.core.DialogKey;
import io.github.cepeter.royalty.core.HiddenConfig;
import io.github.cepeter.royalty.core.PressAndHoldGesture;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TelegramHook implements IXposedHookLoadPackage {
    private static final long CATALOG_PUBLISH_INTERVAL_MS = 3000;
    private static final long REVEAL_HOLD_DURATION_MS = 3000;
    private static final String[] ACTION_BAR_CLASS_NAMES = {
        "org.telegram.ui.ActionBar.ActionBar",
        "org.telegram.ui.ActionBar.l"
    };
    private static final String[] DIALOGS_ACTIVITY_CLASS_NAMES = {
        "org.telegram.ui.DialogsActivity",
        "org.telegram.ui.iz"
    };
    private static final String[] BASE_FRAGMENT_CLASS_NAMES = {
        "org.telegram.ui.ActionBar.BaseFragment",
        "org.telegram.ui.ActionBar.q2"
    };
    private static final String ACTION_BAR_LAYOUT_CLASS =
            "org.telegram.ui.ActionBar.ActionBarLayout";

    private static final XposedConfigRepository CONFIG = new XposedConfigRepository();
    private static final CatalogSnapshotStore CATALOGS = new CatalogSnapshotStore();
    private static final AtomicBoolean REVEALED = new AtomicBoolean(false);
    private static final AtomicBoolean RUNTIME_HOOKS_INSTALLED = new AtomicBoolean(false);
    private static final PressAndHoldGesture REVEAL_GESTURE =
            new PressAndHoldGesture(REVEAL_HOLD_DURATION_MS);
    private static final Map<Integer, Long> LAST_CATALOG_PUBLISH = new LinkedHashMap<>();

    private static View pendingRevealView;
    private static Runnable pendingRevealTask;
    private static View.OnAttachStateChangeListener pendingRevealDetachListener;
    private static long revealGestureGeneration;

    private static ClassLoader telegramClassLoader;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"org.telegram.messenger".equals(lpparam.packageName)
                || !lpparam.packageName.equals(lpparam.processName)) {
            return;
        }

        telegramClassLoader = lpparam.classLoader;
        install("bridge", () -> installApplicationBridge(lpparam.classLoader));
    }

    private static void installApplicationBridge(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(
                "org.telegram.messenger.ApplicationLoader",
                classLoader,
                "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        installRuntimeHooks((Context) param.thisObject, classLoader);
                    }
                });
    }

    private static void installRuntimeHooks(Context context, ClassLoader classLoader) {
        if (!RUNTIME_HOOKS_INSTALLED.compareAndSet(false, true)) {
            return;
        }

        try {
            CatalogRequestBridge.register(context, CATALOGS);
        } catch (RuntimeException error) {
            XposedBridge.log("TelegramChatHider: bridge startup failed: " + error);
        }
        install("compatibility", () -> TelegramCompatibilityProbe.verify(classLoader));
        install("search", () -> TelegramSearchHook.install(
                classLoader,
                CONFIG::current,
                REVEALED::get,
                (status, detail) -> reportStatus("search", status, detail)));
        install("share", () -> TelegramShareHook.install(
                classLoader,
                CONFIG::current,
                REVEALED::get,
                (status, detail) -> reportStatus("share", status, detail)));
        install("contacts", () -> TelegramContactHook.install(
                classLoader,
                CONFIG::current,
                REVEALED::get,
                (status, detail) -> reportStatus("contacts", status, detail)));
        install("dialogs", () -> installDialogHook(classLoader));
        install("notifications", () -> installNotificationHook(classLoader));
        install("reveal", () -> installRevealHook(classLoader));
    }

    private static void installDialogHook(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(
                "org.telegram.messenger.MessagesController",
                classLoader,
                "getDialogs",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object rawResult = param.getResult();
                        if (!(rawResult instanceof List<?>)) {
                            return;
                        }

                        try {
                            int account = XposedHelpers.getIntField(param.thisObject, "currentAccount");
                            @SuppressWarnings("unchecked")
                            List<Object> source = (List<Object>) rawResult;
                            publishCatalogIfDue(account, param.thisObject, source);

                            HiddenConfig config = CONFIG.current();
                            List<Object> filtered = DialogFilter.filteredCopy(
                                    source,
                                    dialog -> DialogKey.of(
                                            account,
                                            XposedHelpers.getLongField(dialog, "id")),
                                    config,
                                    REVEALED.get());
                            param.setResult(filtered);
                        } catch (Throwable error) {
                            reportRuntimeError("dialogs", error);
                        }
                    }
                });
    }

    private static void installNotificationHook(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(
                "org.telegram.messenger.NotificationsController",
                classLoader,
                "processNewMessages",
                ArrayList.class,
                boolean.class,
                boolean.class,
                CountDownLatch.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!(param.args[0] instanceof List<?>)) {
                            return;
                        }

                        try {
                            HiddenConfig config = CONFIG.current();
                            if (!config.suppressNotifications()) {
                                return;
                            }
                            int account = XposedHelpers.getIntField(param.thisObject, "currentAccount");
                            @SuppressWarnings("unchecked")
                            List<Object> source = (List<Object>) param.args[0];
                            List<Object> filtered = DialogFilter.filteredCopy(
                                    source,
                                    message -> DialogKey.of(
                                            account,
                                            ((Number) XposedHelpers.callMethod(
                                                    message, "getDialogId")).longValue()),
                                    config,
                                    false);
                            param.args[0] = filtered;
                        } catch (Throwable error) {
                            reportRuntimeError("notifications", error);
                        }
                    }
                });
    }

    private static void installRevealHook(ClassLoader classLoader) {
        Class<?> actionBarClass = resolveActionBarClass(classLoader);
        XposedHelpers.findAndHookMethod(
                actionBarClass,
                "dispatchTouchEvent",
                MotionEvent.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        MotionEvent event = (MotionEvent) param.args[0];
                        if (event == null) {
                            cancelRevealGesture();
                            return;
                        }

                        int action = event.getActionMasked();
                        try {
                            if (action == MotionEvent.ACTION_DOWN) {
                                View actionBar = (View) param.thisObject;
                                Object fragment = findDialogsFragment(actionBar, classLoader);
                                if (fragment == null) {
                                    cancelRevealGesture();
                                    return;
                                }
                                scheduleRevealGesture(
                                        actionBar,
                                        fragment,
                                        classLoader,
                                        event.getEventTime());
                                return;
                            }
                            if (action == MotionEvent.ACTION_UP
                                    || action == MotionEvent.ACTION_CANCEL) {
                                cancelRevealGesture();
                            }
                        } catch (Throwable error) {
                            cancelRevealGesture();
                            reportRuntimeError("reveal", error);
                        }
                    }
                });
    }

    private static synchronized void scheduleRevealGesture(
            View actionBar, Object fragment, ClassLoader classLoader, long eventTimeMs) {
        cancelRevealGesture();
        REVEAL_GESTURE.onDown(eventTimeMs);
        long generation = ++revealGestureGeneration;
        Runnable task = () -> completeRevealGesture(
                generation, actionBar, fragment, classLoader);
        View.OnAttachStateChangeListener detachListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {}

            @Override
            public void onViewDetachedFromWindow(View view) {
                cancelRevealGesture();
            }
        };
        pendingRevealView = actionBar;
        pendingRevealTask = task;
        pendingRevealDetachListener = detachListener;
        actionBar.addOnAttachStateChangeListener(detachListener);
        if (!actionBar.postDelayed(task, REVEAL_HOLD_DURATION_MS)) {
            cancelRevealGesture();
        }
    }

    private static synchronized void cancelRevealGesture() {
        revealGestureGeneration++;
        if (pendingRevealView != null) {
            if (pendingRevealTask != null) {
                pendingRevealView.removeCallbacks(pendingRevealTask);
            }
            if (pendingRevealDetachListener != null) {
                pendingRevealView.removeOnAttachStateChangeListener(
                        pendingRevealDetachListener);
            }
        }
        pendingRevealView = null;
        pendingRevealTask = null;
        pendingRevealDetachListener = null;
        REVEAL_GESTURE.cancel();
    }

    private static void completeRevealGesture(
            long generation, View actionBar, Object expectedFragment, ClassLoader classLoader) {
        synchronized (TelegramHook.class) {
            if (generation != revealGestureGeneration
                    || !REVEAL_GESTURE.onDeadline(android.os.SystemClock.uptimeMillis())) {
                return;
            }
            if (pendingRevealView != null && pendingRevealDetachListener != null) {
                pendingRevealView.removeOnAttachStateChangeListener(
                        pendingRevealDetachListener);
            }
            pendingRevealView = null;
            pendingRevealTask = null;
            pendingRevealDetachListener = null;
        }

        try {
            Object fragment = findDialogsFragment(actionBar, classLoader);
            if (fragment != expectedFragment) {
                return;
            }
            boolean revealed = toggleReveal();
            Toast.makeText(
                            actionBar.getContext(),
                            revealed ? "Hidden chats revealed" : "Hidden chats concealed",
                            Toast.LENGTH_SHORT)
                    .show();
            requestDialogsReload(fragment);
        } catch (Throwable error) {
            cancelRevealGesture();
            reportRuntimeError("reveal", error);
        }
    }

    private static Class<?> resolveActionBarClass(ClassLoader classLoader) {
        for (String className : ACTION_BAR_CLASS_NAMES) {
            try {
                Class<?> candidate = XposedHelpers.findClass(className, classLoader);
                if (android.view.View.class.isAssignableFrom(candidate)
                        && declaresDispatchTouchEvent(candidate)) {
                    return candidate;
                }
            } catch (XposedHelpers.ClassNotFoundError ignored) {
                // Try the verified Telegram 12.10.4 alias.
            }
        }
        throw new IllegalStateException("Supported ActionBar class not found");
    }

    private static Class<?> resolveDialogsActivityClass(ClassLoader classLoader) {
        for (String className : DIALOGS_ACTIVITY_CLASS_NAMES) {
            try {
                Class<?> candidate = XposedHelpers.findClass(className, classLoader);
                candidate.getMethod("createView", Context.class);
                return candidate;
            } catch (XposedHelpers.ClassNotFoundError | NoSuchMethodException ignored) {
                // Try the verified Telegram 12.10.4 alias.
            }
        }
        throw new IllegalStateException("Supported DialogsActivity class not found");
    }

    private static Class<?> resolveBaseFragmentClass(ClassLoader classLoader) {
        for (String className : BASE_FRAGMENT_CLASS_NAMES) {
            try {
                return XposedHelpers.findClass(className, classLoader);
            } catch (XposedHelpers.ClassNotFoundError ignored) {
                // Try the verified Telegram 12.10.4 alias.
            }
        }
        throw new IllegalStateException("Supported BaseFragment class not found");
    }

    private static boolean declaresDispatchTouchEvent(Class<?> candidate) {
        for (Class<?> type = candidate;
                type != null && android.view.View.class.isAssignableFrom(type);
                type = type.getSuperclass()) {
            try {
                type.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
                return true;
            } catch (NoSuchMethodException ignored) {
                // Continue through Telegram's view hierarchy.
            }
        }
        return false;
    }

    private static Object findDialogsFragment(Object actionBar, ClassLoader classLoader)
            throws IllegalAccessException {
        Class<?> dialogsActivity = resolveDialogsActivityClass(classLoader);
        Object owningFragment = findOwningDialogsFragment(
                actionBar, classLoader, dialogsActivity);
        if (owningFragment != null) {
            return owningFragment;
        }

        Class<?> actionBarLayout = XposedHelpers.findClass(
                ACTION_BAR_LAYOUT_CLASS, classLoader);
        android.app.Activity activity = findActivity(
                ((android.view.View) actionBar).getContext());
        if (activity == null) {
            return null;
        }

        for (Class<?> type = activity.getClass();
                type != null && type != Object.class;
                type = type.getSuperclass()) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                if (!actionBarLayout.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object layout = field.get(activity);
                if (layout == null) {
                    continue;
                }
                Object fragment = XposedHelpers.callMethod(layout, "getLastFragment");
                if (!dialogsActivity.isInstance(fragment)) {
                    continue;
                }
                Object fragmentActionBar = XposedHelpers.callMethod(fragment, "getActionBar");
                if (fragmentActionBar == actionBar) {
                    return fragment;
                }
            }
        }
        return null;
    }

    private static Object findOwningDialogsFragment(
            Object actionBar, ClassLoader classLoader, Class<?> dialogsActivity)
            throws IllegalAccessException {
        Class<?> baseFragmentClass = resolveBaseFragmentClass(classLoader);
        for (Class<?> type = actionBar.getClass();
                type != null && type != Object.class;
                type = type.getSuperclass()) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                if (!baseFragmentClass.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object fragment = field.get(actionBar);
                if (!dialogsActivity.isInstance(fragment)) {
                    continue;
                }
                Object fragmentActionBar = XposedHelpers.callMethod(fragment, "getActionBar");
                if (fragmentActionBar == actionBar) {
                    return fragment;
                }
            }
        }
        return null;
    }

    private static android.app.Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof android.content.ContextWrapper) {
            if (current instanceof android.app.Activity) {
                return (android.app.Activity) current;
            }
            Context next = ((android.content.ContextWrapper) current).getBaseContext();
            if (next == current) {
                break;
            }
            current = next;
        }
        return current instanceof android.app.Activity
                ? (android.app.Activity) current
                : null;
    }

    private static void publishCatalogIfDue(
            int account, Object messagesController, List<Object> dialogs) {
        long now = android.os.SystemClock.elapsedRealtime();
        synchronized (LAST_CATALOG_PUBLISH) {
            Long last = LAST_CATALOG_PUBLISH.get(account);
            if (last != null && now - last < CATALOG_PUBLISH_INTERVAL_MS) {
                return;
            }
            LAST_CATALOG_PUBLISH.put(account, now);
        }

        int count = Math.min(dialogs.size(), CatalogSubmission.MAX_ENTRIES);
        long[] ids = new long[count];
        String[] titles = new String[count];
        int added = 0;
        for (int index = 0; index < count; index++) {
            Object dialog = dialogs.get(index);
            try {
                long id = XposedHelpers.getLongField(dialog, "id");
                if (id == 0) {
                    continue;
                }
                ids[added] = id;
                titles[added] = resolveDialogTitle(messagesController, id);
                added++;
            } catch (Throwable ignored) {
                // Telegram may add synthetic rows; they are not hideable dialogs.
            }
        }
        if (added != count) {
            ids = java.util.Arrays.copyOf(ids, added);
            titles = java.util.Arrays.copyOf(titles, added);
        }
        CATALOGS.replaceAccount(account, ids, titles);
    }

    private static String resolveDialogTitle(Object messagesController, long dialogId) {
        try {
            if (dialogId > 0) {
                Object user = XposedHelpers.callMethod(
                        messagesController, "getUser", Long.valueOf(dialogId));
                if (user != null) {
                    String firstName = nullableString(XposedHelpers.getObjectField(user, "first_name"));
                    String lastName = nullableString(XposedHelpers.getObjectField(user, "last_name"));
                    String fullName = (firstName + " " + lastName).trim();
                    if (!fullName.isEmpty()) {
                        return fullName;
                    }
                    String username = nullableString(XposedHelpers.getObjectField(user, "username"));
                    if (!username.isEmpty()) {
                        return "@" + username;
                    }
                }
            } else {
                Object chat = XposedHelpers.callMethod(
                        messagesController, "getChat", Long.valueOf(-dialogId));
                if (chat != null) {
                    String title = nullableString(XposedHelpers.getObjectField(chat, "title"));
                    if (!title.isEmpty()) {
                        return title;
                    }
                }
            }
        } catch (Throwable ignored) {
            // ID fallback is stable across Telegram schema changes.
        }
        return String.valueOf(dialogId);
    }

    private static String nullableString(Object value) {
        return value instanceof String ? (String) value : "";
    }

    private static boolean toggleReveal() {
        for (;;) {
            boolean current = REVEALED.get();
            boolean next = !current;
            if (REVEALED.compareAndSet(current, next)) {
                return next;
            }
        }
    }

    private static void requestDialogsReload(Object fragment) {
        int account = XposedHelpers.getIntField(fragment, "currentAccount");
        Class<?> notificationCenter = XposedHelpers.findClass(
                "org.telegram.messenger.NotificationCenter", telegramClassLoader);
        Object instance = XposedHelpers.callStaticMethod(
                notificationCenter, "getInstance", account);
        int event = XposedHelpers.getStaticIntField(notificationCenter, "dialogsNeedReload");
        XposedHelpers.callMethod(instance, "postNotificationName", event, new Object[0]);
    }

    private static void install(String hook, HookInstaller installer) {
        try {
            installer.install();
            reportStatus(hook, "installed", "");
        } catch (Throwable error) {
            reportStatus(hook, "missing", error.getClass().getSimpleName());
            XposedBridge.log("TelegramChatHider: " + hook + " hook unavailable: " + error);
        }
    }

    private static void reportRuntimeError(String hook, Throwable error) {
        if (error instanceof VirtualMachineError) {
            throw (VirtualMachineError) error;
        }
        reportStatus(hook, "runtime_error", error.getClass().getSimpleName());
        XposedBridge.log("TelegramChatHider: " + hook + " runtime error: " + error);
    }

    private static void reportStatus(String hook, String status, String detail) {
        CATALOGS.recordStatus(hook, status, detail);
    }

    private interface HookInstaller {
        void install() throws Throwable;
    }
}
