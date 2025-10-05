cmake_minimum_required(VERSION 3.22.1)

project("native-lib")

# Set OpenCV directory - UPDATE THIS PATH
set(OPENCV_ANDROID_DIR "/home/arjit69/OpenCV-android-sdk")
set(OpenCV_DIR ${OPENCV_ANDROID_DIR}/sdk/native/jni)
find_package(OpenCV REQUIRED)

# Add your source files
add_library(native-lib SHARED
        native-lib.cpp
        opencv_utils.cpp)

# Find required libraries
find_library(jnigraphics-lib jnigraphics)
find_library(log-lib log)
find_library(glesv2-lib GLESv2)
find_library(android-lib android)

# Include directories
include_directories(${OpenCV_INCLUDE_DIRS})

# Link libraries
target_link_libraries(native-lib
        ${OpenCV_LIBS}
        ${jnigraphics-lib}
        ${log-lib}
        ${glesv2-lib}
        ${android-lib})