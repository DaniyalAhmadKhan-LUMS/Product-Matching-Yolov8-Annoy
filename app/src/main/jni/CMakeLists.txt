project(yolov8ncnn)

cmake_minimum_required(VERSION 3.10)

set(CMAKE_SOURCE_DIR /home/daniyal/Documents/Upwork/Jared/Yolo+MobileFaceNet+Annoy/combined/app/src/main/jni)

# Paths for the Annoy library and headers based on the provided directory structure
set(ANNOY_INCLUDE_DIR ${CMAKE_SOURCE_DIR}/annoy/src)
set(ANNOY_LIB_DIR ${CMAKE_SOURCE_DIR}/annoy/local/${ANDROID_ABI})

# Include the Annoy header files
include_directories(${ANNOY_INCLUDE_DIR})

set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/opencv-mobile-4.5.1-android/sdk/native/jni)
find_package(OpenCV REQUIRED core imgproc)

set(ncnn_DIR ${CMAKE_SOURCE_DIR}/ncnn-20220420-android-vulkan/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

add_library(yolov8ncnn SHARED yolov8ncnn.cpp yolo.cpp ndkcamera.cpp)

# Link the Annoy static library
add_library(annoy STATIC IMPORTED)
set_target_properties(annoy PROPERTIES IMPORTED_LOCATION ${ANNOY_LIB_DIR}/libannoy.a)

# Link all libraries
target_link_libraries(yolov8ncnn ncnn ${OpenCV_LIBS} camera2ndk mediandk annoy)