package io.github.cepeter.royalty.config;

import android.content.SharedPreferences;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import java.util.concurrent.CopyOnWriteArraySet;

public final class XposedPreferenceService {
    public interface Listener {
        void onPreferencesAvailable(SharedPreferences preferences);
    }

    private static final CopyOnWriteArraySet<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile XposedService activeService;
    private static volatile SharedPreferences preferences;

    static {
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService service) {
                try {
                    SharedPreferences remote = service.getRemotePreferences(ConfigStore.PREFERENCES_NAME);
                    activeService = service;
                    preferences = remote;
                    notifyListeners(remote);
                } catch (RuntimeException ignored) {
                    if (activeService == service) {
                        activeService = null;
                        preferences = null;
                        notifyListeners(null);
                    }
                }
            }

            @Override
            public void onServiceDied(XposedService service) {
                if (activeService == service) {
                    activeService = null;
                    preferences = null;
                    notifyListeners(null);
                }
            }
        });
    }

    private XposedPreferenceService() {}

    public static void subscribe(Listener listener) {
        LISTENERS.add(listener);
        listener.onPreferencesAvailable(preferences);
    }

    public static void unsubscribe(Listener listener) {
        LISTENERS.remove(listener);
    }

    private static void notifyListeners(SharedPreferences current) {
        for (Listener listener : LISTENERS) {
            listener.onPreferencesAvailable(current);
        }
    }
}
