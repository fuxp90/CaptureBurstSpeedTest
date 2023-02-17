//
// Created by fup on 2021/8/28.
//

#ifndef TSCAMERABASE_ALOG_H
#define TSCAMERABASE_ALOG_H
#include <android/log.h>

#define TAG "X264"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

#endif //TSCAMERABASE_ALOG_H
