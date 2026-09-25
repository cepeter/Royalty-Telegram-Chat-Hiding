package io.github.cepeter.royalty.xposed;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

final class TelegramVersionGuard {
    static final String SUPPORTED_VERSION_NAME = "12.10.4";
    static final long SUPPORTED_VERSION_CODE = 70992L;

    private TelegramVersionGuard() {}

    static Version read(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getLongVersionCode()
                    : info.versionCode;
            return new Version(info.versionName, versionCode);
        } catch (PackageManager.NameNotFoundException error) {
            throw new IllegalStateException("cannot read Telegram package version", error);
        }
    }

    static boolean isSupported(Version version) {
        return SUPPORTED_VERSION_NAME.equals(version.name)
                && SUPPORTED_VERSION_CODE == version.code;
    }

    static final class Version {
        final String name;
        final long code;

        Version(String name, long code) {
            this.name = name == null ? "unknown" : name;
            this.code = code;
        }

        String describe() {
            return name + " (" + code + ")";
        }
    }
}
