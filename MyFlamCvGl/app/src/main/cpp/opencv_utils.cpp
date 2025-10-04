#include <jni.h>
#include <string>
#include <android/bitmap.h>
#include <android/log.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <opencv2/opencv.hpp>  // ADD THIS LINE
#include "opencv_utils.h"

#define LOG_TAG "NativeLib"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace cv;
Mat cannyEdgeDetection(const Mat& inputFrame) {
    Mat src_gray, detected_edges, result;

    // Convert to grayscale if needed
    if (inputFrame.channels() == 3) {
        cvtColor(inputFrame, src_gray, COLOR_BGR2GRAY);
    } else if (inputFrame.channels() == 4) {
        cvtColor(inputFrame, src_gray, COLOR_RGBA2GRAY);
    } else {
        src_gray = inputFrame.clone(); // Already grayscale
    }

    // Apply blur to reduce noise
    blur(src_gray, detected_edges, Size(3, 3));

    // Canny edge detection with fixed thresholds for mobile performance
    const int lowThreshold = 50;
    const int ratio = 3;
    const int kernel_size = 3;

    // Create a separate output matrix for Canny
    Mat edges_output;
    Canny(detected_edges, edges_output, lowThreshold, lowThreshold * ratio, kernel_size);

    // Return the edges output (single channel image)
    return edges_output;
}

Mat convertGrayscale(const Mat& inputFrame) {
    Mat gray;
    if (inputFrame.channels() == 3) {
        cvtColor(inputFrame, gray, COLOR_BGR2GRAY);
    } else if (inputFrame.channels() == 4) {
        cvtColor(inputFrame, gray, COLOR_RGBA2GRAY);
    } else {
        gray = inputFrame.clone();
    }
    return gray;
}

// Convert Android Bitmap to OpenCV Mat
Mat bitmapToMat(JNIEnv* env, jobject bitmap) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;

    // Get bitmap info
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("Failed to get bitmap info");
        return Mat();
    }

    // Lock pixels
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("Failed to lock bitmap pixels");
        return Mat();
    }

    Mat mat;
    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        mat = Mat(info.height, info.width, CV_8UC4, pixels);
    } else if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
        mat = Mat(info.height, info.width, CV_8UC2, pixels);
    } else {
        LOGE("Unsupported bitmap format: %d", info.format);
        AndroidBitmap_unlockPixels(env, bitmap);
        return cv::Mat();
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    return mat.clone();
}

//void myFlip(Mat src) {
//    flip(src, src, 0);
//}
//
//void myBlur(Mat src, float sigma) {
//    GaussianBlur(src, src, Size(), sigma);
//}