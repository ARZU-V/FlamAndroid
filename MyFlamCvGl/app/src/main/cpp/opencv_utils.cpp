#include <jni.h>
#include <string>
#include <android/bitmap.h>
#include <android/log.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <opencv2/opencv.hpp>
#include "opencv_utils.h"


#define LOG_TAG "Native-Lib"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)


using namespace cv;

/**
 * This is where the actual Canny edge detection happens.
 * It takes one picture (Mat) and returns a new one with the edges.
 */
Mat cannyEdgeDetection(const Mat& inputFrame) {
    Mat src_gray, detected_edges, result;

    // First, we need a black and white version of the picture.
    if (inputFrame.channels() == 3) {
        cvtColor(inputFrame, src_gray, COLOR_BGR2GRAY);
    } else if (inputFrame.channels() == 4) {
        cvtColor(inputFrame, src_gray, COLOR_RGBA2GRAY);
    } else {
        src_gray = inputFrame.clone(); // Already grayscale
    }

    // A little blur helps get rid of noise and makes the edges cleaner.
    blur(src_gray, detected_edges, Size(3, 3));

    // These are just some settings for the Canny algorithm.
    const int lowThreshold = 50;
    const int ratio = 3;
    const int kernel_size = 3;

    // This is the main OpenCV function that finds the edges.
    Mat edges_output;
    Canny(detected_edges, edges_output, lowThreshold, lowThreshold * ratio, kernel_size);

    // Return the final black and white image with the edges.
    return edges_output;
}

/**
 * A helper function to turn an Android Bitmap from Kotlin into an OpenCV Mat.
 * Note: This function makes a copy of the bitmap data.
 */
Mat bitmapToMat(JNIEnv* env, jobject bitmap) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;

    // Gotta get the bitmap's info, like its width and height.
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("Failed to get bitmap info");
        return Mat();
    }

    // Lock the bitmap to get direct access to its pixels.
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("Failed to lock bitmap pixels");
        return Mat();
    }

    // Create an OpenCV Mat that uses the bitmap's pixel data.
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

    // Unlock the pixels when we're done.
    AndroidBitmap_unlockPixels(env, bitmap);

    // We return a copy of the Mat.
    return mat.clone();
}

/**
 * This is the main function that Kotlin calls through JNI. It takes two bitmaps,
 * processes the first one, and writes the result into the second one directly.
 */
bool processBitmapDirect(JNIEnv* env, jobject bitmapIn, jobject bitmapOut) {
    AndroidBitmapInfo infoIn, infoOut;
    void* pixelsIn = nullptr;
    void* pixelsOut = nullptr;

    // Get info for both bitmaps.
    if (AndroidBitmap_getInfo(env, bitmapIn, &infoIn) < 0) {
        LOGE("Failed to get input bitmap info");
        return false;
    }
    if (AndroidBitmap_getInfo(env, bitmapOut, &infoOut) < 0) {
        LOGE("Failed to get output bitmap info");
        return false;
    }

    // Lock both the input and output bitmaps so we can mess with their pixels.
    if (AndroidBitmap_lockPixels(env, bitmapIn, &pixelsIn) < 0) {
        LOGE("Failed to lock input bitmap pixels");
        return false;
    }
    if (AndroidBitmap_lockPixels(env, bitmapOut, &pixelsOut) < 0) {
        LOGE("Failed to lock output bitmap pixels");
        AndroidBitmap_unlockPixels(env, bitmapIn);
        return false;
    }

    try {
        // Create OpenCV Mats that point directly to the bitmap memory. No copying, so it's fast.
        cv::Mat inputMat(infoIn.height, infoIn.width, CV_8UC4, pixelsIn);
        cv::Mat outputMat(infoOut.height, infoOut.width, CV_8UC4, pixelsOut);

        // Call our other function to do the actual edge detection.
        cv::Mat processed = cannyEdgeDetection(inputMat);

        // The result is just black and white, so we need to convert it back to color (RGBA) to show it on the screen.
        if (processed.channels() == 1) {
            cv::cvtColor(processed, outputMat, cv::COLOR_GRAY2RGBA);
        } else {
            processed.copyTo(outputMat);
        }

    } catch (const std::exception& e) {
        LOGE("Exception in processBitmapDirect: %s", e.what());
        AndroidBitmap_unlockPixels(env, bitmapIn);
        AndroidBitmap_unlockPixels(env, bitmapOut);
        return false;
    }

    // Very important to unlock the pixels when we're done.
    AndroidBitmap_unlockPixels(env, bitmapIn);
    AndroidBitmap_unlockPixels(env, bitmapOut);

    return true;
}