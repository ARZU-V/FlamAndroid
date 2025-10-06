#ifndef OPENCV_UTILS_H
#define OPENCV_UTILS_H

#include <opencv2/opencv.hpp>
#include <jni.h>

// This is just a header file that lists out the main functions we use for OpenCV stuff,
// so other C++ files know they exist and can use them.

using namespace cv;
Mat cannyEdgeDetection(const Mat& inputFrame);

Mat bitmapToMat(JNIEnv* env, jobject bitmap);
bool processBitmapDirect(JNIEnv* env, jobject bitmapIn, jobject bitmapOut);

#endif // OPENCV_UTILS_H