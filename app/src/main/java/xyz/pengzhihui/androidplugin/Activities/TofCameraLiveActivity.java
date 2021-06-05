package xyz.pengzhihui.androidplugin.Activities;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.TextureView;
import android.widget.ImageView;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Date;

import xyz.pengzhihui.androidplugin.Algorithms.HandDetector;
import xyz.pengzhihui.androidplugin.Algorithms.HandPredictor;
import xyz.pengzhihui.androidplugin.Envs.TofCamera;
import xyz.pengzhihui.androidplugin.Envs.TofCameraListener;
import xyz.pengzhihui.androidplugin.R;
import xyz.pengzhihui.androidplugin.Utils.FileUtils;

public class TofCameraLiveActivity extends Activity {
    private static final String TAG = TofCameraLiveActivity.class.getSimpleName();
    public static final int CAM_PERMISSIONS_REQUEST = 0;

    private TextureView tv;
    private ImageView rawDataView;
    private Matrix defaultBitmapTransform;
    private TofCamera camera;
    private HandDetector detector;
    private HandPredictor predictor;
    private int[] cube = new int[]{250, 250, 250};

    private static int HEIGHT = 480;
    private static int WIDTH = 640;
    private static float RANGE_MIN = 10.0f;
    private static float RANGE_MAX = 1500.0f;

    private static float FX = 474.973144f;
    private static float FY = 474.973144f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_tof_camera_preview);
        rawDataView = findViewById(R.id.textureview_tof);

        checkCamPermissions();
        // camera = new TofCamera(this, this);
        // camera.openFrontDepthCamera();

        detector = new HandDetector(FX, FY);
        predictor = new HandPredictor(this);
        detectHand();
        // predictHand();
    }

    private void checkCamPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAM_PERMISSIONS_REQUEST);
        }
    }

    private void drawKeypoints(Mat img, float[][] keypoints) {
        int radius = 5;
        Scalar color = new Scalar(0, 255, 0);
        for (int i = 0; i < keypoints.length; i++) {
            Imgproc.circle(img, new Point(keypoints[i][0], keypoints[i][1]), radius, color);
        }
    }

    private void drawDetectRect(Mat img, float[] center) {
        float[] bounds = detector.comToBounds(img, center, cube);
        Scalar color = new Scalar(0, 255, 0);
        Point p1 = new Point(bounds[HandDetector.X_START], bounds[HandDetector.Y_START]);
        Point p2 = new Point(bounds[HandDetector.X_START], bounds[HandDetector.Y_END]);
        Point p3 = new Point(bounds[HandDetector.X_END], bounds[HandDetector.Y_START]);
        Point p4 = new Point(bounds[HandDetector.X_END], bounds[HandDetector.Y_END]);
        Imgproc.line(img, p1, p2, color);
        Imgproc.line(img, p1, p3, color);
        Imgproc.line(img, p3, p4, color);
        Imgproc.line(img, p2, p4, color);
    }

    private void predictHand() {
        float[][] depth = loadDepth("1.txt");
        Mat img = float2mat(depth);
        float[] com = detector.detect(img, cube);
        float[] bounds = detector.comToBounds(img, com, cube);
        Point leftbottom = new Point(bounds[HandDetector.X_START], bounds[HandDetector.Y_START]);
        Point righttop = new Point(bounds[HandDetector.X_END], bounds[HandDetector.Y_END]);
        float[][] keypoints = predictor.predict(img, com, leftbottom, righttop);
        drawKeypoints(img, keypoints);
        Bitmap bitmap = mat2bitmap(img);
        rawDataView.setImageBitmap(bitmap);
    }

    private void detectHand() {
        float[][] depth = loadDepth("1.txt");
        Mat img = float2mat(depth);
        float[] com = detector.detect(img, cube);
        drawDetectRect(img, com);
        Bitmap bitmap = mat2bitmap(img);
        rawDataView.setImageBitmap(bitmap);
    }

    private float[][] loadDepth(String file) {
        float[][] img = null;
        try {
            InputStream in = getAssets().open(file);
            img = FileUtils.getDepthImg(in, HEIGHT, WIDTH);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return img;
    }

    private int normalizeRange(int range) {
        float normalized = (float)range - RANGE_MIN;
        // Clamp to min/max
        normalized = Math.max(RANGE_MIN, normalized);
        normalized = Math.min(RANGE_MAX, normalized);
        // Normalize to 0 to 255
        normalized = normalized - RANGE_MIN;
        normalized = normalized / (RANGE_MAX - RANGE_MIN) * 255;
        return (int)normalized;
    }

    private Bitmap mat2bitmap(Mat depth) {
        Bitmap bitmap = Bitmap.createBitmap(depth.width(), depth.height(), Bitmap.Config.ARGB_8888);
        float[] item = new float[1];
        for (int y = 0; y < depth.height(); y++)
            for (int x = 0; x < depth.width(); x++) {
                depth.get(y, x, item);
                bitmap.setPixel(x, y, Color.argb(255, 0, (int)item[0], 0));
            }
        return bitmap;
    }

    public void onRawDataAvailable(float[][] depth) {
    }

    private void renderBitmapToTextureView(Bitmap bitmap, TextureView textureView) {
        Canvas canvas = textureView.lockCanvas();
        canvas.drawBitmap(bitmap, defaultBitmapTransform(textureView), null);
        textureView.unlockCanvasAndPost(canvas);
    }

    private Matrix defaultBitmapTransform(TextureView view) {
        if (defaultBitmapTransform == null || view.getWidth() == 0 || view.getHeight() == 0) {
            Matrix matrix = new Matrix();
            int centerX = view.getWidth() / 2;
            int centerY = view.getHeight() / 2;

            int bufferWidth = TofCameraListener.WIDTH;
            int bufferHeight = TofCameraListener.HEIGHT;

            RectF bufferRect = new RectF(0, 0, bufferWidth, bufferHeight);
            RectF viewRect = new RectF(0, 0, view.getWidth(), view.getHeight());
            matrix.setRectToRect(bufferRect, viewRect, Matrix.ScaleToFit.CENTER);
            // 顺时针旋转90度
            matrix.postRotate(90, centerX, centerY);

            defaultBitmapTransform = matrix;
        }
        return defaultBitmapTransform;
    }

    private Mat float2mat(float[][] depth) {
        int height = depth.length;
        int width = depth[0].length;
        Mat m = new Mat(height, width, CvType.CV_32FC1);
        for (int i = 0; i < height; i++)
            m.put(i, 0, depth[i]);
        return m;
    }
}
