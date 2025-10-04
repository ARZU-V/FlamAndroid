#include <jni.h>
#include <string>
#include <android/bitmap.h>
#include <android/log.h>
#include "opencv_utils.h"



#define LOG_TAG "NativeLib"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_myflamcvgl_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_flamopenglcv_MainActivity_testOpenCV(
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
} // extern "C"