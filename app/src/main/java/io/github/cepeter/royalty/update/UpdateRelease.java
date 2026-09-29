package io.github.cepeter.royalty.update;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

public final class UpdateRelease {
    static final String TRUSTED_RELEASE_PREFIX =
            "https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/";
    static final Pattern VERSION_PATTERN = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)$");
    private static final Pattern CURRENT_VERSION_PATTERN =
            Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$");

    private final String tag;
    private final String version;
    private final String url;
    private final long[] versionParts;

    private UpdateRelease(String tag, String version, String url, long[] versionParts) {
        this.tag = tag;
        this.version = version;
        this.url = url;
        this.versionParts = versionParts;
    }

    public static UpdateRelease fromJson(String json) {
        try {
            JSONObject release = new JSONObject(json);
            return fromMetadata(
                    release.optString("tag_name", ""),
                    release.optString("html_url", ""),
                    release.optBoolean("draft", true),
                    release.optBoolean("prerelease", true));
        } catch (Exception error) {
            return null;
        }
    }

    public static UpdateRelease fromStored(String tag, String url) {
        return fromMetadata(tag, url, false, false);
    }

    static UpdateRelease fromMetadata(String tag, String url, boolean draft, boolean prerelease) {
        if (draft || prerelease || tag == null || url == null) {
            return null;
        }

        Matcher versionMatcher = VERSION_PATTERN.matcher(tag);
        if (!versionMatcher.matches() || !isTrustedReleaseUrl(url, tag)) {
            return null;
        }

        long[] parts = parseVersionParts(versionMatcher);
        if (parts == null) {
            return null;
        }
        return new UpdateRelease(
                tag,
                parts[0] + "." + parts[1] + "." + parts[2],
                url,
                parts);
    }

    public String tag() {
        return tag;
    }

    public String version() {
        return version;
    }

    public String url() {
        return url;
    }

    public boolean isNewerThan(String currentVersion) {
        Matcher matcher = CURRENT_VERSION_PATTERN.matcher(currentVersion);
        if (!matcher.matches()) {
            return false;
        }
        long[] currentParts = parseVersionParts(matcher);
        if (currentParts == null) {
            return false;
        }
        for (int index = 0; index < versionParts.length; index++) {
            if (versionParts[index] != currentParts[index]) {
                return versionParts[index] > currentParts[index];
            }
        }
        return false;
    }

    private static boolean isTrustedReleaseUrl(String url, String tag) {
        if (!url.equals(TRUSTED_RELEASE_PREFIX + tag)) {
            return false;
        }
        try {
            URI parsed = new URI(url);
            return "https".equals(parsed.getScheme())
                    && "github.com".equals(parsed.getHost())
                    && parsed.getPort() == -1
                    && parsed.getUserInfo() == null
                    && parsed.getQuery() == null
                    && parsed.getFragment() == null;
        } catch (Exception error) {
            return false;
        }
    }

    private static long[] parseVersionParts(Matcher matcher) {
        try {
            return new long[] {
                Long.parseLong(matcher.group(1)),
                Long.parseLong(matcher.group(2)),
                Long.parseLong(matcher.group(3)),
            };
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
