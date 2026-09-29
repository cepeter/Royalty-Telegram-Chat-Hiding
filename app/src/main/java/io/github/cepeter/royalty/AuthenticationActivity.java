package io.github.cepeter.royalty;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;
import io.github.cepeter.royalty.core.AuthenticationProtocol;

/** External entry has no authority. Only a successful platform result emits a response. */
public final class AuthenticationActivity extends Activity {
    private static final int CREDENTIAL_REQUEST = 1;
    private String nonce;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        nonce = getIntent().getStringExtra(AuthenticationProtocol.EXTRA_NONCE);
        if (!AuthenticationProtocol.validNonce(nonce)) { finish(); return; }
        if (state != null) return;
        KeyguardManager keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguard == null || !keyguard.isDeviceSecure()) {
            Toast.makeText(this, "Set a device screen lock before enabling authentication", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        @SuppressWarnings("deprecation")
        Intent credential = keyguard.createConfirmDeviceCredentialIntent("Reveal hidden chats", "Confirm your device screen lock");
        if (credential == null) { finish(); return; }
        try { startActivityForResult(credential, CREDENTIAL_REQUEST); }
        catch (RuntimeException error) {
            Toast.makeText(this, "Device credential is unavailable", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != CREDENTIAL_REQUEST) return;
        if (resultCode == RESULT_OK && AuthenticationProtocol.validNonce(nonce)) {
            sendBroadcast(new Intent(AuthenticationProtocol.ACTION_RESULT)
                    .setPackage(AuthenticationProtocol.TELEGRAM_PACKAGE)
                    .putExtra(AuthenticationProtocol.EXTRA_NONCE, nonce)
                    .putExtra(AuthenticationProtocol.EXTRA_SUCCESS, true));
        } else {
            Toast.makeText(this, "Authentication cancelled; hidden chats remain concealed", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
