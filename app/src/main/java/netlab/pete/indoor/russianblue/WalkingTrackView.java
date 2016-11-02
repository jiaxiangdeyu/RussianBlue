package netlab.pete.indoor.russianblue;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

/**
 * Created by wenping on 2016/10/26.
 */

public class WalkingTrackView extends ImageView {
    public final static float FLT_EPSILON = 0.0000001f;
    public final static int MSG_FOOT_UPDATE = 1;
    public final static int MSG_STEP_UPDATE = 2;
    private final static int COORDINATE_TEXTSIZE = 30;
    private final static int COORDINATE_STROKEWIDTH = 3;
    private final static int INFORMATION_TEXTSIZE = 45;
    private final static int TRACK_STROKEWIDTH = 4;
    private final static float TRACK_MINSCALE = 0.3f;
    private final static float TRACK_MAXSCALE = 3.f;

    private final static float MAP_SCALE = 20 / 0.65f;  // (pt/m)

    private static float Real2Map(float value) { return  value * MAP_SCALE; }

    private final static float [] FOOT_SIZES = {16.f, 2.f, 4.f, 4.f, 3.f};
    private final static int [] FOOT_COLORS = {Color.CYAN, Color.GREEN, Color.MAGENTA, Color.YELLOW, Color.BLUE};

    private Paint m_trackPaint;
    private float m_scale;
    private float m_dX;
    private float m_dY;

    private Path m_walkingPath;
    private int m_footColorIndex;
    private PointF m_footLocation;
    private int m_stepNum;
    private WalkingViewHandler m_wvHandler;

    private enum Action_Mode {
        INIT, DRAG, ZOOM
    }

    private final class TouchListener implements OnTouchListener {

        private Action_Mode m_motionMode = Action_Mode.INIT;
        // Drag and Scale
        private PointF m_startPoint = new PointF();
        private float m_startDistance;

        // Variables for drag and scale parameters
        private float m_dX = 0.f;
        private float m_dY = 0.f;
        private float m_scale = 1.f;

        private float m_dLastX = 0.f;
        private float m_dLastY = 0.f;
        private float m_lastScale = 1.f;

        /**
         * Called when a touch event is dispatched to a view. This allows listeners to
         * get a chance to respond before the target view.
         *
         * @param v     The view the touch event has been dispatched to.
         * @param event The MotionEvent object containing full information about
         *              the event.
         * @return True if the listener has consumed the event, false otherwise.
         */
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:   // One Finger Down
                    m_motionMode = Action_Mode.DRAG;
                    m_startPoint.set(event.getX(), event.getY());
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:   // Another Finger Down, now we have two fingers down
                    m_motionMode = Action_Mode.ZOOM;
                    m_startDistance = distance(event);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (m_motionMode == Action_Mode.DRAG) {
                        m_dX = event.getX() - m_startPoint.x; // Translation on x axis
                        m_dY = event.getY() - m_startPoint.y; // Translation on y axis
                    }
                    else if (m_motionMode == Action_Mode.ZOOM) {
                        float endDistance = distance(event);
                        // Slight touch will be ignored.
                        if (endDistance > 10.f && m_startDistance > 10.f) {
                            m_scale = endDistance / m_startDistance; // Zoom scale
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    m_motionMode = Action_Mode.INIT;
                    m_dLastX += m_dX;
                    m_dLastY += m_dY;
                    m_dX = 0.f;
                    m_dY = 0.f;
                    m_lastScale *= m_scale;
                    m_scale = 1.f;
                    m_lastScale = Math.max(TRACK_MINSCALE, m_lastScale);
                    m_lastScale = Math.min(TRACK_MAXSCALE, m_lastScale);
                    break;
                default:
                    break;

            }
            WalkingTrackView.this.updateCanvasParameters(m_lastScale * m_scale, m_dLastX + m_dX, m_dLastY + m_dY);
            return true;
        }
        private float distance(MotionEvent event) {
            float dX = event.getX(1) - event.getX(0);
            float dY = event.getY(1) - event.getY(0);
            return (float) Math.sqrt(dX * dX + dY * dY);
        }

    }

    private void updateCanvasParameters(float scale, float dX, float dY) {
        scale = Math.max(TRACK_MINSCALE, scale);
        scale = Math.min(TRACK_MAXSCALE, scale);
        this.m_scale = scale;
        this.m_dX = dX;
        this.m_dY = dY;
        invalidate();
    }

    public final class WalkingViewHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_FOOT_UPDATE: {
                    float x = Real2Map(msg.getData().getFloat(StepCounter.FOOT_CURRENTX));
                    float y = 0 - Real2Map(msg.getData().getFloat(StepCounter.FOOT_CURRENTY));
//                    if (Math.abs(m_footLocation.x - x) > FLT_EPSILON
//                            || Math.abs(m_footLocation.y - y) > FLT_EPSILON ) {
//
//                    }
                    updateWalkingFoot(x, y);
                    break;
                }
                case MSG_STEP_UPDATE: {
                    float x = Real2Map(msg.getData().getFloat(StepCounter.FOOT_CURRENTX));
                    float y = 0 - Real2Map(msg.getData().getFloat(StepCounter.FOOT_CURRENTY));
                    int num = msg.getData().getInt(StepCounter.STEP_COUNT);
                    updateWalkingTrack(x, y, num);
                    break;
                }
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    }

    public WalkingViewHandler getWalkingViewHandler() {
        return m_wvHandler;
    }
    private void initPaint() {
        // Set walking track path style.
        m_trackPaint = new Paint();
        m_trackPaint.setColor(Color.BLUE);
        m_trackPaint.setStrokeWidth(TRACK_STROKEWIDTH);
        m_trackPaint.setTextSize(INFORMATION_TEXTSIZE);
        m_trackPaint.setStyle(Paint.Style.STROKE);
    }

    public WalkingTrackView(Context context) {
        super(context);
        initPaint();
        m_walkingPath = new Path();
        m_walkingPath.moveTo(0.f, 0.f);
        m_wvHandler = new WalkingViewHandler();
        m_footColorIndex = 0;
        m_footLocation = new PointF(0.f, 0.f);
        m_stepNum = 0;
        setOnTouchListener(new TouchListener());
    }

    public WalkingTrackView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaint();
        m_walkingPath = new Path();
        m_walkingPath.moveTo(0.f, 0.f);
        m_wvHandler = new WalkingViewHandler();
        m_footColorIndex = 0;
        m_footLocation = new PointF(0.f, 0.f);
        m_stepNum = 0;
        setOnTouchListener(new TouchListener());
    }

    public void initWalkingPath() {
        m_walkingPath.rewind();
        m_walkingPath.moveTo(0.f, 0.f);
        m_footColorIndex = 0;
        m_footLocation = new PointF(0.f, 0.f);
    }

    public void updateWalkingFoot(float x, float y) {
        m_footLocation.set(x, y);
        m_footColorIndex += 1;
        m_footColorIndex %= FOOT_COLORS.length;
        invalidate();
    }

    public void updateWalkingTrack(float x, float y, int num) {
        m_walkingPath.lineTo(x, y);
        m_footLocation.set(x, y);
        m_stepNum = num;
        invalidate();
    }

    protected void drawCoordinates(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        int centerX = width / 2;
        int centerY = height / 2;
        m_trackPaint.setColor(Color.RED);
        m_trackPaint.setTextSize(COORDINATE_TEXTSIZE);
        m_trackPaint.setStrokeWidth(COORDINATE_STROKEWIDTH);
        m_trackPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        canvas.save();
        // View Border
        m_trackPaint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(1, 1, width - 1, height - 1, m_trackPaint);
        m_trackPaint.setStyle(Paint.Style.FILL_AND_STROKE);

        //canvas.translate(m_dX, m_dY);

        // X axis, this is also the West-East axis
        canvas.drawLine(0, centerY, width, centerY, m_trackPaint);
        canvas.drawLine(width - 20, centerY - 20, width, centerY, m_trackPaint);
        canvas.drawLine(width - 20, centerY + 20, width, centerY, m_trackPaint);
        canvas.drawText("E", width - 30, centerY + 40, m_trackPaint);
        // Y axis, this is also the South-North axis
        canvas.drawLine(centerX, 0, centerX, height, m_trackPaint);
        canvas.drawLine(centerX - 20, 20, centerX, 0, m_trackPaint);
        canvas.drawLine(centerX + 20, 20, centerX, 0, m_trackPaint);
        canvas.drawText("N", centerX + 30, 40, m_trackPaint);
        canvas.restore();
        m_trackPaint.setColor(Color.BLUE);
        m_trackPaint.setTextSize(INFORMATION_TEXTSIZE);
        m_trackPaint.setStrokeWidth(TRACK_STROKEWIDTH);
        m_trackPaint.setStyle(Paint.Style.STROKE);
    }

    protected void drawWalkingPath(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        canvas.save();
        // Change the coordinate to our painting coordinate.
        canvas.translate(width / 2, height / 2);
        canvas.translate(m_dX, m_dY);
        canvas.scale(m_scale, m_scale);
        canvas.drawPath(m_walkingPath, m_trackPaint);
        canvas.restore();
    }

    protected void drawStepNum(Canvas canvas) {
        canvas.save();

        canvas.drawText("Step Number: " + Integer.toString(m_stepNum), 50, 100, m_trackPaint);

        canvas.restore();
    }

    protected void drawWalkingFoot(Canvas canvas) {
        m_trackPaint.setColor(FOOT_COLORS[m_footColorIndex]);
        int width = getWidth();
        int height = getHeight();
        canvas.save();
        // Change the coordinate to our painting coordinate.
        canvas.translate(width / 2, height / 2);
        canvas.drawCircle(m_footLocation.x, m_footLocation.y, FOOT_SIZES[0], m_trackPaint);
        canvas.restore();
        m_trackPaint.setColor(Color.BLUE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawCoordinates(canvas);
        drawWalkingPath(canvas);
        drawStepNum(canvas);
        drawWalkingFoot(canvas);
//        int width = getWidth();
//        int height = getHeight();
//        canvas.save();
//        canvas.translate(m_dX, m_dY);
//        canvas.scale(m_scale, m_scale);
//        canvas.drawRect(5, 5, width - 5, height - 5, m_trackPaint);
//        canvas.restore();
        super.onDraw(canvas);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }


}
