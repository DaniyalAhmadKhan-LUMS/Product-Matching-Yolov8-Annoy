package com.tencent.yolov8ncnn;

public class DetectedObject {
    public float x;
    public float y;
    public float width;
    public float height;
    public int label;
    public float prob;
    public String labelName; // For the label name
    public float bboxArea; // For the bounding box area
}
