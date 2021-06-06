package xyz.pengzhihui.androidplugin.Envs;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;

import java.nio.ShortBuffer;

import xyz.pengzhihui.androidplugin.Activities.TofCameraLiveActivity;

public class TofCameraListener implements ImageReader.OnImageAvailableListener{
    private static final String TAG = TofCameraListener.class.getSimpleName();

    public static int WIDTH = 240;
    public static int HEIGHT = 180;

    private static float RANGE_MIN = 10.0f;
    private static float RANGE_MAX = 1500.0f;
    private static float CONFIDENCE_FILTER = 0.2f;

    private TofCameraLiveActivity depthFrameVisualizer;
    private float[][] rawMask;

    public TofCameraListener(TofCameraLiveActivity depthFrameVisualizer) {
        this.depthFrameVisualizer = depthFrameVisualizer;
        rawMask = new float[HEIGHT][WIDTH];
    }


    @Override
    public void onImageAvailable(ImageReader reader) {
        try {
            Image image = reader.acquireNextImage();
            if (image != null && image.getFormat() == ImageFormat.DEPTH16) {
                processImage(image);
                publishRawData();
            }
            image.close();
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to acquireNextImage: " + e.getMessage());
        }
    }

    private void publishRawData() {
        if (depthFrameVisualizer != null) {
            depthFrameVisualizer.onRawDataAvailable(rawMask);
        }
    }

    private void processImage(Image image) {
        ShortBuffer shortDepthBuffer = image.getPlanes()[0].getBuffer().asShortBuffer();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int index = y * WIDTH + x;
                short depthSample = shortDepthBuffer.get(index);
                rawMask[y][x] = extractRange(depthSample, CONFIDENCE_FILTER);
            }
        }
    }

    private float extractRange(short sample, float confidenceFilter) {
        float depthRange = (float) (sample & 0x1FFF);
        int depthConfidence = (short) ((sample >> 13) & 0x7);
        float depthPercentage = depthConfidence == 0 ? 1.f : (depthConfidence - 1) / 7.f;
        return depthPercentage > confidenceFilter ? depthRange : 0;
    }
}