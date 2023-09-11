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

#ifndef YOLO_H
#define YOLO_H

#include <opencv2/core/core.hpp>
#include <string>
#include <vector>
#include <utility>
#include <net.h>
#include <fstream>
#include <iostream>
#include <cmath>
#include <algorithm>
#include "annoylib.h"
#include "kissrandom.h"

struct Object
{
    cv::Rect_<float> rect;
    int label;
    float prob;
};
struct GridAndStride
{
    int grid0;
    int grid1;
    int stride;
};
class Yolo
{
public:
    Yolo();
    

    int load(const char* modeltype, int target_size, const float* mean_vals, const float* norm_vals, bool use_gpu = false);
    int loadMFnet(AAssetManager* mgr, const char* modeltype, int target_size, const float* mean_vals, const float* norm_vals, bool use_gpu = false);
    int load(AAssetManager* mgr, const char* modeltype, int target_size, const float* mean_vals, const float* norm_vals, bool use_gpu = false);
    int detect(const cv::Mat& rgb, std::vector<Object>& objects, float prob_threshold = 0.4f, float nms_threshold = 0.5f);
    int draw(cv::Mat& rgb, const std::vector<Object>& objects);

std::vector<float> convert_to_vector(const ncnn::Mat& mat) {
    std::vector<float> vec;
    int channels = mat.c;
    int width = mat.w;
    int height = mat.h;

    vec.reserve(channels * width * height);

    for (int c = 0; c < channels; ++c) {
        for (int h = 0; h < height; ++h) {
            for (int w = 0; w < width; ++w) {
                float value = mat.channel(c).row(h)[w];
                vec.push_back(value);
            }
        }
    }

    return vec;
}



private:

    const int n_trees = 10;
    const int di = 512;
    Annoy::AnnoyIndex<int, float, Annoy::Angular, Annoy::Kiss32Random, Annoy::AnnoyIndexSingleThreadedBuildPolicy> index;
    ncnn::Net yolo;
    int target_size;
    float mean_vals[3];
    float norm_vals[3];
    ncnn::UnlockedPoolAllocator blob_pool_allocator;
    ncnn::PoolAllocator workspace_pool_allocator;
    std::vector<std::vector<float>> featureVectors;
    std::vector<std::string> labels;
    // SqueezNet INT
    ncnn::UnlockedPoolAllocator g_blob_pool_allocator;
    ncnn::PoolAllocator g_workspace_pool_allocator;

    // std::vector<std::string> squeezenet_words;
    ncnn::Net MFnet;
    ncnn::Option opt;
};

#endif // NANODET_H
