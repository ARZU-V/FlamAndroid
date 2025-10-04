#include <jni.h>
#include <string>
#include "android/bitmap.h"
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include "opencv_utils.h"




extern "C" JNIEXPORT jstring JNICALL
Java_com_example_myflamcvgl_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

