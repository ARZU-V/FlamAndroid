cmake_minimum_required(VERSION 3.22.1)

# Project Name
project("myflamcvgl")

# Path to OpenCV Android SDK.
set(OPENCV_ANDROID_DIR "/home/arjit69/OpenCV-android-sdk")

# Tell CMake where to find the OpenCV package configuration files.
set(OpenCV_DIR ${OPENCV_ANDROID_DIR}/sdk/native/jni)

# Find the OpenCV package. 'REQUIRED' makes the build fail if it's not found.
find_package(OpenCV REQUIRED)

# This will produce the "libmyflamcvgl.so" file that your Kotlin code needs.
# Add all your C++ source files here.
add_library(myflamcvgl SHARED
        native-lib.cpp
        opencv_utils.cpp) # add other cpp files

# Finds standard Android native libraries needed for graphics and logging.
find_library(jnigraphics-lib jnigraphics)
find_library(log-lib log)
find_library(glesv2-lib GLESv2)
find_library(android-lib android)

# Adds the OpenCV header file directory to the include path.
# This allows you to use #include <opencv2/core.hpp> in your C++ code.
include_directories(${OpenCV_INCLUDE_DIRS})

# Links your library against all the other libraries it depends on.
target_link_libraries(myflamcvgl
        ${OpenCV_LIBS}
        ${jnigraphics-lib}
        ${log-lib}
        ${glesv2-lib}
        ${android-lib})