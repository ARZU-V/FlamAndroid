#include <jni.h>
#include <string>
#include <android/bitmap.h>
#include <android/log.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <opencv2/opencv.hpp>  // ADD THIS LINE
#include "opencv_utils.h"


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

bool processBitmapDirect(JNIEnv* env, jobject bitmapIn, jobject bitmapOut) {
    AndroidBitmapInfo infoIn, infoOut;
    void* pixelsIn = nullptr;
    void* pixelsOut = nullptr;

    // Get input and output bitmap info...
    if (AndroidBitmap_getInfo(env, bitmapIn, &infoIn) < 0 || AndroidBitmap_getInfo(env, bitmapOut, &infoOut) < 0) {
        LOGE("Failed to get bitmap info");
        return false;
    }

    // Lock pixels to get access to the memory
    if (AndroidBitmap_lockPixels(env, bitmapIn, &pixelsIn) < 0) {
        LOGE("Failed to lock input bitmap pixels");
        return false;
    }
    if (AndroidBitmap_lockPixels(env, bitmapOut, &pixelsOut) < 0) {
        LOGE("Failed to lock output bitmap pixels");
        AndroidBitmap_unlockPixels(env, bitmapIn); // At least unlock the first one on failure
        return false;
    }

    try {
        // Create Mats from the locked pixel buffers
        cv::Mat inputMat(infoIn.height, infoIn.width, CV_8UC4, pixelsIn);
        cv::Mat outputMat(infoOut.height, infoOut.width, CV_8UC4, pixelsOut);

        // Process the frame (this part is fine)
        cv::Mat processed = cannyEdgeDetection(inputMat);

        // Convert the result back to RGBA for display
        cv::cvtColor(processed, outputMat, cv::COLOR_GRAY2RGBA);

    } catch (const std::exception& e) {
        LOGE("Exception in processBitmapDirect_BAD: %s", e.what());
        // Even in an exception, a good function would unlock here. This bad one doesn't.
        return false;
    }

    return true;
}