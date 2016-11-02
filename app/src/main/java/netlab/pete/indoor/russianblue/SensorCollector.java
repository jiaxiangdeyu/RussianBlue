package netlab.pete.indoor.russianblue;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Calendar;

/**
 * Created by wenping on 2016/10/27.
 */

public class SensorCollector implements SensorEventListener {
    public static final String [] SENSOR_IDS = {"acce", "gyro", "magn"};
    public enum Sensor_Type {
        ACCE, GYRO, MAGN
    }
    public static final Sensor_Type [] SENSORTYPE_SEQUENCE = {Sensor_Type.ACCE, Sensor_Type.GYRO, Sensor_Type.MAGN};
    public static final float ORIENTATION_DISABLE = -100f;

    private final SensorManager m_sensorManager;
    private Sensor [] m_sensors;    // 0 Accelerometer, 1 Gyroscope, 2 Magnetometer

    private ArrayList<String> [] m_sensorValueLists;

    // For orientation calculation, we keep the last sensor values.
    private float [] m_lastAcceValue;
    private float [] m_lastMagnValue;
    private float [][] m_lastSensorValues;

    private StepCounter m_stepCounter;





    public SensorCollector(SensorManager sensorManager, StepCounter stepCounter) {
        m_sensorManager = sensorManager;
        m_sensors = new Sensor[SENSOR_IDS.length];
        m_sensors[Sensor_Type.ACCE.ordinal()] = m_sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        m_sensors[Sensor_Type.GYRO.ordinal()] = m_sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        m_sensors[Sensor_Type.MAGN.ordinal()] = m_sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        m_sensorValueLists = new ArrayList[SENSOR_IDS.length];
        for (int i = 0; i < SENSOR_IDS.length; i++) {
            m_sensorValueLists[i] = new ArrayList<String>();
        }
        m_lastAcceValue = new float[3];
        m_lastMagnValue = new float[3];
        m_lastSensorValues = new float[SENSOR_IDS.length][3];

        m_stepCounter = stepCounter;
    }

    /**
     * Please notify multi-thread security.
     * */
    public void resetDataSpace() {
        synchronized (this) {
            for (int i = 0; i < SENSOR_IDS.length; i++) {
                m_sensorValueLists[i].clear();
            }
        }
    }

    public void registerEventListener() {
        resetDataSpace();
        if (m_sensorManager != null) {
            for (int i = 0; i < SENSOR_IDS.length; i++) {
                m_sensorManager.registerListener(this, m_sensors[i], SensorManager.SENSOR_DELAY_GAME);
            }
        }
    }

    public void unregisterEventListener() {
        if (m_sensorManager != null) {
            if (m_sensorManager != null) {
                for (int i = 0; i < SENSOR_IDS.length; i++) {
                    m_sensorManager.unregisterListener(this, m_sensors[i]);
                }
            }
        }
    }
    /**
     * Called when there is a new sensor event.  Note that "on changed"
     * is somewhat of a misnomer, as this will also be called if we have a
     * new reading from a sensor with the exact same sensor values (but a
     * newer timestamp).
     * <p>
     * <p>See {@link SensorManager SensorManager}
     * for details on possible sensor types.
     * <p>See also {@link SensorEvent SensorEvent}.
     * <p>
     * <p><b>NOTE:</b> The application doesn't own the
     * {@link SensorEvent event}
     * object passed as a parameter and therefore cannot hold on to it.
     * The object may be part of an internal pool and may be reused by
     * the framework.
     *
     * @param event the {@link SensorEvent SensorEvent}.
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;
        if (sensor == null)
            return;
        Calendar calendar = Calendar.getInstance();
        Long timeStamp = calendar.getTimeInMillis();
        StringBuilder strBuf = new StringBuilder();
        int sensorType = sensor.getType();

        // Accelerometer - Oriention and Step Counter
        if (sensorType == sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, m_lastAcceValue, 0, event.values.length);
            strBuf.append(timeStamp);
            strBuf.append(", ");
            strBuf.append(event.values[0]);
            strBuf.append(", ");
            strBuf.append(event.values[1]);
            strBuf.append(", ");
            strBuf.append(event.values[2]);
            strBuf.append('\n');
            m_sensorValueLists[Sensor_Type.ACCE.ordinal()].add(strBuf.toString());
            m_stepCounter.addAcceValue(timeStamp, event.values);

            if (m_sensorValueLists[Sensor_Type.MAGN.ordinal()].size() > 0) {
                float azimut = calculateOrientation();
                if (azimut != ORIENTATION_DISABLE) {
                    m_stepCounter.addDirectionValue(timeStamp, azimut);
                }
            }

        }
        else if (sensorType == sensor.TYPE_GYROSCOPE) {
            strBuf.append(timeStamp);
            strBuf.append(", ");
            strBuf.append(event.values[0]);
            strBuf.append(", ");
            strBuf.append(event.values[1]);
            strBuf.append(", ");
            strBuf.append(event.values[2]);
            strBuf.append('\n');
            m_sensorValueLists[Sensor_Type.GYRO.ordinal()].add(strBuf.toString());
        }
        else if (sensorType == sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, m_lastMagnValue, 0, event.values.length);
            strBuf.append(timeStamp);
            strBuf.append(", ");
            strBuf.append(event.values[0]);
            strBuf.append(", ");
            strBuf.append(event.values[1]);
            strBuf.append(", ");
            strBuf.append(event.values[2]);
            strBuf.append('\n');
            m_sensorValueLists[Sensor_Type.MAGN.ordinal()].add(strBuf.toString());

            if (m_sensorValueLists[Sensor_Type.ACCE.ordinal()].size() > 0) {
                float azimut = calculateOrientation();
                if (azimut != ORIENTATION_DISABLE) {
                    m_stepCounter.addDirectionValue(timeStamp, azimut);
                }
            }
        }

    }

    private float calculateOrientation() {
        float R[] = new float[9];
        float I[] = new float[9];

        float azimut = ORIENTATION_DISABLE;
        boolean success = SensorManager.getRotationMatrix(R, I, m_lastAcceValue, m_lastMagnValue);
        if (success) {
            float orientation[] = new float[3];
            SensorManager.getOrientation(R, orientation);
            azimut = orientation[0];
        }
        return azimut;
    }

    /**
     * Called when the accuracy of the registered sensor has changed.  Unlike
     * onSensorChanged(), this is only called when this accuracy value changes.
     * <p>
     * <p>See the SENSOR_STATUS_* constants in
     * {@link SensorManager SensorManager} for details.
     *
     * @param sensor
     * @param accuracy The new accuracy of this sensor, one of
     *                 {@code SensorManager.SENSOR_STATUS_*}
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    /**
     * Save sensors' values to external storage file, before calling this method,
     * we should promise the existence of parent directories.
     * Notice: This should be called in non-UI thread.
     * @param fileName The absolute file path of storage target
     * */
    public boolean toExternalStorage(String fileName, Sensor_Type type) {
        boolean saveFlag = true;
        int sensorIndex = 0;
        for (int i = 0; i < SENSORTYPE_SEQUENCE.length; i++) {
            if (type == SENSORTYPE_SEQUENCE[i]) {
                sensorIndex = i;
                break;
            }
        }
        try {
            File wifiFile = new File(fileName);
            if (!wifiFile.exists()) {
                wifiFile.createNewFile();
            }

            FileWriter fw = new FileWriter(wifiFile);
            for (String sensorValue : m_sensorValueLists[sensorIndex]) {
                fw.append(sensorValue);
            }
            fw.flush();
            fw.close();
        }
        catch (Exception ex) {
            saveFlag = false;
            ex.printStackTrace();
            Log.d(SENSOR_IDS[sensorIndex], "Exception throwed from saving sensor values method");
        }
        return saveFlag;
    }
}
