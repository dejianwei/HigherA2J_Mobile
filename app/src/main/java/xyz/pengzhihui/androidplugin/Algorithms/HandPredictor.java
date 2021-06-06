package xyz.pengzhihui.androidplugin.Algorithms;

import android.content.Context;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import xyz.pengzhihui.androidplugin.Activities.TofCameraLiveActivity;

public class HandPredictor {
    private static final String TAG = TofCameraLiveActivity.class.getSimpleName();

    public static int KEYPOINT_NUM = 14;
    private int cropWidth = 176;
    private int cropHeight = 176;
    private int depthThres = 150;
    private float depth_pixel_ratio = (float)cropHeight / 2 / depthThres;

    // dataset mean and std for NYU
    private float MEAN = -0.668775f;
    private float STD = 28.329582f;

    private Module net = null;

    private float[][] pred_keypoints;

    public HandPredictor(Context context) {
        pred_keypoints = new float[KEYPOINT_NUM][3];
        try {
            net = Module.load(assetFilePath(context, "HigherA2J.pt"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public float[][] predict(Mat img, float[] center, Point leftbottom, Point righttop) {
        img = prepareImg(img, center, leftbottom, righttop);
        float[] data = new float[img.height() * img.width()];
        img.get(0, 0, data);
        Tensor input = Tensor.fromBlob(data, new long[]{1, 1, cropHeight, cropWidth});
        Tensor result = net.forward(IValue.from(input)).toTensor();
        float[] keypoints = result.getDataAsFloatArray();
        prepareKeypoints(keypoints, center, leftbottom, righttop);
        return pred_keypoints;
    }

    private Mat prepareImg(Mat img, float[] center, Point leftbottom, Point righttop) {
        Mat crop = new Mat(img, new Rect(leftbottom, righttop)).clone();
        Imgproc.resize(crop, crop, new Size(cropWidth, cropHeight), 0, 0, Imgproc.INTER_NEAREST);
        Core.subtract(crop, new Scalar(center[2]), crop);
        Imgproc.threshold(crop, crop, -depthThres, 255, Imgproc.THRESH_TOZERO);
        Imgproc.threshold(crop, crop, depthThres, 255, Imgproc.THRESH_TOZERO_INV);
        Core.multiply(crop, new Scalar(depth_pixel_ratio), crop);
        Core.subtract(crop, new Scalar(MEAN), crop);
        Core.divide(crop, new Scalar(STD), crop);
        // Core.normalize(crop, crop, 0, 1, Core.NORM_MINMAX);
        return crop;
    }

    private void prepareKeypoints(float[] keypoints, float[] center, Point leftbottom, Point righttop) {
        for (int i = 0; i < 3*KEYPOINT_NUM; i+=3) {
            pred_keypoints[i/3][0] = keypoints[i+1] * (float)(righttop.x - leftbottom.x) / cropWidth + (float)leftbottom.x;
            pred_keypoints[i/3][1] = keypoints[i] * (float)(righttop.y - leftbottom.y) / cropHeight + (float)leftbottom.y;
            pred_keypoints[i/3][2] = keypoints[i+2] / depth_pixel_ratio + center[2];
        }
    }

    /**
     * Copies specified asset to the file in /files app directory and returns this file absolute path.
     *
     * @return absolute file path
     */
    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }
}
