package netlab.pete.indoor.russianblue;

import android.graphics.PointF;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by wenping on 2016/10/27.
 */

public class StepCounter {
    // Key values for bundle message data
    public final static String FOOT_CURRENTX = "currentx";
    public final static String FOOT_CURRENTY = "currenty";
    public final static String STEP_COUNT = "stepcount";
    // Parameters for step counting algorithm
    private final static int SLIDINGWINDOW_SIZE = 7;
    private final static float ACCE_MAXTHRESHOLD = 10.5f;
    private final static long STEP_MINPERIOD = 300;
    // Timer parameter for counting
    private final static int FOOT_DURATION = 500;
    private final static int STEP_DURATION = 150;
    private final WalkingTrackView m_wtView;

    public class AcceData {
        private Long m_timeStamp;
        private float m_acceValue;

        public Long getTimeStamp() {
            return m_timeStamp;
        }
        public void setTimeStamp(Long timeStamp) {
            this.m_timeStamp = timeStamp;
        }

        public float getAcceValue() { return  m_acceValue; }

        public AcceData(Long timeStamp, final float acceValue) {
            this.m_timeStamp = timeStamp;
            this.m_acceValue = acceValue;
        }
        public AcceData(Long timeStamp, final float [] acceValues) {
            this.m_timeStamp = timeStamp;
            float x = acceValues[0];
            float y = acceValues[1];
            float z = acceValues[2];

            this.m_acceValue = (float)Math.sqrt(x * x + y * y + z * z);
        }

        public String toString() {
            return Long.toString(m_timeStamp) + ", " + Float.toString(m_acceValue) + "\n";
        }
    }

    public class Direction {
        private Long m_timeStamp;
        private float m_azimut;

        public Long getTimeStamp() {
            return m_timeStamp;
        }
        public void setTimeStamp(Long timeStamp) {
            this.m_timeStamp = timeStamp;
        }
        public float getDirection() { return  m_azimut; }

        public  Direction(Long timeStamp, final float azimut) {
            this.m_timeStamp = timeStamp;
            this.m_azimut = azimut;
        }

        public String toString() {
            return Long.toString(m_timeStamp) + ", " + Float.toString(m_azimut) + "\n";
        }
    }

    public class StepData{
        private Long m_timeStamp;
        private float m_peakValue;

        public Long getTimeStamp() {
            return m_timeStamp;
        }
        public void setTimeStamp(Long timeStamp) {
            this.m_timeStamp = timeStamp;
        }
        public float getPeakValue() {
            return m_peakValue;
        }
        public void setPeakValue(float peakValue) {
            this.m_peakValue = peakValue;
        }

        public StepData(Long timeStamp, float peakValue) {
            this.m_timeStamp = timeStamp;
            this.m_peakValue = peakValue;
        }
    }


    private ArrayList<AcceData> m_acceList;
    private ArrayList<AcceData> m_filteredAcceList;
    private int m_indexofFilterAcce;
    private ArrayList<StepData> m_stepDataList;
    private ArrayList<Direction> m_directionList;
    private int m_indexofTime;

    private Timer m_footTimer;
    private Timer m_stepTimer;

    private float m_stepLength;
    private ArrayList<PointF> m_stepSequence;

    private TimerTask m_footTimerTask;
    private TimerTask m_stepTimerTask;

    public StepCounter(WalkingTrackView view, float stepLength) {
        m_wtView = view;
        m_acceList = new ArrayList<>();
        m_filteredAcceList = new ArrayList<>();
        m_indexofFilterAcce = 0;
        m_indexofTime = 0;
        m_stepDataList = new ArrayList<>();
        m_directionList = new ArrayList<>();
        m_stepLength = stepLength;
        m_stepSequence = new ArrayList<>();
        m_footTimer = new Timer();
        m_stepTimer = new Timer();
        m_footTimerTask = null;
        m_stepTimerTask = null;

        //m_stepLength = 0.65f;

    }

    public void start() {
        m_acceList.clear();
        m_filteredAcceList.clear();
        m_indexofFilterAcce = 0;
        m_stepDataList.clear();
        m_directionList.clear();
        m_indexofTime = 0;
        m_stepSequence.clear();

        m_footTimerTask = new TimerTask() {
            @Override
            public void run() {
                PointF currentPoint = m_stepSequence.get(m_stepSequence.size() - 1);
                Message msg = new Message();
                msg.what = WalkingTrackView.MSG_FOOT_UPDATE;
                Bundle bundle = new Bundle();
                bundle.putFloat(FOOT_CURRENTX, currentPoint.x);
                bundle.putFloat(FOOT_CURRENTY, currentPoint.y);
                msg.setData(bundle);
                m_wtView.getWalkingViewHandler().sendMessage(msg);
            }
        };
        m_stepTimerTask = new TimerTask() {
            @Override
            public void run() {
                if (exploreOneStep()) {
                    PointF currentPoint = m_stepSequence.get(m_stepSequence.size() - 1);
                    Message msg = new Message();
                    msg.what = WalkingTrackView.MSG_STEP_UPDATE;
                    Bundle bundle = new Bundle();
                    bundle.putFloat(FOOT_CURRENTX, currentPoint.x);
                    bundle.putFloat(FOOT_CURRENTY, currentPoint.y);
                    bundle.putInt(STEP_COUNT, m_stepDataList.size());
                    msg.setData(bundle);
                    m_wtView.getWalkingViewHandler().sendMessage(msg);
                }
            }
        };
        // Add the starting location.
        m_stepSequence.add(new PointF(0.f, 0.f));
        m_footTimer = new Timer();
        m_stepTimer = new Timer();
        m_footTimer.schedule(m_footTimerTask, 10, FOOT_DURATION);
        m_stepTimer.schedule(m_stepTimerTask, 10, STEP_DURATION);
    }

    public void stop() {
        m_footTimer.cancel();
        m_stepTimer.cancel();

        //Debug
        boolean saveFlag = toExternalStorage();
        if (saveFlag) {
            Log.d("counterdebug", "save debug file success.");
        }
    }

    public boolean toExternalStorage() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
        String currentDateStr = format.format(new Date());
        String fileName = "/sdcard/RussianBlue/" + currentDateStr + "debug_filtered.txt";
        boolean saveFlag = true;
        if (m_filteredAcceList.size() > 0) {
            try {
                File wifiFile = new File(fileName);
                if (!wifiFile.exists()) {
                    wifiFile.createNewFile();
                }
                FileWriter fw = new FileWriter(wifiFile);
                for (AcceData acceValue : m_filteredAcceList) {
                    fw.append(acceValue.toString());
                }
                fw.flush();
                fw.close();
            }
            catch (Exception ex) {
                saveFlag = false;
                ex.printStackTrace();
                Log.d("counterdebug", "Exception throwed from save filtered accelerometer values method");
            }
        }
        fileName = "/sdcard/RussianBlue/" + currentDateStr + "debug_orientation.txt";
        if (m_directionList.size() > 0) {
            try {
                File wifiFile = new File(fileName);
                if (!wifiFile.exists()) {
                    wifiFile.createNewFile();
                }
                FileWriter fw = new FileWriter(wifiFile);
                for (Direction orient : m_directionList) {
                    fw.append(orient.toString());
                }
                fw.flush();
                fw.close();
            }
            catch (Exception ex) {
                saveFlag = false;
                ex.printStackTrace();
                Log.d("counterdebug", "Exception throwed from save orientation values method");
            }
        }

        return saveFlag;
    }
    public void addDirectionValue(Long timeStamp, final float azimut) {
        Direction direction = new Direction(timeStamp, azimut);
        m_directionList.add(direction);
    }

    public void addAcceValue(Long timeStamp, final float [] acceValues) {
        m_acceList.add(new AcceData(timeStamp, acceValues));
        // Sliding window algorithm to filter the raw accelerometer data.
        int num = m_acceList.size();
        if (num < SLIDINGWINDOW_SIZE)
            return;
        int end = m_acceList.size()-1;
        float avgValue = 0.0f;
        Long avgTime = 0L ;
        for (int i = 0; i < SLIDINGWINDOW_SIZE; i ++) {
            avgValue += m_acceList.get(end - i).getAcceValue();
        }
        avgValue /= SLIDINGWINDOW_SIZE;
        // Multiply a factor so the range will be larger between the bottom and the up
        float  gravity = 9.45f;
        avgValue = (avgValue - gravity) * (2.5f) + gravity;
        avgTime = m_acceList.get(end - (SLIDINGWINDOW_SIZE / 2)).getTimeStamp();
        AcceData filteredData = new AcceData(avgTime, avgValue);
        m_filteredAcceList.add(filteredData);
    }
    public boolean exploreOneStep() {
        int endIndex = m_filteredAcceList.size() - 1;
        int currentIndex = m_indexofFilterAcce;
        // The last 2 samples do not affect the step counting result
        if (endIndex - currentIndex < 2) {
            return false;
        }
        // Get peak value
        float value = m_filteredAcceList.get(currentIndex).getAcceValue();
        float nextValue = m_filteredAcceList.get(currentIndex + 1).getAcceValue();
        while (nextValue <= value) {
            value = nextValue;
            currentIndex = currentIndex + 1;
            if (currentIndex >= endIndex) {
                // Update the start position for next exploring
                m_indexofFilterAcce = currentIndex;
                return false;
            }
            nextValue = m_filteredAcceList.get(currentIndex + 1).getAcceValue();
        }
        while (nextValue >= value) {
            value = nextValue;
            currentIndex = currentIndex + 1;
            if (currentIndex >= endIndex) {
                // Go back to the last position so that we will not pass the peak value right in the end.
                m_indexofFilterAcce = currentIndex - 1;
                return false;
            }
            nextValue = m_filteredAcceList.get(currentIndex + 1).getAcceValue();
        }
        // Update the start position for next step exploring
        m_indexofFilterAcce = currentIndex + 1;
        // Just the lower peak in the through
        if (value  <= ACCE_MAXTHRESHOLD) {
            return false;
        }
        // Step counter algorithm: threshold value and time frequency
        Long timeStamp = m_filteredAcceList.get(currentIndex).getTimeStamp();
        if (m_stepDataList.size() == 0) {
            m_stepDataList.add(new StepData(timeStamp, value));
            // Get orientation for this step
            float azimut = getOrientation(timeStamp);
            float preX = m_stepSequence.get(m_stepSequence.size() - 1).x;
            float preY = m_stepSequence.get(m_stepSequence.size() - 1).y;
            PointF currentLocation = new PointF();
            currentLocation.set(preX + m_stepLength * (float)Math.sin(azimut),
                    preY + m_stepLength * (float)Math.cos(azimut));
            m_stepSequence.add(currentLocation);
            return true;
        }
        Long lastTimeStamp = m_stepDataList.get(m_stepDataList.size() - 1).getTimeStamp();
        if (timeStamp - lastTimeStamp >= STEP_MINPERIOD) {
            m_stepDataList.add(new StepData(timeStamp, value));
            // Get orientation for this step
            float azimut = getOrientation(timeStamp);
            float preX = m_stepSequence.get(m_stepSequence.size() - 1).x;
            float preY = m_stepSequence.get(m_stepSequence.size() - 1).y;
            PointF currentLocation = new PointF();
            currentLocation.set(preX + m_stepLength * (float)Math.sin(azimut),
                    preY + m_stepLength * (float)Math.cos(azimut));
            m_stepSequence.add(currentLocation);
            return true;
        }
        else {
            float lastPeakValue = m_stepDataList.get(m_stepDataList.size() - 1).getPeakValue();
            if (value >= lastPeakValue) {
                m_stepDataList.get(m_stepDataList.size() - 1).setTimeStamp(timeStamp);
                m_stepDataList.get(m_stepDataList.size() - 1).setPeakValue(value);
                // TODO: update the last step sequence data.
            }
        }
        return false;
    }

    private float getOrientation(Long timeStamp) {
        int index = m_indexofTime;
        float azimut = m_directionList.get(index).getDirection();
        while (index < m_directionList.size()) {
            if (m_directionList.get(index).getTimeStamp() > timeStamp) {
                int preIndex = Math.max(0, index - 1);
                azimut = (m_directionList.get(index).getDirection() + m_directionList.get(preIndex).getDirection()) / 2;
                break;
            }
            index = index + 1;
        }
        if (index == m_directionList.size()) {
            index = index - 1;
            azimut = m_directionList.get(index).getDirection();
        }
        m_indexofTime = index;
        return  azimut;
    }
}
