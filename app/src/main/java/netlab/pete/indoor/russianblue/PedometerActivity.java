package netlab.pete.indoor.russianblue;

import android.app.Activity;
import android.content.Context;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PedometerActivity extends Activity {
    public final static String PDTAG = "pedometer";
    public final static String EXTERNAL_DIRECTORYPATH = "/sdcard/RussianBlue/";
    private final static int TARGETDATA_TYPECOUNT = 4; // wifi, acce, gyro, magn

    private Context m_appContext;
    private EditText m_edtStepLength;
    private CheckBox m_cbWiFi;
    private CheckBox m_cbAcce;
    private CheckBox m_cbGyro;
    private CheckBox m_cbMagn;
    private boolean [] saveFlags;
    private Button m_btnControl;
    private boolean m_controlFlag;  // false : waiting to start, true : waiting to stop
    private WalkingTrackView m_walkingTV;
    private StepCounter m_stepCounter;

    private WifiManager m_wifiManager;
    private WifiScanner m_wifiScanner;
    private SensorManager m_sensorManager;
    private SensorCollector m_sensorCollector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        m_appContext = getApplicationContext();

        m_edtStepLength = (EditText) findViewById(R.id.edtLength);
        m_cbWiFi = (CheckBox) findViewById(R.id.cbWiFi);
        m_cbAcce = (CheckBox) findViewById(R.id.cbAcce);
        m_cbGyro = (CheckBox) findViewById(R.id.cbGyro);
        m_cbMagn = (CheckBox) findViewById(R.id.cbMagn);
        saveFlags = new boolean[TARGETDATA_TYPECOUNT];
        for (int i = 0; i < TARGETDATA_TYPECOUNT; i++) {
            saveFlags[i] = true;
        }
        m_btnControl = (Button) findViewById(R.id.btnControl);
        m_controlFlag = false;
        m_walkingTV = (WalkingTrackView) findViewById(R.id.walkingTV);
        String stepLengthTxt = m_edtStepLength.getText().toString();
        m_stepCounter = new StepCounter(m_walkingTV, Float.valueOf(stepLengthTxt));

        m_wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        m_wifiScanner = new WifiScanner(m_appContext, m_wifiManager);
        m_sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        m_sensorCollector = new SensorCollector(m_sensorManager, m_stepCounter);

        // To start or stop the data sensing process.
        m_btnControl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (m_controlFlag) {    // stop -> start, program will stop listening the sensors
                    m_controlFlag = false;
                    m_btnControl.setText(R.string.btn_titlestart);
                    // Stop to scan WiFi signals
                    m_wifiScanner.stopScan();
                    // Stop to monitor sensor values
                    m_sensorCollector.unregisterEventListener();
                    m_stepCounter.stop();

                    m_cbWiFi.setEnabled(true);
                    m_cbAcce.setEnabled(true);
                    m_cbGyro.setEnabled(true);
                    m_cbMagn.setEnabled(true);


                    // Save the collected values to external storage file.
                    SaveFileThread sft = new SaveFileThread(saveFlags);
                    sft.start();


                }
                else {      // start -> stop, program will start listening the sensors
                    m_controlFlag = true;
                    m_btnControl.setText(R.string.btn_titlestop);
                    m_cbWiFi.setEnabled(false);
                    m_cbAcce.setEnabled(false);
                    m_cbGyro.setEnabled(false);
                    m_cbMagn.setEnabled(false);

                    saveFlags[0] = m_cbWiFi.isChecked();
                    saveFlags[1] = m_cbAcce.isChecked();
                    saveFlags[2] = m_cbGyro.isChecked();
                    saveFlags[3] = m_cbMagn.isChecked();

                    // Start to scan WiFi signals
                    WifiScanThread wst = new WifiScanThread();
                    wst.start();
                    // Start to monitor sensor values
                    m_walkingTV.initWalkingPath();
                    m_sensorCollector.registerEventListener();
                    m_stepCounter.start();
                }
            }
        });

        Log.d(PDTAG, "onCreate");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(PDTAG, "onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(PDTAG, "onStop");
    }

    /**
     * Show toast message despite of non-UI threads.
     * */
    public void showToast(final String toastMsg)
    {
        runOnUiThread(new Runnable() {
            public void run()
            {
                Toast.makeText(PedometerActivity.this, toastMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    class WifiScanThread extends Thread {
        @Override
        public void run() {
            super.run();
            m_wifiScanner.startScan();
        }
    }

    class SaveFileThread extends Thread {
        private boolean m_wifiSaveFlag = true;
        private boolean [] m_sensorSaveFlags = {true, true, true};

        public SaveFileThread(boolean [] saveFlags) {
            super();
            if (saveFlags.length >= 4) {
                m_wifiSaveFlag = saveFlags[0];
                m_sensorSaveFlags[0] = saveFlags[1];
                m_sensorSaveFlags[1] = saveFlags[2];
                m_sensorSaveFlags[2] = saveFlags[3];
            }

        }

        @Override
        public void run() {
            super.run();
            boolean saveFlag = saveValueToExternalStorage();
            if (saveFlag) {
                showToast(getString(R.string.file_ok));
            }
            else {
                showToast(getString(R.string.file_error));
            }
        }

        /**
         * Save the collected data to external storage.
         * */
        private boolean saveValueToExternalStorage() {
            boolean saveFlag = false;
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
            String currentDateStr = format.format(new Date());
            try {
                File appExternalDirectory = new File(PedometerActivity.EXTERNAL_DIRECTORYPATH);
                boolean hasParentDirectory = appExternalDirectory.exists();
                if (!hasParentDirectory) {
                    hasParentDirectory = appExternalDirectory.mkdirs();
                }
                if (hasParentDirectory)
                    Log.d(PDTAG, "Now we have the application directory in external storage");
                else {
                    Log.d(PDTAG, "Failed to create the application directory in external storage");
                    return saveFlag;
                }

                if (m_wifiSaveFlag) {
                    String wifiFilePath = PedometerActivity.EXTERNAL_DIRECTORYPATH
                            + currentDateStr + "_" + WifiScanner.SENSOR_ID + ".txt";
                    saveFlag = m_wifiScanner.toExternalStorage(wifiFilePath);
                    if (!saveFlag) {
                        Log.d(PDTAG, "Failed to save WiFi RSS values to external storage");
                        return saveFlag;
                    }
                }

                String sensorFilePath = null;
                SensorCollector.Sensor_Type [] sensors = {SensorCollector.Sensor_Type.ACCE,
                        SensorCollector.Sensor_Type.GYRO, SensorCollector.Sensor_Type.MAGN};
                for (int i = 0; i < SensorCollector.SENSOR_IDS.length; i++) {
                    if (m_sensorSaveFlags[i]) {
                        sensorFilePath = PedometerActivity.EXTERNAL_DIRECTORYPATH
                                + currentDateStr + "_" + SensorCollector.SENSOR_IDS[i] + ".txt";
                        saveFlag = m_sensorCollector.toExternalStorage(sensorFilePath, sensors[i]);
                        if (!saveFlag) {
                            Log.d(PDTAG, "Failed to save sensor values to external storage");
                            return saveFlag;
                        }
                    }
                }

            }
            catch (Exception ex) {
                saveFlag = false;
                ex.printStackTrace();
            }
            return saveFlag;
        }
    }

}
