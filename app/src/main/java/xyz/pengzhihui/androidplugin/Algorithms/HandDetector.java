package xyz.pengzhihui.androidplugin.Algorithms;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.util.ArrayList;
import java.util.List;

public class HandDetector {
    public static int X_START = 0;
    public static int X_END = 1;
    public static int Y_START = 2;
    public static int Y_END = 3;
    public static int Z_START = 4;
    public static int Z_END = 5;

    private float minDepth;
    private float maxDepth;
    private float fx;
    private float fy;

    public HandDetector(float fx, float fy) {
        this.fx = fx;
        this.fy = fy;
        this.maxDepth = 1500;
        this.minDepth = 10;
    }

    private void setMaxMin(Mat depth) {
        Core.MinMaxLocResult minmax = Core.minMaxLoc(depth);
        this.maxDepth = Math.min(1500, (float)minmax.maxVal);
        this.minDepth = Math.max(10, (float)minmax.minVal);
    }

    public float[] detect(Mat depth, int[] cube) {
        // setMaxMin(depth);
        // Imgproc.threshold(depth, depth, this.minDepth, 255, Imgproc.THRESH_TOZERO);
        // Imgproc.threshold(depth, depth, this.maxDepth, 255, Imgproc.THRESH_TOZERO_INV);

        int step = 10;
        float dz = (this.maxDepth - this.minDepth)/ step;
        int height = depth.height();
        int width = depth.width();
        float[] item = new float[1];
        for (int k = 0; k < step; k++) {
            Mat part = depth.clone();

            Imgproc.threshold(part, part, k*dz + this.minDepth, 255, Imgproc.THRESH_TOZERO);
            Imgproc.threshold(part, part, (k+1)*dz + this.minDepth, 255, Imgproc.THRESH_TOZERO_INV);

            // 二值化
            // Mat thresh = new Mat(height, width, CvType.CV_8UC1);
            // Imgproc.threshold(part, part, 1, 255, Imgproc.THRESH_BINARY);

            // 计算轮廓
            part.convertTo(part, CvType.CV_8UC1);
            List<MatOfPoint> contours = new ArrayList<>();
            // Mat hierarchy = new Mat(height, width, CvType.CV_8UC1);
            Imgproc.findContours(part, contours, part, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

            for(MatOfPoint point: contours) {
                double counts = Imgproc.contourArea(point);
                if(counts > 200.0) {
                    Moments M = Imgproc.moments(point);
                    int cx = (int)(M.m10 / M.m00);
                    int cy = (int)(M.m01 / M.m00);

                    int xstart = Math.max(cx - 100, 0);
                    int xend = Math.min(cx + 100, width - 1);
                    int ystart = Math.max(cy - 100, 0);
                    int yend = Math.min(cy + 100, height - 1);

                    Mat crop = new Mat(depth, new Rect(xstart, ystart, xend - xstart, yend - ystart)).clone();
                    Imgproc.threshold(crop, crop, k*dz + this.minDepth, 255, Imgproc.THRESH_TOZERO);
                    Imgproc.threshold(crop, crop, (k+1)*dz + this.minDepth, 255, Imgproc.THRESH_TOZERO_INV);

                    float[] com = calculateCOM(crop);
                    if(com[0] == 0 && com[1] == 0 && com[2] == 0) {
                        crop.get(crop.height()/2, crop.width()/2, item);
                        com[2] = item[0];
                    }
                    com[0] += xstart;
                    com[1] += ystart;

                    // com = refineCOMIterative(depth.clone(), com, 2, cube);
                    return com;
                }
            }
        }
        depth.get((int)(height/2), (int)(width/2), item);
        return new float[]{(int)(width/2), (int)(height/2), item[0]};
    }

    public float[] comToBounds(Mat depth, float[] com, int[] cube) {
        float[] bounds = new float[6];
        if(com[2] == 0) {
            bounds[X_START] = depth.width() / 4;
            bounds[X_END] = bounds[X_START] + depth.width() / 2;
            bounds[Y_START] = depth.height() / 4;
            bounds[Y_END] = bounds[Y_START] + depth.height() / 2;
            bounds[Z_START] = this.minDepth;
            bounds[Z_END] = this.maxDepth;
        } else {
            bounds[Z_START] = com[2] - cube[2] / 2;
            bounds[Z_END] = com[2] + cube[2] / 2;
            bounds[X_START] = (float)Math.floor((com[0] * com[2] / this.fx - cube[0] / 2.) / com[2]*this.fx+0.5);
            bounds[X_END] = (float)Math.floor((com[0] * com[2] / this.fx + cube[0] / 2.) / com[2]*this.fx+0.5);
            bounds[Y_START] = (float)Math.floor((com[1] * com[2] / this.fy - cube[1] / 2.) / com[2]*this.fy+0.5);
            bounds[Y_END] = (float)Math.floor((com[1] * com[2] / this.fy + cube[1] / 2.) / com[2]*this.fy+0.5);
        }
        return bounds;
    }

    private Mat getCrop(Mat depth, float[] bounds) {
        Point start = new Point(Math.max(bounds[X_START], 0), Math.max(bounds[Y_START], 0));
        Point end = new Point(Math.min(bounds[X_END], depth.width()-1), Math.min(bounds[Y_END], depth.height()-1));
        Mat crop = new Mat(depth, new Rect(start, end)).clone();
        Imgproc.threshold(crop, crop, bounds[Z_START], 255, Imgproc.THRESH_TOZERO);
        Imgproc.threshold(crop, crop, bounds[Z_END], 255, Imgproc.THRESH_TOZERO_INV);
        return crop;
    }

    private float[] refineCOMIterative(Mat depth, float[] com, int iterNum, int[] cube) {
        float[] item = new float[1];
        for (int i = 0; i < iterNum; i++) {
            float[] bounds = this.comToBounds(depth, com, cube);
            Mat crop = this.getCrop(depth, bounds);
            com = calculateCOM(crop);
            if(com[0] == 0 && com[1] == 0 && com[2] == 0) {
                crop.get(crop.height()/2, crop.width()/2, item);
                com[2] = item[0];
            }
            com[0] += bounds[X_START];
            com[1] += bounds[Y_START];
        }
        return com;
    }

    private float[] calculateCOM(Mat depth) {
        float[] com = new float[]{0, 0, 0};
        Moments M = Imgproc.moments(depth);
        com[0] = (float)(M.m10 / M.m00);
        com[1] = (float)(M.m01 / M.m00);
        com[2] = (float)(Core.sumElems(depth).val[0] / Core.countNonZero(depth));
        return com;
    }

    /* accuracy version
    private float[] calculateCOM(Mat depth) {
        float[] com = new float[]{0, 0, 0};
        int height = depth.height();
        int width = depth.width();
        float[] item = new float[1];
        int counts = 0;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                depth.get(i, j, item);
                if(item[0] > 0) {
                    com[0] += j; // x
                    com[1] += i; // y
                    com[2] += item[0];
                    counts ++;
                }
            }
        }
        if(counts != 0) {
            com[0] /= counts;
            com[1] /= counts;
            com[2] /= counts;
        }
        return com;
    }
    */
}
