// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

#include <android/asset_manager_jni.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>

#include <android/log.h>

#include <jni.h>

#include <string>
#include <vector>

#include <platform.h>
#include <benchmark.h>
#include "yolo.h"

#include "ndkcamera.h"
#include "opencv-mobile-4.5.1-android/sdk/native/jni/include/opencv2/highgui/highgui.hpp"

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
//#include <opencv2/highgui/highgui.hpp>

#if __ARM_NEON
#include <arm_neon.h>
#endif // __ARM_NEON

static int draw_unsupported(cv::Mat& rgb)
{
    const char text[] = "unsupported";

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 1.0, 1, &baseLine);

    int y = (rgb.rows - label_size.height) / 2;
    int x = (rgb.cols - label_size.width) / 2;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
                  cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 1.0, cv::Scalar(0, 0, 0));

    return 0;
}

static int draw_fps(cv::Mat& rgb)
{
    // resolve moving average
    float avg_fps = 0.f;
    {
        static double t0 = 0.f;
        static float fps_history[10] = {0.f};

        double t1 = ncnn::get_current_time();
        if (t0 == 0.f)
        {
            t0 = t1;
            return 0;
        }

        float fps = 1000.f / (t1 - t0);
        t0 = t1;

        for (int i = 9; i >= 1; i--)
        {
            fps_history[i] = fps_history[i - 1];
        }
        fps_history[0] = fps;

        if (fps_history[9] == 0.f)
        {
            return 0;
        }

        for (int i = 0; i < 10; i++)
        {
            avg_fps += fps_history[i];
        }
        avg_fps /= 10.f;
    }

    char text[32];
    sprintf(text, "FPS=%.2f", avg_fps);

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 0.5, 1, &baseLine);

    int y = 0;
    int x = rgb.cols - label_size.width;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
                  cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 0.5, cv::Scalar(0, 0, 0));

    return 0;
}
jobject convertVectorToList(JNIEnv* env, const std::vector<Object>& objects) {
    // Get ArrayList class and method IDs
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID arrayListInit = env->GetMethodID(arrayListClass, "<init>", "()V");
    jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

    // Create an ArrayList
    jobject arrayList = env->NewObject(arrayListClass, arrayListInit);

    // Get DetectedObject class and its constructor method ID
    jclass detectedObjectClass = env->FindClass("com/tencent/yolov8ncnn/DetectedObject");
    jmethodID detectedObjectInit = env->GetMethodID(detectedObjectClass, "<init>", "()V");

    // Get DetectedObject class field IDs
    jfieldID xFieldID = env->GetFieldID(detectedObjectClass, "x", "F");
    jfieldID yFieldID = env->GetFieldID(detectedObjectClass, "y", "F");
    jfieldID widthFieldID = env->GetFieldID(detectedObjectClass, "width", "F");
    jfieldID heightFieldID = env->GetFieldID(detectedObjectClass, "height", "F");
    jfieldID labelFieldID = env->GetFieldID(detectedObjectClass, "label", "I");
    jfieldID probFieldID = env->GetFieldID(detectedObjectClass, "prob", "F");
    jfieldID labelNameFieldID = env->GetFieldID(detectedObjectClass, "labelName", "Ljava/lang/String;");
    jfieldID bboxAreaFieldID = env->GetFieldID(detectedObjectClass, "bboxArea", "F");

    for (const Object& obj : objects) {
        // Create a new DetectedObject instance
        jobject jobj = env->NewObject(detectedObjectClass, detectedObjectInit);

        // Set the fields for the DetectedObject
        env->SetFloatField(jobj, xFieldID, obj.rect.x);
        env->SetFloatField(jobj, yFieldID, obj.rect.y);
        env->SetFloatField(jobj, widthFieldID, obj.rect.width);
        env->SetFloatField(jobj, heightFieldID, obj.rect.height);
        env->SetIntField(jobj, labelFieldID, obj.label);
        env->SetFloatField(jobj, probFieldID, obj.prob);

        // Set the label name field
        jstring labelNameStr = env->NewStringUTF(obj.labelStr.c_str());
        env->SetObjectField(jobj, labelNameFieldID, labelNameStr);

        // Calculate and set the bounding box area field
        float bboxArea = obj.rect.width * obj.rect.height;
        env->SetFloatField(jobj, bboxAreaFieldID, bboxArea);

        // Add the DetectedObject to the ArrayList
        env->CallBooleanMethod(arrayList, arrayListAdd, jobj);

        // Clean up the local reference to the label name string
        env->DeleteLocalRef(labelNameStr);

        // Delete the local reference to the DetectedObject
        env->DeleteLocalRef(jobj);
    }

    return arrayList;
}
static Yolo* g_yolo = 0;
static ncnn::Mutex lock;
static jobject g_callback = 0;

class MyNdkCamera : public NdkCameraWindow
{
public:
    virtual void on_image_render(cv::Mat& rgb) const;
    void update_detected_objects(const std::vector<Object>& objects) const;
    const std::vector<Object>& get_last_detected_objects() const;
private:
    mutable std::vector<Object> last_detected_objects;


};
void MyNdkCamera::update_detected_objects(const std::vector<Object> &objects) const {
    last_detected_objects = objects;
}
const std::vector<Object>& MyNdkCamera::get_last_detected_objects() const {
    return last_detected_objects;
}
void MyNdkCamera::on_image_render(cv::Mat& rgb) const
{
    // nanodet
    {
        ncnn::MutexLockGuard g(lock);

        if (g_yolo)
        {
            std::vector<Object> objects;
            g_yolo->detect(rgb, objects);
            g_yolo->draw(rgb, objects);
            update_detected_objects(objects);
        }
        else
        {
            draw_unsupported(rgb);
        }
    }

    draw_fps(rgb);
}

static MyNdkCamera* g_camera = 0;

extern "C" {




JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnLoad");
    g_camera = new MyNdkCamera;
//    g_camera->get_last_detected_objects();
    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnUnload");

    {
        ncnn::MutexLockGuard g(lock);

        delete g_yolo;
        g_yolo = 0;
    }

    delete g_camera;
    g_camera = 0;
}

JNIEXPORT void JNICALL Java_com_tencent_yolov8ncnn_Yolov8Ncnn_setDetectionCallback(JNIEnv* env, jobject thiz, jobject callback) {
    g_callback = env->NewGlobalRef(callback);
}

// public native boolean loadModel(AssetManager mgr, int modelid, int cpugpu);
JNIEXPORT jboolean JNICALL Java_com_tencent_yolov8ncnn_Yolov8Ncnn_loadModel(JNIEnv* env, jobject thiz, jobject assetManager, jint modelid, jint cpugpu)
{
    if (modelid < 0 || modelid > 6 || cpugpu < 0 || cpugpu > 1)
    {
        return JNI_FALSE;
    }

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "loadModel %p", mgr);

    const char* modeltypes[] =
            {
                    "n",
                    "s",
            };

    const int target_sizes[] =
            {
                    320,
                    320,
            };

    const float mean_vals[][3] =
            {
                    {103.53f, 116.28f, 123.675f},
                    {103.53f, 116.28f, 123.675f},
            };

    const float norm_vals[][3] =
            {
                    { 1 / 255.f, 1 / 255.f, 1 / 255.f },
                    { 1 / 255.f, 1 / 255.f, 1 / 255.f },
            };

    const char* modeltype = modeltypes[(int)modelid];
    int target_size = target_sizes[(int)modelid];
    bool use_gpu = (int)cpugpu == 1;

    // reload
    {
        ncnn::MutexLockGuard g(lock);

        if (use_gpu && ncnn::get_gpu_count() == 0)
        {
            // no gpu
            delete g_yolo;
            g_yolo = 0;
        }
        else
        {
            if (!g_yolo)
                g_yolo = new Yolo;
            g_yolo->load(mgr, modeltype, target_size, mean_vals[(int)modelid], norm_vals[(int)modelid], use_gpu);
            g_yolo->loadMFnet(mgr, modeltype, target_size, mean_vals[(int)modelid], norm_vals[(int)modelid], use_gpu);
        }
    }

    return JNI_TRUE;
}

// public native boolean openCamera(int facing);
JNIEXPORT jboolean JNICALL Java_com_tencent_yolov8ncnn_Yolov8Ncnn_openCamera(JNIEnv* env, jobject thiz, jint facing)
{
    if (facing < 0 || facing > 1)
        return JNI_FALSE;

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "openCamera %d", facing);

    g_camera->open((int)facing);

    return JNI_TRUE;
}

// public native boolean closeCamera();
JNIEXPORT jboolean JNICALL Java_com_tencent_yolov8ncnn_Yolov8Ncnn_closeCamera(JNIEnv* env, jobject thiz)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "closeCamera");

    g_camera->close();

    return JNI_TRUE;
}

// public native boolean setOutputWindow(Surface surface);
JNIEXPORT jboolean JNICALL Java_com_tencent_yolov8ncnn_Yolov8Ncnn_setOutputWindow(JNIEnv* env, jobject thiz, jobject surface)
{
    ANativeWindow* win = ANativeWindow_fromSurface(env, surface);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "setOutputWindow %p", win);

    g_camera->set_window(win);

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_com_tencent_yolov8ncnn_Yolov8Ncnn_detectImage(JNIEnv* env, jobject thiz, jobject bitmap)
{
    // Convert Android Bitmap to cv::Mat
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);

    // if (info.format != ANDROID_BITMAP_FORMAT_RGB_565)
    //     return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
        return JNI_FALSE;
    void* pixels = 0;
    AndroidBitmap_lockPixels(env, bitmap, &pixels);
    if (pixels == NULL) {
        // Handle error here
        return JNI_FALSE;
    }
    cv::Mat img(info.height, info.width, CV_8UC4, pixels);

    cv::Mat img_bgr;
    cv::cvtColor(img, img_bgr, cv::COLOR_RGBA2BGR);
//    std::string savePath = "/storage/self/primary/DCIM/Camera";
//    cv::imwrite(savePath, img_bgr);



    // Lock the mutex and detect
    {
        ncnn::MutexLockGuard g(lock);
        if (g_yolo)
        {
            std::vector<Object> objects;
            g_yolo->detect(img_bgr, objects);
            g_yolo->drawGallery(img_bgr, objects);
        }
        else
        {
            draw_unsupported(img_bgr);
        }
    }
    cv::cvtColor(img_bgr, img, cv::COLOR_BGR2RGBA);
    AndroidBitmap_unlockPixels(env, bitmap);

    // Optionally: Convert the cv::Mat back to Bitmap if you've made modifications and want to reflect them in Java.

    // For simplicity, we're just returning JNI_TRUE for now. You may need to handle the return type based on your needs.
    return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL Java_com_tencent_yolov8ncnn_Yolov8Ncnn_detectStream(JNIEnv* env, jobject thiz, jobject bitmap)
{
    // Convert Android Bitmap to cv::Mat
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);

    // if (info.format != ANDROID_BITMAP_FORMAT_RGB_565)
    //     return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
        return JNI_FALSE;
    void* pixels = 0;
    AndroidBitmap_lockPixels(env, bitmap, &pixels);
    if (pixels == NULL) {
        // Handle error here
        return JNI_FALSE;
    }
    cv::Mat img(info.height, info.width, CV_8UC4, pixels);

    cv::Mat img_bgr;
    cv::cvtColor(img, img_bgr, cv::COLOR_RGBA2BGR);
    {
        ncnn::MutexLockGuard g(lock);
        if (g_yolo)
        {
            std::vector<Object> objects;
            g_yolo->detect(img_bgr, objects);
            g_yolo->draw(img_bgr, objects);
        }
        else
        {
            draw_unsupported(img_bgr);
        }
    }
    cv::cvtColor(img_bgr, img, cv::COLOR_BGR2RGBA);
    AndroidBitmap_unlockPixels(env, bitmap);

    return JNI_TRUE;
}
JNIEXPORT jobject JNICALL Java_com_tencent_yolov8ncnn_Yolov8Ncnn_getCameraYOLOout(JNIEnv* env, jobject thiz)
{
    const std::vector<Object>& objects = g_camera->get_last_detected_objects();

    // Use the convertVectorToList function to convert the vector of objects to a Java ArrayList
    jobject arrayListOfDetectedObjects = convertVectorToList(env, objects);
    return arrayListOfDetectedObjects;
}
}





