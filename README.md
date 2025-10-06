# Flam Assignment 

A full-stack Android project demonstrating real-time video processing with OpenCV/C++ and live streaming to a web browser via WebSockets. The processed feed is rendered locally using OpenGL ES.

---

## Features

### Core Functionality
* **Live Camera Feed**: Captures video using the **Android Camera2 API**.
* **Native Image Processing**: Performs real-time computer vision tasks using **OpenCV** in **C++** (connected via JNI).
* **Switchable Processing**: UI button to toggle native OpenCV effects on/off.

### Rendering
* **High-Performance Display**: Renders the video feed using **OpenGL ES 2.0** for a smooth.
* **Real-Time Shader Effects**: Applies visual effects (**Grayscale**, **Invert**) using **GLSL shaders**, controlled by a UI button.

### Networking & Web Interface
* **Embedded Web Server**: Runs a lightweight **Ktor WebSocket server** on the Android device.
* **Live Web Viewer**: A **React/TypeScript** web application that connects to the stream and displays the live feed.


## Prerequisites & Setup

### Android Setup
1.  **Install Android Studio**.
2.  Use the **SDK Manager** (`Settings` > `System Settings` > `Android SDK` > `SDK Tools`) to install:
    * SDK Platform 
    * NDK 
    * CMake

### OpenCV Setup
1.  Download **OpenCV for Android SDK** from the official site.
2.  Unzip and copy the `sdk` folder into the `app/src/main/cpp/` directory in the project Or just copy the file path and set Dir Name in Cmake File
3.  Make sure your `app/src/main/cpp/CMakeLists.txt` has this line:
    ```cmake
    set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/sdk)
    ```

### Web Viewer Setup
1.  Install **Node.js**.
2.  Navigate to the web viewer's project folder and run
    ```bash
    npm install
    ```

### Hardware & Network
* You need a physical Android device with **USB Debugging enabled**.
* Connect both your computer and Android device to the **same Wi-Fi network** to make use of the WebSocket (IpAdress + Port).