package xyz.pengzhihui.androidplugin.Activities;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;

import com.beardedhen.androidbootstrap.BootstrapButton;
import com.beardedhen.androidbootstrap.TypefaceProvider;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
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
import xyz.pengzhihui.androidplugin.Utils.LogUtil;

public class TofCameraLiveActivity extends Activity {
    private static final String TAG = TofCameraLiveActivity.class.getSimpleName();
    public static final int CAM_PERMISSIONS_REQUEST = 0;

    private TextureView rawDataView;
    private BootstrapButton buttonOrigin;
    private BootstrapButton buttonDetect;
    private BootstrapButton buttonEstimation;
    private int APP_MODEL_ORIGIN = 0;
    private int APP_MODEL_DETECT = 1;
    private int APP_MODEL_ESTIMATION = 2;
    private int appMode = APP_MODEL_ORIGIN;

    private Matrix defaultBitmapTransform;
    private TofCamera camera;
    private HandDetector detector;
    private HandPredictor predictor;
    private int[] cube = new int[]{200, 200, 200};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TypefaceProvider.registerDefaultIconSets();
        setContentView(R.layout.layout_tof_camera_preview);
        rawDataView = findViewById(R.id.textureview_tof);

        buttonOrigin = findViewById(R.id.button_origin);
        buttonDetect = findViewById(R.id.button_detect);
        buttonEstimation = findViewById(R.id.button_estimation);
        setButtonEvent();

        checkCamPermissions();
        camera = new TofCamera(this, this);
        camera.openDepthCamera();

        detector = new HandDetector();
        predictor = new HandPredictor(this);

        // fpsTest();
    }

    private void fpsTest() {
        float[][] depth = loadDepth("1.txt");
        Mat img = float2mat(depth);

        int count = 200;
        Date ds = new Date();
        for (int i = 0; i < count; i++) {
            float[] center = detectHand(img);
        }
        float duration = (float)(new Date().getTime() - ds.getTime()) / 1000;
        Log.i(TAG, "detect fps = " + count / duration); // 161 FPS

        ds = new Date();
        for (int i = 0; i < count; i++) {
            float[][] keypoints = predictHand(img);
        }
        duration = (float)(new Date().getTime() - ds.getTime()) / 1000;
        Log.i(TAG, "predict fps = " + count / duration); // 36 FPS
    }

    private void setButtonEvent() {
        buttonOrigin.setOnClickListener((View v) -> appMode = APP_MODEL_ORIGIN);
        buttonDetect.setOnClickListener((View v) -> appMode = APP_MODEL_DETECT);
        buttonEstimation.setOnClickListener((View v) -> appMode = APP_MODEL_ESTIMATION);
    }

    private void checkCamPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAM_PERMISSIONS_REQUEST);
        }
    }

    private void drawKeypoints(Mat img, float[][] keypoints) {
        int radius = 3;
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

    private float[][] predictHand(Mat img) {
        float[] com = detector.detect(img, cube);
        float[] bounds = detector.comToBounds(img, com, cube);
        Point leftbottom = new Point(bounds[HandDetector.X_START], bounds[HandDetector.Y_START]);
        Point righttop = new Point(bounds[HandDetector.X_END], bounds[HandDetector.Y_END]);
        float[][] keypoints = predictor.predict(img, com, leftbottom, righttop);
        return keypoints;
    }

    private float[] detectHand(Mat img) {
        return detector.detect(img, cube);
    }

    private float[][] loadDepth(String file) {
        float[][] img = null;
        try {
            InputStream in = getAssets().open(file);
            img = FileUtils.getDepthImg(in, 480, 640);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return img;
    }

    private Mat cropHand(Mat img) {
        float[] com = detector.detect(img, cube);
        float[] bounds = detector.comToBounds(img, com, cube);

        Point leftbottom = new Point(bounds[HandDetector.X_START], bounds[HandDetector.Y_START]);
        Point righttop = new Point(bounds[HandDetector.X_END], bounds[HandDetector.Y_END]);
        Mat crop = new Mat(img, new Rect(leftbottom, righttop)).clone();
        Imgproc.resize(crop, crop, new Size(img.width(), img.height()), 0, 0, Imgproc.INTER_NEAREST);
        return crop;
    }

    public void onRawDataAvailable(float[][] depth) {
        Mat img = float2mat(depth);
        Imgproc.threshold(img, img, HandDetector.MIN_DEPTH, 255, Imgproc.THRESH_TOZERO);
        Imgproc.threshold(img, img, HandDetector.MAX_DEPTH, 255, Imgproc.THRESH_TOZERO_INV);

        if(appMode == APP_MODEL_DETECT) {
            float[] center = detectHand(img);
            drawDetectRect(img, center);
        } else if(appMode == APP_MODEL_ESTIMATION) {
            float[][] keypoints = predictHand(img);
            drawKeypoints(img, keypoints);
        }

        Core.normalize(img, img, 0, 255, Core.NORM_MINMAX);
        img.convertTo(img, CvType.CV_8UC1);
        Bitmap map = Bitmap.createBitmap(img.width(), img.height(), Bitmap.Config.RGB_565);
        Utils.matToBitmap(img, map);

        Canvas canvas = rawDataView.lockCanvas();
        canvas.drawBitmap(map, defaultBitmapTransform(rawDataView, map), null);
        rawDataView.unlockCanvasAndPost(canvas);
    }

    private Matrix defaultBitmapTransform(TextureView view, Bitmap map) {
        if (defaultBitmapTransform == null || view.getWidth() == 0 || view.getHeight() == 0) {
            Matrix matrix = new Matrix();
            int centerX = view.getWidth() / 2;
            int centerY = view.getHeight() / 2;
            int bufferWidth = map.getWidth();
            int bufferHeight = map.getHeight();
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
