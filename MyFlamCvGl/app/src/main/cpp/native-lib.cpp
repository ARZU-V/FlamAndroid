#include <jni.h>
#include <string>
#include <android/bitmap.h>
#include <android/log.h>
#include "opencv_utils.h"

// This file has all the main "doors" that our Kotlin code uses to talk to C++.
// The function names are long and specific so JNI knows exactly what to call.

#define LOG_TAG "Native-Lib"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace cv;

extern "C" {

/**
 * A simple test function that Kotlin calls when the app starts,
 * just to make sure OpenCV is loaded and working correctly.
 */
JNIEXPORT jboolean JNICALL
Java_com_example_myflamcvgl_MainActivity_testOpenCV(
        JNIEnv* env,
        jobject thiz) {

    try {
        // Make a boring gray image.
        Mat testImage(100, 100, CV_8UC3, Scalar(100, 100, 100));

        // Try to run our edge detection on it.
        Mat result = cannyEdgeDetection(testImage);

        // If we got something back, it worked!
        if (!result.empty()) {
            LOGI("OpenCV test successful - Result size: %dx%d, channels: %d",
                 result.cols, result.rows, result.channels());
            return JNI_TRUE;
        } else {
            LOGE("OpenCV test failed - Empty result");
            return JNI_FALSE;
        }

    } catch (const std::exception& e) {
        LOGE("OpenCV test exception: %s", e.what());
        return JNI_FALSE;
    }
}

/**
 * This is the main JNI function for processing a full frame.
 * It takes the camera picture and an empty picture to draw on.
 */
JNIEXPORT void JNICALL
Java_com_example_myflamcvgl_MainActivity_processFrameToBitmap(
        JNIEnv* env,
        jobject thiz,
        jobject bitmapIn,
        jobject bitmapOut) {

    // First, we try the fast direct method which doesn't copy memory around.
    bool success = processBitmapDirect(env, bitmapIn, bitmapOut);
    if (!success) {
        LOGE("processBitmapDirect failed, trying the backup method");

        // --- Fallback Method ---
        // If the fast way failed for some reason, we try this safer backup method.
        // It's a bit slower because it copies data, but it's a good backup.
        try {
            // Turn the input bitmap into something OpenCV can use.
            Mat inputMat = bitmapToMat(env, bitmapIn);
            if (inputMat.empty()) {
                LOGE("Input mat is empty");
                return;
            }

            // Run the edge detection.
            Mat processed = cannyEdgeDetection(inputMat);

            // Now, we get ready to copy the result into the output bitmap.
            AndroidBitmapInfo infoOut;
            void* pixelsOut;

            if (AndroidBitmap_getInfo(env, bitmapOut, &infoOut) < 0) {
                LOGE("Failed to get output bitmap info");
                return;
            }

            if (AndroidBitmap_lockPixels(env, bitmapOut, &pixelsOut) < 0) {
                LOGE("Failed to lock output bitmap pixels");
                return;
            }

            Mat outputMat(infoOut.height, infoOut.width, CV_8UC4, pixelsOut);
            Mat finalOutput;

            // Convert the black and white result back to color so it looks right on the screen.
            if (processed.channels() == 1) {
                cvtColor(processed, finalOutput, COLOR_GRAY2RGBA);
            } else {
                finalOutput = processed;
            }

            // Make sure the sizes match, then copy the final picture into the output bitmap.
            if (finalOutput.rows == outputMat.rows && finalOutput.cols == outputMat.cols) {
                finalOutput.copyTo(outputMat);
            } else {
                LOGE("Size mismatch: processed %dx%d, output %dx%d",
                     finalOutput.cols, finalOutput.rows, outputMat.cols, outputMat.rows);
            }

            AndroidBitmap_unlockPixels(env, bitmapOut);

        } catch (const std::exception& e) {
            LOGE("Exception in fallback method: %s", e.what());
        }
    }
}

/**
 * This is another way to process an image. Instead of whole bitmap objects,
 * it just works with an array of pixels. Not currently used but could be useful.
 */
JNIEXPORT jintArray JNICALL
Java_com_example_myflamcvgl_MainActivity_processFrameToPixels(
        JNIEnv* env,
        jobject thiz,
        jintArray inputPixels,
        jint width,
        jint height) {

    // Get the raw pixel data from the Java array.
    jint* inputArray = env->GetIntArrayElements(inputPixels, nullptr);
    jsize length = env->GetArrayLength(inputPixels);

    try {
        // Create an OpenCV Mat using the pixel data.
        Mat inputMat(height, width, CV_8UC4, inputArray);

        // Process using edge detection.
        Mat processed = cannyEdgeDetection(inputMat);

        // Convert single channel result back to RGBA for display.
        Mat outputMat;
        if (processed.channels() == 1) {
            cvtColor(processed, outputMat, COLOR_GRAY2RGBA);
        } else {
            outputMat = processed;
        }

        // Make a new Java array for the results.
        jintArray result = env->NewIntArray(length);
        // Copy the processed pixels into the new Java array.
        env->SetIntArrayRegion(result, 0, length, reinterpret_cast<const jint*>(outputMat.data));

        // Clean up and send the new array of pixels back to Kotlin.
        env->ReleaseIntArrayElements(inputPixels, inputArray, 0);
        return result;

    } catch (const std::exception& e) {
        LOGE("Exception in processFrameToPixels: %s", e.what());
        env->ReleaseIntArrayElements(inputPixels, inputArray, 0);
        return nullptr;
    }
}

} // extern "C"