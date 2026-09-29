package io.github.cepeter.royalty.update;

import android.os.Handler;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import javax.net.ssl.HttpsURLConnection;

public final class UpdateChecker implements AutoCloseable {
    private static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/cepeter/Royalty-Telegram-Chat-Hiding/releases/latest";
    private static final int NETWORK_TIMEOUT_MILLIS = 8_000;
    static final int MAX_RESPONSE_BYTES = 64 * 1024;

    public interface Callback {
        void onResult(UpdateRelease release);
    }

    private final Handler mainHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean closed;

    public UpdateChecker(Handler mainHandler) {
        this.mainHandler = mainHandler;
    }

    public void check(String currentVersion, Callback callback) {
        try {
            executor.execute(() -> {
                UpdateRelease release = fetch(currentVersion);
                if (!closed) {
                    mainHandler.post(() -> {
                        if (!closed) {
                            callback.onResult(release);
                        }
                    });
                }
            });
        } catch (RejectedExecutionException ignored) {
            // Activity teardown raced with a scheduled check; there is nothing to display.
        }
    }

    @Override
    public void close() {
        closed = true;
        executor.shutdownNow();
    }

    private UpdateRelease fetch(String currentVersion) {
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(LATEST_RELEASE_URL).openConnection();
            connection.setConnectTimeout(NETWORK_TIMEOUT_MILLIS);
            connection.setReadTimeout(NETWORK_TIMEOUT_MILLIS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            connection.setRequestProperty(
                    "User-Agent", "io.github.cepeter.royalty/" + currentVersion);
            if (connection.getResponseCode() != HttpsURLConnection.HTTP_OK) {
                return null;
            }
            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_RESPONSE_BYTES) {
                return null;
            }
            try (InputStream input = connection.getInputStream()) {
                return UpdateRelease.fromJson(readBounded(input));
            }
        } catch (Exception error) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readBounded(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4 * 1024];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > MAX_RESPONSE_BYTES) {
                throw new IllegalArgumentException("Release response is too large");
            }
            output.write(buffer, 0, count);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
