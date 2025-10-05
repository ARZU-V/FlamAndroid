#include <jni.h>
#include <string>
#include <android/bitmap.h>
#include <android/log.h>
#include "opencv_utils.h"

#define LOG_TAG "Native-Lib"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace cv;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_myflamcvgl_MainActivity_testOpenCV(
        JNIEnv* env,
        jobject thiz) {

    try {
        // Create a simple test image
        Mat testImage(100, 100, CV_8UC3, Scalar(100, 100, 100));

        // Test edge detection function
        Mat result = cannyEdgeDetection(testImage);

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

JNIEXPORT void JNICALL
Java_com_example_myflamcvgl_MainActivity_processFrameToBitmap(
        JNIEnv* env,
        jobject thiz,
        jobject bitmapIn,
        jobject bitmapOut) {

    // Use the direct processing method to avoid memory issues
    bool success = processBitmapDirect(env, bitmapIn, bitmapOut);
    if (!success) {
        LOGE("processBitmapDirect failed, falling back to manual method");

        // Fallback method
        try {
            Mat inputMat = bitmapToMat(env, bitmapIn);
            if (inputMat.empty()) {
                LOGE("Input mat is empty");
                return;
            }

            LOGI("Processing frame: %dx%d, channels: %d",
                 inputMat.cols, inputMat.rows, inputMat.channels());

            // Process with edge detection
            Mat processed = cannyEdgeDetection(inputMat);

            // Convert processed Mat to output bitmap
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

            // Convert to appropriate format for display
            if (processed.channels() == 1) {
                cvtColor(processed, finalOutput, COLOR_GRAY2RGBA);
            } else {
                finalOutput = processed;
            }

            // Ensure sizes match and copy data
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

JNIEXPORT jintArray JNICALL
Java_com_example_myflamcvgl_MainActivity_processFrameToPixels(
        JNIEnv* env,
        jobject thiz,
        jintArray inputPixels,
        jint width,
        jint height) {

    jint* inputArray = env->GetIntArrayElements(inputPixels, nullptr);
    jsize length = env->GetArrayLength(inputPixels);

    try {
        // Create OpenCV Mat from pixel data (RGBA format)
        Mat inputMat(height, width, CV_8UC4, inputArray);

        LOGI("Processing pixels: %dx%d, channels: %d", width, height, inputMat.channels());

        // Process using edge detection
        Mat processed = cannyEdgeDetection(inputMat);

        // Convert single channel result back to RGBA for display
        Mat outputMat;
        if (processed.channels() == 1) {
            cvtColor(processed, outputMat, COLOR_GRAY2RGBA);
        } else {
            outputMat = processed;
        }

        // Create output array
        jintArray result = env->NewIntArray(length);
        env->SetIntArrayRegion(result, 0, length, reinterpret_cast<const jint*>(outputMat.data));

        env->ReleaseIntArrayElements(inputPixels, inputArray, 0);
        return result;

    } catch (const std::exception& e) {
        LOGE("Exception in processFrameToPixels: %s", e.what());
        env->ReleaseIntArrayElements(inputPixels, inputArray, 0);
        return nullptr;
    }
}

} // extern "C"