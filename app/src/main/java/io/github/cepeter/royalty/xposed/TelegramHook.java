package io.github.cepeter.royalty.xposed;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import io.github.cepeter.royalty.config.ConfigStore;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import io.github.cepeter.royalty.core.DialogFilter;
import io.github.cepeter.royalty.core.ActivityVisibility;
import io.github.cepeter.royalty.core.AuthenticationProtocol;
import io.github.cepeter.royalty.core.AuthenticationRoute;
import io.github.cepeter.royalty.core.RevealSession;
import io.github.cepeter.royalty.core.DialogKey;
import io.github.cepeter.royalty.core.HiddenConfig;
import io.github.cepeter.royalty.core.PressAndHoldGesture;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TelegramHook extends XposedModule {
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

    private static XposedConfigRepository CONFIG;
    private static String processName;
    private static final CatalogSnapshotStore CATALOGS = new CatalogSnapshotStore();
    private static final RevealSession REVEAL = new RevealSession();
    private static final ActivityVisibility VISIBILITY = new ActivityVisibility();
    private static final AdapterRefreshRegistry ADAPTERS = new AdapterRefreshRegistry();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static String modulePackage;
    private static Object lastDialogsFragment;
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
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        ModernHookBridge.attach(this);
        processName = param.getProcessName();
        modulePackage = getModuleApplicationInfo().packageName;
        CONFIG = new XposedConfigRepository(getRemotePreferences(ConfigStore.PREFERENCES_NAME));
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!param.isFirstPackage()
                || !"org.telegram.messenger".equals(param.getPackageName())
                || !param.getPackageName().equals(processName)) {
            return;
        }

        telegramClassLoader = param.getClassLoader();
        install("bridge", () -> installApplicationBridge(param.getClassLoader()));
    }

    private static void installApplicationBridge(ClassLoader classLoader) {
        ModernHookBridge.findAndHookMethod(
                "org.telegram.messenger.ApplicationLoader",
                classLoader,
                "onCreate",
                new ModernHookBridge.MethodHook() {
                    @Override
                    protected void afterHookedMethod(ModernHookBridge.MethodHookParam param) {
                        installRuntimeHooks((Context) param.thisObject, classLoader);
                    }
                });
    }

    private static void installRuntimeHooks(Context context, ClassLoader classLoader) {
        if (!RUNTIME_HOOKS_INSTALLED.compareAndSet(false, true)) {
            return;
        }

        try {
            CatalogRequestBridge.register(context, CATALOGS, CONFIG::inventory);
            reportStatus("bridge", "installed", "");
        } catch (RuntimeException error) {
            reportStatus("bridge", "missing", error.getClass().getSimpleName());
            ModernHookBridge.log("TelegramChatHider: bridge startup failed: " + error);
        }
        TelegramVersionGuard.Version version;
        try {
            version = TelegramVersionGuard.read(context);
        } catch (RuntimeException error) {
            reportStatus("compatibility", "version_read_error", error.getClass().getSimpleName());
            ModernHookBridge.log("TelegramChatHider: Telegram version unavailable: " + error);
            return;
        }
        if (!TelegramVersionGuard.isSupported(version)) {
            reportUnsupportedVersion(version);
            return;
        }
        install("compatibility", () -> TelegramCompatibilityProbe.verify(classLoader));
        try {
            CONFIG.setOwnerResolver(new UserConfigOwnerResolver(classLoader),
                    (status, detail) -> reportStatus("ownership", status, detail));
            CONFIG.inventory();
        } catch (RuntimeException error) {
            reportStatus("ownership", "missing", error.getClass().getSimpleName());
        }
        install("search", () -> TelegramSearchHook.install(
                classLoader,
                CONFIG::current,
                TelegramHook::revealState,
                ADAPTERS::track,
                (status, detail) -> reportStatus("search", status, detail)));
        install("share", () -> TelegramShareHook.install(
                classLoader,
                CONFIG::current,
                TelegramHook::revealState,
                ADAPTERS::track,
                (status, detail) -> reportStatus("share", status, detail)));
        install("contacts", () -> TelegramContactHook.install(
                classLoader,
                CONFIG::current,
                TelegramHook::revealState,
                ADAPTERS::track,
                (status, detail) -> reportStatus("contacts", status, detail)));
        install("dialogs", () -> installDialogHook(classLoader));
        install("notifications", () -> installNotificationHook(classLoader));
        install("premium", () -> TelegramPremiumHook.install(
                classLoader,
                CONFIG::localPremiumEnabled,
                (status, detail) -> reportStatus("premium", status, detail)));
        RevealInstallation.install(() -> installRevealHook(classLoader),
                () -> installRevealAdapters((Application) context.getApplicationContext()),
                (installed, error) -> {
                    if (installed) reportStatus("reveal", "installed", "");
                    else {
                        reportStatus("reveal", "missing", error.getClass().getSimpleName());
                        ModernHookBridge.log("TelegramChatHider: reveal prerequisites unavailable: " + error);
                    }
                });
    }

    private static void installDialogHook(ClassLoader classLoader) {
        ModernHookBridge.findAndHookMethod(
                "org.telegram.messenger.MessagesController",
                classLoader,
                "getDialogs",
                int.class,
                new ModernHookBridge.MethodHook() {
                    @Override
                    protected void afterHookedMethod(ModernHookBridge.MethodHookParam param) {
                        Object rawResult = param.getResult();
                        if (!(rawResult instanceof List<?>)) {
                            return;
                        }

                        try {
                            int account = ModernHookBridge.getIntField(param.thisObject, "currentAccount");
                            @SuppressWarnings("unchecked")
                            List<Object> source = (List<Object>) rawResult;
                            publishCatalogIfDue(account, param.thisObject, source);

                            HiddenConfig config = CONFIG.current();
                            List<Object> filtered = DialogFilter.filteredCopy(
                                    source,
                                    dialog -> DialogKey.of(
                                            account,
                                            ModernHookBridge.getLongField(dialog, "id")),
                                    config,
                                    revealState());
                            param.setResult(filtered);
                        } catch (Throwable error) {
                            reportRuntimeError("dialogs", error);
                        }
                    }
                });
    }

    private static void installNotificationHook(ClassLoader classLoader) {
        ModernHookBridge.findAndHookMethod(
                "org.telegram.messenger.NotificationsController",
                classLoader,
                "processNewMessages",
                ArrayList.class,
                boolean.class,
                boolean.class,
                CountDownLatch.class,
                new ModernHookBridge.MethodHook() {
                    @Override
                    protected void beforeHookedMethod(ModernHookBridge.MethodHookParam param) {
                        if (!(param.args[0] instanceof List<?>)) {
                            return;
                        }

                        try {
                            HiddenConfig config = CONFIG.current();
                            if (!config.suppressNotifications()) {
                                return;
                            }
                            int account = ModernHookBridge.getIntField(param.thisObject, "currentAccount");
                            @SuppressWarnings("unchecked")
                            List<Object> source = (List<Object>) param.args[0];
                            List<Object> filtered = DialogFilter.filteredCopy(
                                    source,
                                    message -> DialogKey.of(
                                            account,
                                            ((Number) ModernHookBridge.callMethod(
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
        ModernHookBridge.findAndHookMethod(
                actionBarClass,
                "dispatchTouchEvent",
                MotionEvent.class,
                new ModernHookBridge.MethodHook() {
                    @Override
                    protected void beforeHookedMethod(ModernHookBridge.MethodHookParam param) {
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
            lastDialogsFragment = fragment;
            REVEAL.configure(CONFIG.saved());
            boolean revealed;
            if (!REVEAL.revealed() && REVEAL.authenticationRequired()) {
                String nonce = REVEAL.beginChallenge(SystemClock.elapsedRealtime());
                if (nonce == null || !launchCredential(actionBar.getContext(), nonce)) {
                    REVEAL.cancelChallenge();
                    Toast.makeText(actionBar.getContext(), "Device credential unavailable; hidden chats remain concealed", Toast.LENGTH_LONG).show();
                }
                return;
            }
            revealed = REVEAL.toggle(SystemClock.elapsedRealtime());
            scheduleRevealTimeout();
            Toast.makeText(
                            actionBar.getContext(),
                            revealed ? "Hidden chats revealed" : "Hidden chats concealed",
                            Toast.LENGTH_SHORT)
                    .show();
            refreshRevealedViews();
        } catch (Throwable error) {
            cancelRevealGesture();
            reportRuntimeError("reveal", error);
        }
    }

    private static Class<?> resolveActionBarClass(ClassLoader classLoader) {
        for (String className : ACTION_BAR_CLASS_NAMES) {
            try {
                Class<?> candidate = ModernHookBridge.findClass(className, classLoader);
                if (android.view.View.class.isAssignableFrom(candidate)
                        && declaresDispatchTouchEvent(candidate)) {
                    return candidate;
                }
            } catch (ModernHookBridge.ClassLookupException ignored) {
                // Try the verified Telegram 12.10.4 alias.
            }
        }
        throw new IllegalStateException("Supported ActionBar class not found");
    }

    private static Class<?> resolveDialogsActivityClass(ClassLoader classLoader) {
        for (String className : DIALOGS_ACTIVITY_CLASS_NAMES) {
            try {
                Class<?> candidate = ModernHookBridge.findClass(className, classLoader);
                candidate.getMethod("createView", Context.class);
                return candidate;
            } catch (ModernHookBridge.ClassLookupException | NoSuchMethodException ignored) {
                // Try the verified Telegram 12.10.4 alias.
            }
        }
        throw new IllegalStateException("Supported DialogsActivity class not found");
    }

    private static Class<?> resolveBaseFragmentClass(ClassLoader classLoader) {
        for (String className : BASE_FRAGMENT_CLASS_NAMES) {
            try {
                return ModernHookBridge.findClass(className, classLoader);
            } catch (ModernHookBridge.ClassLookupException ignored) {
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

        Class<?> actionBarLayout = ModernHookBridge.findClass(
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
                Object fragment = ModernHookBridge.callMethod(layout, "getLastFragment");
                if (!dialogsActivity.isInstance(fragment)) {
                    continue;
                }
                Object fragmentActionBar = ModernHookBridge.callMethod(fragment, "getActionBar");
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
                Object fragmentActionBar = ModernHookBridge.callMethod(fragment, "getActionBar");
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

        CatalogOwnerPublisher.publish(account, dialogs, new CatalogOwnerPublisher.RowReader<Object>() {
            @Override public long id(Object row) {
                return ModernHookBridge.getLongField(row, "id");
            }
            @Override public String title(Object row, long id) {
                return resolveDialogTitle(messagesController, id);
            }
        }, CONFIG::inventory, CATALOGS);
    }

    private static String resolveDialogTitle(Object messagesController, long dialogId) {
        try {
            if (dialogId > 0) {
                Object user = ModernHookBridge.callMethod(
                        messagesController, "getUser", Long.valueOf(dialogId));
                if (user != null) {
                    String firstName = nullableString(ModernHookBridge.getObjectField(user, "first_name"));
                    String lastName = nullableString(ModernHookBridge.getObjectField(user, "last_name"));
                    String fullName = (firstName + " " + lastName).trim();
                    if (!fullName.isEmpty()) {
                        return fullName;
                    }
                    String username = nullableString(ModernHookBridge.getObjectField(user, "username"));
                    if (!username.isEmpty()) {
                        return "@" + username;
                    }
                }
            } else {
                Object chat = ModernHookBridge.callMethod(
                        messagesController, "getChat", Long.valueOf(-dialogId));
                if (chat != null) {
                    String title = nullableString(ModernHookBridge.getObjectField(chat, "title"));
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

    private static boolean revealState() {
        try {
            boolean wasRevealed = REVEAL.revealed();
            REVEAL.configure(CONFIG.saved());
            REVEAL.onTime(SystemClock.elapsedRealtime());
            if (wasRevealed && !REVEAL.revealed()) MAIN.post(TelegramHook::refreshRevealedViews);
            return REVEAL.revealed();
        } catch (RuntimeException error) {
            REVEAL.configure(HiddenConfig.empty());
            return false;
        }
    }

    private static boolean launchCredential(Context context, String nonce) {
        try {
            Intent intent = new Intent().setComponent(new ComponentName(
                    AuthenticationRoute.packageName(modulePackage), AuthenticationRoute.className()))
                    .putExtra(AuthenticationProtocol.EXTRA_NONCE, nonce)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (RuntimeException error) {
            reportRuntimeError("reveal", error);
            return false;
        }
    }

    private static void installRevealAdapters(Application application) {
        java.util.function.BooleanSupplier active = ModernHookBridge.installationActive();
        Application.ActivityLifecycleCallbacks lifecycle = new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityStarted(Activity activity) {
                if (!active.getAsBoolean()) return;
                VISIBILITY.started(activity);
                boolean wasRevealed = REVEAL.revealed();
                revealState();
                REVEAL.onForeground(SystemClock.elapsedRealtime());
                if (wasRevealed != REVEAL.revealed()) {
                    refreshRevealedViews();
                    scheduleRevealTimeout();
                }
            }
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {
                if (!active.getAsBoolean()) return;
                if (VISIBILITY.stopped(activity, activity.isChangingConfigurations())) {
                    if (REVEAL.onBackground(REVEAL.credentialHandoff())) refreshRevealedViews();
                } else {
                    long generation = VISIBILITY.pendingGeneration();
                    if (generation >= 0) MAIN.postDelayed(() -> {
                        if (active.getAsBoolean() && VISIBILITY.reconcile(generation)
                                && REVEAL.onBackground(REVEAL.credentialHandoff())) refreshRevealedViews();
                    }, ActivityVisibility.RECREATION_GRACE_MS);
                }
            }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        };
        ModernHookBridge.trackCleanup(() -> application.unregisterActivityLifecycleCallbacks(lifecycle));
        application.registerActivityLifecycleCallbacks(lifecycle);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (!active.getAsBoolean()) return;
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    if (REVEAL.onScreenOff()) refreshRevealedViews();
                    return;
                }
                if (!AuthenticationProtocol.ACTION_RESULT.equals(intent.getAction())
                        || !intent.getBooleanExtra(AuthenticationProtocol.EXTRA_SUCCESS, false)) return;
                try {
                    REVEAL.configure(CONFIG.saved());
                    if (REVEAL.authorize(intent.getStringExtra(AuthenticationProtocol.EXTRA_NONCE),
                            SystemClock.elapsedRealtime())) {
                        refreshRevealedViews();
                        scheduleRevealTimeout();
                    }
                } catch (RuntimeException error) {
                    REVEAL.cancelChallenge();
                    reportRuntimeError("reveal", error);
                }
            }
        };
        // One receiver identity owns both registrations; unregister removes both filters.
        ModernHookBridge.trackCleanup(() -> application.unregisterReceiver(receiver));
        application.registerReceiver(receiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        IntentFilter response = new IntentFilter(AuthenticationProtocol.ACTION_RESULT);
        if (Build.VERSION.SDK_INT >= 33) {
            application.registerReceiver(receiver, response,
                    AuthenticationProtocol.SIGNATURE_PERMISSION, null, Context.RECEIVER_EXPORTED);
        } else {
            registerLegacyAuthReceiver(application, receiver, response);
        }
    }

    @SuppressWarnings("deprecation")
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private static void registerLegacyAuthReceiver(Context context, BroadcastReceiver receiver, IntentFilter filter) {
        context.registerReceiver(receiver, filter, AuthenticationProtocol.SIGNATURE_PERMISSION, null);
    }

    private static void scheduleRevealTimeout() {
        int timeout = REVEAL.timeoutMs();
        if (!REVEAL.revealed() || timeout == 0) return;
        long generation = REVEAL.generation();
        MAIN.postDelayed(() -> {
            if (REVEAL.onTimeout(generation, SystemClock.elapsedRealtime())) refreshRevealedViews();
        }, timeout);
    }

    private static void refreshRevealedViews() {
        Object fragment = lastDialogsFragment;
        if (fragment != null) {
            try { requestDialogsReload(fragment); }
            catch (Throwable error) { reportRuntimeError("reveal", error); }
        }
        TelegramSearchHook.invalidatePositions();
        TelegramContactHook.invalidateSections();
        ADAPTERS.refreshAll(adapter -> {
            try { AdapterRefreshRegistry.refresh(adapter); }
            catch (Throwable error) { reportRuntimeError("reveal", error); }
        });
    }

    private static void requestDialogsReload(Object fragment) {
        int account = ModernHookBridge.getIntField(fragment, "currentAccount");
        Class<?> notificationCenter = ModernHookBridge.findClass(
                "org.telegram.messenger.NotificationCenter", telegramClassLoader);
        Object instance = ModernHookBridge.callStaticMethod(
                notificationCenter, "getInstance", account);
        int event = ModernHookBridge.getStaticIntField(notificationCenter, "dialogsNeedReload");
        ModernHookBridge.callMethod(instance, "postNotificationName", event, new Object[0]);
    }

    private static void install(String hook, HookInstaller installer) {
        try {
            ModernHookBridge.installAtomically(installer::install);
            reportStatus(hook, "installed", "");
        } catch (Throwable error) {
            reportStatus(hook, "missing", error.getClass().getSimpleName());
            ModernHookBridge.log("TelegramChatHider: " + hook + " hook unavailable: " + error);
        }
    }

    private static void reportUnsupportedVersion(TelegramVersionGuard.Version version) {
        String detail = version.describe() + "; requires "
                + TelegramVersionGuard.SUPPORTED_VERSION_NAME + " ("
                + TelegramVersionGuard.SUPPORTED_VERSION_CODE + ")";
        String[] hooks = {
            "compatibility", "search", "share", "contacts", "dialogs", "notifications", "premium", "reveal", "ownership"
        };
        for (String hook : hooks) {
            reportStatus(hook, "unsupported_version", detail);
        }
        ModernHookBridge.log("TelegramChatHider: unsupported Telegram " + detail);
    }

    private static void reportRuntimeError(String hook, Throwable error) {
        if (error instanceof VirtualMachineError) {
            throw (VirtualMachineError) error;
        }
        reportStatus(hook, "runtime_error", error.getClass().getSimpleName());
        ModernHookBridge.log("TelegramChatHider: " + hook + " runtime error: " + error);
    }

    private static void reportStatus(String hook, String status, String detail) {
        CATALOGS.recordStatus(hook, status, detail);
    }

    private interface HookInstaller {
        void install() throws Throwable;
    }
}
