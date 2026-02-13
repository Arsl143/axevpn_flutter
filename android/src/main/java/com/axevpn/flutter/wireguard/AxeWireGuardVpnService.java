package com.axevpn.flutter.wireguard;

import android.app.Service;
import android.content.Intent;
import android.net.VpnService;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import androidx.annotation.Nullable;

import com.wireguard.config.Config;
import com.wireguard.config.InetNetwork;
import com.wireguard.config.Interface;
import com.wireguard.config.Peer;

import java.net.InetAddress;

/**
 * Actual VPN Service that creates the VPN tunnel using Android VpnService API
 * This is what GoBackend uses to create the VPN interface
 */
public class AxeWireGuardVpnService extends VpnService {
    private static final String TAG = "AxeWireGuardVpnService";
    
    public static final String ACTION_CONNECT = "com.axevpn.wireguard.vpn.CONNECT";
    public static final String ACTION_DISCONNECT = "com.axevpn.wireguard.vpn.DISCONNECT";
    public static final String EXTRA_CONFIG = "config";
    public static final String EXTRA_TUNNEL_NAME = "tunnelName";
    
    private static AxeWireGuardVpnService instance;
    private ParcelFileDescriptor vpnInterface;
    private String currentTunnelName;
    
    public static AxeWireGuardVpnService getInstance() {
        return instance;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        android.util.Log.d(TAG, "VPN Service created");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            
            if (ACTION_CONNECT.equals(action)) {
                String configStr = intent.getStringExtra(EXTRA_CONFIG);
                currentTunnelName = intent.getStringExtra(EXTRA_TUNNEL_NAME);
                if (currentTunnelName == null) {
                    currentTunnelName = "AxeVPN";
                }
                
                if (configStr != null) {
                    try {
                        android.util.Log.i(TAG, "Starting VPN tunnel: " + currentTunnelName);
                        Config config = Config.parse(new java.io.ByteArrayInputStream(
                            configStr.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                        establishVpnInterface(config);
                    } catch (Exception e) {
                        android.util.Log.e(TAG, "Failed to parse config", e);
                        stopSelf();
                    }
                } else {
                    android.util.Log.e(TAG, "No config provided");
                    stopSelf();
                }
                
            } else if (ACTION_DISCONNECT.equals(action)) {
                android.util.Log.i(TAG, "Disconnect action received");
                disconnect();
            }
        }
        
        return START_NOT_STICKY;
    }
    
    private void establishVpnInterface(Config config) {
        try {
            Interface iface = config.getInterface();
            Builder builder = new Builder();
            
            // Set interface addresses
            for (InetNetwork addr : iface.getAddresses()) {
                builder.addAddress(addr.getAddress(), addr.getMask());
                android.util.Log.d(TAG, "Added address: " + addr.getAddress() + "/" + addr.getMask());
            }
            
            // Set DNS servers
            for (InetAddress dns : iface.getDnsServers()) {
                builder.addDnsServer(dns);
                android.util.Log.d(TAG, "Added DNS: " + dns);
            }
            
            // Set routes from peers
            for (Peer peer : config.getPeers()) {
                for (InetNetwork allowedIp : peer.getAllowedIps()) {
                    builder.addRoute(allowedIp.getAddress(), allowedIp.getMask());
                    android.util.Log.d(TAG, "Added route: " + allowedIp.getAddress() + "/" + allowedIp.getMask());
                }
            }
            
            // Set MTU
            if (iface.getMtu().isPresent()) {
                builder.setMtu(iface.getMtu().get());
                android.util.Log.d(TAG, "Set MTU: " + iface.getMtu().get());
            } else {
                builder.setMtu(1280); // Default WireGuard MTU
            }
            
            // Set session name
            builder.setSession(currentTunnelName);
            builder.setBlocking(false);
            
            // Establish VPN interface
            if (vpnInterface != null) {
                try {
                    vpnInterface.close();
                } catch (Exception e) {
                    android.util.Log.w(TAG, "Error closing old interface", e);
                }
            }
            
            vpnInterface = builder.establish();
            
            if (vpnInterface == null) {
                android.util.Log.e(TAG, "❌ Failed to establish VPN interface - permission denied?");
                stopSelf();
                return;
            }
            
            android.util.Log.i(TAG, "✅ VPN interface established successfully");
            
            // Note: GoBackend will handle the actual WireGuard protocol
            // This service just creates the VPN interface
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to establish VPN", e);
            stopSelf();
        }
    }
    
    private void disconnect() {
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
                android.util.Log.i(TAG, "VPN interface closed");
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error closing VPN interface", e);
            }
            vpnInterface = null;
        }
        stopSelf();
    }
    
    @Override
    public void onDestroy() {
        disconnect();
        instance = null;
        android.util.Log.d(TAG, "VPN Service destroyed");
        super.onDestroy();
    }
    
    @Override
    public void onRevoke() {
        android.util.Log.w(TAG, "VPN permission revoked");
        disconnect();
        super.onRevoke();
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
