cmake_minimum_required(VERSION 3.18.1)
include_directories(include)
include_directories(jpeg)
include_directories(libyuv)



# Declares and names the project.
project("mediacodec2demo")

add_library(
        # 指定库名称
        yuv
        # 导入库类型 .a文件则是静态STATIC .so文件则是动态SHARED
        SHARED
        # 告知 CMake yuv 是导入的库
        IMPORTED)
set_target_properties(
        # add新增的库名称要对应
        yuv
        # 指定属性（本地导入的已有库）
        PROPERTIES
        # 指定属性（本地导入的已有库）
        IMPORTED_LOCATION
        # 动态库对应的so文件路径 CMAKE_CURRENT_SOURCE_DIR为当前文件路径开始
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${CMAKE_ANDROID_ARCH_ABI}/libyuv.so)
add_library(jpeg
        SHARED
        IMPORTED)
set_target_properties(jpeg
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${CMAKE_ANDROID_ARCH_ABI}/libjpeg.so
        )
add_library(turbojpeg
        SHARED
        IMPORTED)
set_target_properties(turbojpeg
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${CMAKE_ANDROID_ARCH_ABI}/libturbojpeg.so
        )
add_library(x264
        SHARED
        IMPORTED)
set_target_properties(x264
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${CMAKE_ANDROID_ARCH_ABI}/libx264.so
        )
add_library(avcodec
        SHARED
        IMPORTED)
set_target_properties(avcodec
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${CMAKE_ANDROID_ARCH_ABI}/libavcodec.so
        )
add_library(avfilter
        SHARED
        IMPORTED)
set_target_properties(avfilter
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${CMAKE_ANDROID_ARCH_ABI}/libavfilter.so
        )
add_library(avformat
        SHARED
        IMPORTED)
set_target_properties(avformat
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${CMAKE_ANDROID_ARCH_ABI}/libavformat.so
        )
add_library(avutil
        SHARED
        IMPORTED)
set_target_properties(avutil
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${CMAKE_ANDROID_ARCH_ABI}/libavutil.so
        )
add_library(swresample
        SHARED
        IMPORTED)
set_target_properties(swresample
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${CMAKE_ANDROID_ARCH_ABI}/libswresample.so
        )
add_library(swscale
        SHARED
        IMPORTED)
set_target_properties(swscale
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${CMAKE_ANDROID_ARCH_ABI}/libswscale.so
        )

add_library(postproc
        SHARED
        IMPORTED)
set_target_properties(postproc
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${CMAKE_ANDROID_ARCH_ABI}/libpostproc.so
        )
add_library(avdevice
        SHARED
        IMPORTED)
set_target_properties(avdevice
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${CMAKE_ANDROID_ARCH_ABI}/libavdevice.so
        )


find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log )
find_library(
        android-lib
        android
)
add_library( # Sets the name of the library.
        mediacodec2demo

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        native-lib.cpp)
target_link_libraries( # Specifies the target library.
        mediacodec2demo
        avcodec
        avfilter
        avformat
        avutil
        swresample
        swscale
        postproc
        avdevice
        x264
        yuv
        jpeg
        turbojpeg
        # Links the target library to the log library
        # included in the NDK.
        ${log-lib}
        ${android-lib})