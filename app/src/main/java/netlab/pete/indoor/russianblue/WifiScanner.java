package netlab.pete.indoor.russianblue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Created by wenping on 2016/10/27.
 */

public class WifiScanner {
    public static final String SENSOR_ID = "wifi";

    private final Context m_appContext;
    private final WifiManager m_wifiManager;
    private boolean m_wifiState;
    private BroadcastReceiver m_wifiScanReceiver;
    private ArrayList<String> m_wifiRSSList;

    public WifiScanner(Context context, WifiManager wifiManager) {
        m_appContext = context;
        m_wifiManager = wifiManager;
        m_wifiScanReceiver = null;
        m_wifiRSSList = new ArrayList<String>();;
    }
    /**
     * Please notify multi-thread security.
     * */
    public void resetDataSpace() {
        synchronized (this) {
            m_wifiRSSList.clear();
        }
    }
    /**
     *  This should be called in non-UI thread and this method will block its owner thread.
     * */
    public void startScan() {
        // Save the WiFi state
        m_wifiState = m_wifiManager.isWifiEnabled();
        if (!m_wifiState) {
            m_wifiManager.setWifiEnabled(true);
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                ex.printStackTrace();
                Log.d(SENSOR_ID, "WiFi scanner thread failed.");
            }
        }

        resetDataSpace();

        // Define broadcast receiver and register this receiver according to intent filter
        m_wifiScanReceiver =  new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                List<ScanResult> result = m_wifiManager.getScanResults();
                Calendar calendar = Calendar.getInstance();
                StringBuilder sb = new StringBuilder();
                sb.append(calendar.getTimeInMillis());
                for(ScanResult record : result) {
                    sb.append(", ");
                    sb.append(record.BSSID + ":" + record.level);
                }
                sb.append('\n');
                m_wifiRSSList.add(sb.toString());
                // Restart scanning.
                m_wifiManager.startScan();
            }
        };
        IntentFilter wifiFilter = new IntentFilter();
        wifiFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        m_appContext.registerReceiver(m_wifiScanReceiver, wifiFilter);
        // Don't forget to start the first scan trigger.
        m_wifiManager.startScan();
    }
    public void stopScan() {
        m_appContext.unregisterReceiver(m_wifiScanReceiver);
        // Restore the WiFi state
        m_wifiManager.setWifiEnabled(m_wifiState);
    }

    /**
     * Save WiFi signals' values to external storage file, before calling this method,
     * we should promise the existence of parent directories.
     * Notice: This should be called in non-UI thread.
     * @param fileName The absolute file path of storage target
     * */
    public boolean toExternalStorage(String fileName) {
        boolean saveFlag = true;
        if (m_wifiRSSList.size() > 0) {
            try {
                File wifiFile = new File(fileName);
                if (!wifiFile.exists()) {
                    wifiFile.createNewFile();
                }
                FileWriter fw = new FileWriter(wifiFile);
                for (String wifiRecord : m_wifiRSSList) {
                    fw.append(wifiRecord);
                }
                fw.flush();
                fw.close();
            }
            catch (Exception ex) {
                saveFlag = false;
                ex.printStackTrace();
                Log.d(SENSOR_ID, "Exception throwed from save wifi signals values method");
            }
        }
        return saveFlag;
    }
}
