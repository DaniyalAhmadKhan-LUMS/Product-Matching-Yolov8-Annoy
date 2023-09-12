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
    std::pair<std::vector<std::vector<float>>, std::vector<std::string>> load_feature_db_txt(const std::string& str, const std::string& delimiter)
    {
        std::vector<std::vector<float>> featureVectorsTemp;
        std::vector<std::string> labelsTemp;

        std::string::size_type pos = 0;
        std::string::size_type prev = 0;
        while ((pos = str.find(delimiter, prev)) != std::string::npos)
        {
            std::string line = str.substr(prev, pos - prev);
            std::istringstream iss(line);
            std::string featureStr, label;
            std::getline(iss, featureStr, ':');
            featureStr = featureStr.substr(1, featureStr.size() - 2);
            std::getline(iss, label, ':');
            label = label.substr(2, label.size() - 3);

            std::vector<float> featureVec;
            std::istringstream featureStream(featureStr);
            std::string val;
            while (std::getline(featureStream, val, ' ')) {
                if (!val.empty()){
                    featureVec.push_back(std::stof(val));
                }
            }
            featureVectorsTemp.push_back(featureVec);
            labelsTemp.push_back(label);
            prev = pos + delimiter.size();

        }

        // To get the last substring (or only, if delimiter is not found)
        return {featureVectorsTemp, labelsTemp};
    }
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
float dotProduct(const std::vector<float>& A, const std::vector<float>& B) {
    float sum = 0;
    for (size_t i = 0; i < A.size(); ++i) {
        sum += A[i] * B[i];
    }
    return sum;
}

// Function to calculate magnitude (Euclidean norm) of a vector
float magnitude(const std::vector<float>& A) {
    return std::sqrt(dotProduct(A, A));
}

// Function to calculate cosine similarity between two vectors
float cosineSimilarity(const std::vector<float>& A, const std::vector<float>& B) {
    return dotProduct(A, B) / (magnitude(A) * magnitude(B));
}

// Function to find the index of the most similar vector in V2 to V1
int findMostSimilar(const std::vector<float>& V1, const std::vector<std::vector<float>>& V2) {
    int mostSimilarIndex = -1;
    float highestSimilarity = -1.0;  // Initialize to -1 as cosine similarity ranges from -1 to 1

    for (size_t i = 0; i < V2.size(); ++i) {
        float similarity = cosineSimilarity(V1, V2[i]);
        if (similarity > highestSimilarity) {
            highestSimilarity = similarity;
            mostSimilarIndex = i;
        }
    }

    return mostSimilarIndex;
}
private:
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
