package xyz.pengzhihui.androidplugin.Activities;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.TextureView;

import xyz.pengzhihui.androidplugin.Envs.TofCamera;
import xyz.pengzhihui.androidplugin.Envs.TofCameraListener;
import xyz.pengzhihui.androidplugin.R;

public class TofCameraLiveActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();
    public static final int CAM_PERMISSIONS_REQUEST = 0;

    private TextureView rawDataView;
    private Matrix defaultBitmapTransform;
    private TofCamera camera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_tof_camera_preview);
        rawDataView = findViewById(R.id.textureview_tof);

        checkCamPermissions();
        camera = new TofCamera(this, this);
        camera.openFrontDepthCamera();
    }

    private void checkCamPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAM_PERMISSIONS_REQUEST);
        }
    }

    public void onRawDataAvailable(Bitmap bitmap) {
        renderBitmapToTextureView(bitmap, rawDataView);
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
            matrix.postRotate(90, centerX, centerY);

            defaultBitmapTransform = matrix;
        }
        return defaultBitmapTransform;
    }

}
