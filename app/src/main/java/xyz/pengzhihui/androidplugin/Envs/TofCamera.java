package xyz.pengzhihui.androidplugin.Envs;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.ImageReader;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Range;
import android.util.SizeF;
import android.view.Surface;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import xyz.pengzhihui.androidplugin.Activities.TofCameraLiveActivity;

public class TofCamera extends CameraDevice.StateCallback{

    private static final String TAG = TofCamera.class.getSimpleName();

    private static int FPS_MIN = 25;
    private static int FPS_MAX = 30;

    private Context context;
    private CameraManager cameraManager;
    private ImageReader previewReader;
    private CaptureRequest.Builder previewBuilder;
    private TofCameraListener imageAvailableListener;

    public TofCamera(Context context, TofCameraLiveActivity depthFrameVisualizer) {
        this.context = context;
        cameraManager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        imageAvailableListener = new TofCameraListener(depthFrameVisualizer);
        previewReader = ImageReader.newInstance(TofCameraListener.WIDTH, TofCameraListener.HEIGHT, ImageFormat.DEPTH16,2);
        previewReader.setOnImageAvailableListener(imageAvailableListener, null);
    }


    // Open the front depth camera and start sending frames
    public void openFrontDepthCamera() {
        final String cameraId = getDepthCameraID();
        openCamera(cameraId);
    }

    private String getDepthCameraID() {
        String depth_camera = null;
        try {
            Log.i(TAG, "getCameraIdList: " + Arrays.toString(cameraManager.getCameraIdList()));
            for (String camera : cameraManager.getCameraIdList()) {
                CameraCharacteristics chars = cameraManager.getCameraCharacteristics(camera);

                float[] intrinsic_param = chars.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION);
                Log.i(TAG, "any intrinsic calibration: " + Arrays.toString(intrinsic_param));

                // [0, 2, 9, 8]
                // 0 REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE
                // 2 REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING
                // 8 REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT
                // 9 REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO
                final int[] capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                Set<String> ss = chars.getPhysicalCameraIds(); // []

                boolean depthCapable = false;
                for (int capability : capabilities) {
                    boolean capable = capability == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT;
                    depthCapable = depthCapable || capable;
                }
                if (depthCapable) {
                    // Note that the sensor size is much larger than the available capture size
                    SizeF sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                    Log.i(TAG, "Sensor size: " + sensorSize);

                    // Since sensor size doesn't actually match capture size and because it is
                    // reporting an extremely wide aspect ratio, this FoV is bogus
                    float[] focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    if (focalLengths.length > 0) {
                        float focalLength = focalLengths[0];
                        double fov = 2 * Math.atan(sensorSize.getWidth() / (2 * focalLength));
                        Log.i(TAG, "Calculated FoV: " + fov);
                    }
                    // return camera;
                    depth_camera = camera;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Could not initialize Camera Cache");
            e.printStackTrace();
        }
        return depth_camera;
    }

    private void openCamera(String cameraId) {
        try{
            int permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA);
            if(PackageManager.PERMISSION_GRANTED == permission) {
                cameraManager.openCamera(cameraId, this, null);
            }else{
                Log.e(TAG,"Permission not available to open camera");
            }
        }catch (CameraAccessException | IllegalStateException | SecurityException e){
            Log.e(TAG,"Opening Camera has an Exception " + e);
            e.printStackTrace();
        }
    }


    @Override
    public void onOpened(@NonNull CameraDevice camera) {
        try {
            previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.set(CaptureRequest.JPEG_ORIENTATION, 0);
            Range<Integer> fpsRange = new Range<>(FPS_MIN, FPS_MAX);
            previewBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
            previewBuilder.addTarget(previewReader.getSurface());

            List<Surface> targetSurfaces = Arrays.asList(previewReader.getSurface());
            camera.createCaptureSession(targetSurfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            onCaptureSessionConfigured(session);
                        }
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG,"!!! Creating Capture Session failed due to internal error ");
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void onCaptureSessionConfigured(@NonNull CameraCaptureSession session) {
        Log.i(TAG,"Capture Session created");
        previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

        CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                           @NonNull CaptureRequest request,
                                           @NonNull TotalCaptureResult result) {
                // 失败，返回值是0
                // float[] calibration = result.get(CaptureResult.LENS_INTRINSIC_CALIBRATION);
                // Log.i(TAG, "capture result calibration: " + Arrays.toString(calibration));
            }
        };

        try {
            session.setRepeatingRequest(previewBuilder.build(), captureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onDisconnected(@NonNull CameraDevice camera) {

    }

    @Override
    public void onError(@NonNull CameraDevice camera, int error) {

    }
}
