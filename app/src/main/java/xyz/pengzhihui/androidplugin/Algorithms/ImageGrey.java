package xyz.pengzhihui.androidplugin.Algorithms;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class ImageGrey {
    public Mat doProcessing(Mat frame)
    {
        Imgproc.cvtColor(frame, frame, Imgproc.COLOR_BGR2GRAY);
        return frame;
    }
}
