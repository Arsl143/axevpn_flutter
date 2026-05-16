package de.blinkt.openvpn;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.ProfileManager;

/**
 * Replacement for the legacy packaged OpenVPN disconnect activity.
 *
 * Some builds still route the notification action here instead of the
 * activities.DisconnectVPN class, so this class must also disconnect directly.
 */
public class DisconnectVPNActivity extends Activity {
    private static final String TAG = "AxeVPN-DisconnectLegacy";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        disconnectAndFinish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        disconnectAndFinish();
    }

    private void disconnectAndFinish() {
        try {
            ProfileManager.setConntectedVpnProfileDisconnected(this);
        } catch (Exception e) {
            Log.w(TAG, "Failed to clear connected profile", e);
        }

        try {
            Intent disconnectIntent = new Intent(OpenVPNService.DISCONNECT_VPN);
            disconnectIntent.setClass(this, OpenVPNService.class);
            startService(disconnectIntent);
            Log.i(TAG, "Sent DISCONNECT_VPN intent to OpenVPNService");
        } catch (Exception e) {
            Log.e(TAG, "Failed to send DISCONNECT_VPN intent", e);
        }

        finish();
        overridePendingTransition(0, 0);
    }
}
