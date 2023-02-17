# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)
# 声明一个变量 JNI_LIBS_DIR  并且赋值
# ${CMAKE_SOURCES_DIR} 是 cmake 自带的参数，可以直接使用
set(LIBS_DIR ${CMAKE_SOURCES_DIR}/libs)

add_library( # Sets the name of the library.
        x264_jni
        # Sets the library as a shared library.
        SHARED
        # Provides a relative path to your source file(s).
        x264_encoder.cpp
        )


# 1、添加一个库，在这里可以理解为声明了一个变量，来承载需要引入的库
add_library(
        x264 # 表示的是模块名称，可以自己任意取，例如：deviceutil
        SHARED # 这个是固定的，基本上表示共享库
        IMPORTED # 这个也基本上是固定的，表示当前是导入的，跟我们 java 的 import 差不多含义
)

# 2、将第一步声明的库名称(libraryname)，跟具体的库关联到一起
set_target_properties(
        x264 # 库的名称
        PROPERTIES IMPORTED_LOCATION # 表示当前库是导入的方式
        ${LIBS_DIR}/${ANDROID_ABI}/libx264.so # so 动态库的具体路径
)

# 3、如果第三方库有头文件的，为了在编码的时候可以有提示，使用下面的指令进行指定
# 这里是指定头文件那些的目录，这样指定后，编译器会扫描这个目录，编码的时候就可以提示到这里的方法了
include_directories(${LIBS_DIR}/include)

target_link_libraries( # Specifies the target library.
        x264_jni

        ${x264}
        # Links the target library to the log library
        # included in the NDK.
        nativewindow
        android
        jnigraphics
        dl
        m
        log
        EGL
        GLESv3)


# Enable if you want to debug cmake
# set(CMAKE_VERBOSE_MAKEFILE on)
