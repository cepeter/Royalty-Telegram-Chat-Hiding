package io.github.cepeter.royalty;

import io.github.cepeter.royalty.core.ConfigurationDraft;
import io.github.cepeter.royalty.core.HiddenConfig;

/** Shared draft-to-controls mapping and suppression of programmatic change listeners. */
final class SettingsDraftControls {
    interface Controls {
        void notifications(boolean value);
        void premium(boolean value);
        void background(boolean value);
        void screenOff(boolean value);
        void authentication(boolean value);
        void timeout(int milliseconds);
    }
    private boolean rendering;
    boolean rendering() { return rendering; }
    void render(ConfigurationDraft draft, Controls controls, Runnable selectionAndHistory) {
        boolean previous = rendering;
        rendering = true;
        try {
            HiddenConfig config = draft.current();
            controls.notifications(config.suppressNotifications());
            controls.premium(config.localPremium());
            controls.background(config.concealOnBackground());
            controls.screenOff(config.concealOnScreenOff());
            controls.authentication(config.authenticate());
            controls.timeout(config.revealTimeoutMs());
            selectionAndHistory.run();
        } finally { rendering = previous; }
    }
    void edit(Runnable change, Runnable repaint) {
        if (!rendering) { change.run(); repaint.run(); }
    }
}
