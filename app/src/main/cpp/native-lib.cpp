#include <jni.h>
#include <string>
#include <unistd.h>
#include "myheader.h"

int videoIdx = -1;
const char *logo_path = nullptr;
AVCodecParameters *video_parametres = nullptr;
const AVFilter *buffer_filter = nullptr;
const AVFilter *buffersink_filter = nullptr;
AVFilterContext *buffer_filter_ctx = nullptr;
AVFilterContext *buffersink_filter_ctx = nullptr;
AVFilterInOut *in_filter = nullptr;
AVFilterInOut *out_filter = nullptr;
AVFilterGraph *filterGraph = nullptr;
AVFormatContext *fmt_ctx = nullptr;
AVCodecContext *decode_ctx;
const AVCodec *decode;
AVFrame *filter_frame = av_frame_alloc();
AVFrame *src_frame = av_frame_alloc();

void init_avfilter();

int init_decode();

void i420ToNv12(uint8_t *src_i420_data, jint width, jint height, jbyte *src_nv21_data);

void i420ToNv21(jbyte *src_i420_data, jint width, jint height, jbyte *src_nv21_data);

void free(void *opaque, uint8_t *data);

void i420ToPng(uint8_t *i420);

void frameToPng(AVFrame *frame, const char *png_path);

void mirrorI420(uint8_t *src_i420_data, jint width, jint height, uint8_t *dst_i420_data);


extern "C" JNIEXPORT jstring JNICALL
Java_com_mt_mediacodec2demo_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

void init_avfilter() {
    //buffer滤镜 负责将原始视频帧添加到滤镜图中
    buffer_filter = avfilter_get_by_name("buffer");
    //buffersink滤镜 用于从滤镜图中获取处理后的视频帧。
    buffersink_filter = avfilter_get_by_name("buffersink");

    char videoInfoArgs[256];
    //buffer滤镜参数
    snprintf(videoInfoArgs, sizeof(videoInfoArgs),
             "video_size=%dx%d:pix_fmt=%d:time_base=%d/%d:pixel_aspect=%d:%d",
             video_parametres->width, video_parametres->height, video_parametres->format,
             fmt_ctx->streams[videoIdx]->time_base.num, fmt_ctx->streams[videoIdx]->time_base.den,
             video_parametres->sample_aspect_ratio.num, video_parametres->sample_aspect_ratio.den);
    //创建滤镜图
    filterGraph = avfilter_graph_alloc();
    if (!filterGraph) {
        return;
    }
    //创建滤镜输入端口
    int ret = avfilter_graph_create_filter(&buffer_filter_ctx, buffer_filter, "in", videoInfoArgs,
                                           nullptr, filterGraph);
    if (ret < 0) {
        return;
    }
    ret = avfilter_graph_create_filter(&buffersink_filter_ctx, buffersink_filter, "out", nullptr,
                                       nullptr, filterGraph);
    if (ret < 0) {
        return;
    }

    enum AVPixelFormat pixelFormat[] = {AV_PIX_FMT_YUV420P,
                                        (AVPixelFormat) (video_parametres->format)};
    //它被用来设置 buffersink 滤镜的像素格式。 在处理视频帧时，buffersink 滤镜将尝试使用 AV_PIX_FMT_YUV420P 或 video_parametres->format 这两种像素格式之一。
    av_opt_set_int_list(buffersink_filter_ctx, "pix_fmts", pixelFormat, AV_PIX_FMT_YUV420P,
                        AV_OPT_SEARCH_CHILDREN);
    in_filter = avfilter_inout_alloc();
    in_filter->next = nullptr;
    in_filter->name = av_strdup("out");
    in_filter->filter_ctx = buffersink_filter_ctx;
    in_filter->pad_idx = 0;

    out_filter = avfilter_inout_alloc();
    out_filter->next = nullptr;
    out_filter->name = av_strdup("in");
    out_filter->filter_ctx = buffer_filter_ctx;
    out_filter->pad_idx = 0;
//    movie=%s,scale=1:-1[wm];[in][wm]overlay=5:5[out]
//lutyuv='u=128:v=128'
    char filters[256];
    snprintf(filters, sizeof(filters), "movie=%s,scale=200:-1[wm];[in][wm]overlay=50:10[out]",
             logo_path);
    ret = avfilter_graph_parse_ptr(filterGraph, filters, &in_filter, &out_filter, nullptr);
    if (ret < 0) {
        return;
    }
    ret = avfilter_graph_config(filterGraph, nullptr);

    if (ret < 0) {
        return;
    }
}


int init_decode() {
    decode = avcodec_find_decoder(video_parametres->codec_id);
    if (!decode) {
        return -1;
    }
    decode_ctx = avcodec_alloc_context3(decode);
    if (!decode_ctx) {
        return -1;
    }
    int ret = avcodec_parameters_to_context(decode_ctx, video_parametres);
    if (ret < 0) {
        return -1;
    }
    ret = avcodec_open2(decode_ctx, decode, nullptr);
    if (ret < 0) {
        return -1;
    }
    return 1;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_mt_mediacodec2demo_MainActivity_native_1find_1video_1info(JNIEnv *env, jobject thiz,
                                                                   jstring path) {
    const char *m_path = env->GetStringUTFChars(path, 0);
    fmt_ctx = avformat_alloc_context();
    int ret = avformat_open_input(&fmt_ctx, m_path, nullptr, nullptr);
    if (ret < 0 || !fmt_ctx) {
        exit(1);
    }
    ret = avformat_find_stream_info(fmt_ctx, nullptr);
    if (ret < 0) {
        exit(1);
    }
    for (int i = 0; i < fmt_ctx->nb_streams; i++) {
        if (fmt_ctx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
            videoIdx = i;
            break;
        }
    }
    video_parametres = fmt_ctx->streams[videoIdx]->codecpar;
}
extern "C"
JNIEXPORT void JNICALL
Java_com_mt_mediacodec2demo_MainActivity_native_1filter_1logo(JNIEnv *env, jobject thiz,
                                                              jstring path) {
    logo_path = env->GetStringUTFChars(path, 0);
    init_avfilter();

}
extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_mt_mediacodec2demo_MainActivity_native_1filter(JNIEnv *env, jobject thiz, jbyteArray src,
                                                        jobject i_native_callback) {
    jbyteArray array;
    jclass cls = env->GetObjectClass(i_native_callback);
    jmethodID mid = env->GetMethodID(cls, "onFrame", "([B)V");

    jsize length = env->GetArrayLength(src);
    uint8_t *src_data = (uint8_t *) av_malloc(length);
    env->GetByteArrayRegion(src, 0, length, (jbyte *) src_data);
    av_image_fill_arrays(src_frame->data, src_frame->linesize, src_data,
                         (AVPixelFormat) video_parametres->format, video_parametres->width,
                         video_parametres->height, 1);
    src_frame->width = video_parametres->width;
    src_frame->height = video_parametres->height;
    src_frame->time_base = fmt_ctx->streams[videoIdx]->time_base;
    src_frame->sample_aspect_ratio = video_parametres->sample_aspect_ratio;
    src_frame->format = video_parametres->format;
    //pts = 0 表示每一帧显示的时间是0 直接显示
    src_frame->pts = 0;
    //添加到滤镜中
    int ret = av_buffersrc_add_frame_flags(buffer_filter_ctx, src_frame,
                                           AV_BUFFERSRC_FLAG_KEEP_REF);
    if (ret >= 0) {
        ret = av_buffersink_get_frame(buffersink_filter_ctx, filter_frame);
        if (ret >= 0) {
            //滤镜已经添加了
            int width = filter_frame->width;
            int height = filter_frame->height;
            int y_size = width * height;
            int uv_size = y_size / 4;

//            // 分配内存，用于存储i420数据
//            uint8_t *i420_data = (uint8_t *) malloc(y_size + uv_size * 2);
//
//            // 获取Y分量数据
//            for (int i = 0; i < height; i++) {
//                memcpy(i420_data + i * width, filter_frame->data[0] + i * filter_frame->linesize[0],
//                       width);
//            }
//
//            // 获取U分量数据
//            for (int i = 0; i < height / 2; i++) {
//                memcpy(i420_data + y_size + i * width / 2,
//                       filter_frame->data[1] + i * filter_frame->linesize[1], width / 2);
//            }
//
//            // 获取V分量数据
//            for (int i = 0; i < height / 2; i++) {
//                memcpy(i420_data + y_size + uv_size + i * width / 2,
//                       filter_frame->data[2] + i * filter_frame->linesize[2], width / 2);
//            }
            AVFrame *i420_frame = av_frame_alloc();
            uint8_t *out_buf = (uint8_t *) av_malloc(
                    av_image_get_buffer_size(AV_PIX_FMT_YUV420P, width, height, 1));
            av_image_fill_arrays(i420_frame->data, i420_frame->linesize, out_buf,
                                 AV_PIX_FMT_YUV420P, width, height, 1);
            struct SwsContext *img_ctx = sws_getContext(
                    filter_frame->width, filter_frame->height,
                    (AVPixelFormat) filter_frame->format, //源地址长宽以及数据格式
                    filter_frame->width, filter_frame->height, AV_PIX_FMT_YUV420P,  //目的地址长宽以及数据格式
                    SWS_BICUBIC, NULL, NULL, NULL);
            sws_scale(img_ctx, filter_frame->data, filter_frame->linesize, 0, height,
                      i420_frame->data, i420_frame->linesize);



            uint8_t *i420_data = (uint8_t *) malloc(y_size * 3 / 2);
            memcpy(i420_data, i420_frame->data[0], y_size);
            memcpy(i420_data + y_size, i420_frame->data[1], uv_size);
            memcpy(i420_data + y_size + uv_size, i420_frame->data[2], uv_size);
//            uint8_t *i420_data_mirror = (uint8_t *) malloc(y_size * 3 / 2);
            // 水平镜像处理
//            mirrorI420(i420_data,width,height,i420_data_mirror);
//            i420ToPng(i420_data);
//            frameToPng(filter_frame,"/data/user/0/com.mt.mediacodec2demo/files/filter_test.png");
            jbyteArray array_nv12 = env->NewByteArray(y_size * 3 / 2);
            jbyte *nv12 = env->GetByteArrayElements(array_nv12, 0);
            i420ToNv12(i420_data, video_parametres->width, video_parametres->height, nv12);
            env->SetByteArrayRegion(array_nv12, 0, y_size * 3 / 2,
                                    (jbyte *) (nv12));
            env->CallVoidMethod(i_native_callback, mid, array_nv12);
            array = array_nv12;
            free(i420_data);
//            free(i420_data_mirror);
            av_frame_free(&i420_frame);
            free(out_buf);
            sws_freeContext(img_ctx);
        }
    }
    av_frame_unref(filter_frame);
    av_frame_unref(src_frame);
    av_free(src_data);
    return array;
}

// i420 --> nv12
void i420ToNv12(uint8_t *src_i420_data, jint width, jint height, jbyte *src_nv21_data) {
    jint src_y_size = width * height;
    jint src_u_size = (width >> 1) * (height >> 1);

    jbyte *src_nv21_y_data = src_nv21_data;
    jbyte *src_nv21_uv_data = src_nv21_data + src_y_size;

    uint8_t *src_i420_y_data = src_i420_data;
    uint8_t *src_i420_u_data = src_i420_data + src_y_size;
    uint8_t *src_i420_v_data = src_i420_data + src_y_size + src_u_size;


    libyuv::I420ToNV12(
            (const uint8_t *) src_i420_y_data, width,
            (const uint8_t *) src_i420_u_data, width >> 1,
            (const uint8_t *) src_i420_v_data, width >> 1,
            (uint8_t *) src_nv21_y_data, width,
            (uint8_t *) src_nv21_uv_data, width,
            width, height);
}

void i420ToNv21(jbyte *src_i420_data, jint width, jint height, jbyte *src_nv21_data) {
    jint src_y_size = width * height;
    jint src_u_size = (width >> 1) * (height >> 1);

    jbyte *src_nv21_y_data = src_nv21_data;
    jbyte *src_nv21_uv_data = src_nv21_data + src_y_size;

    jbyte *src_i420_y_data = src_i420_data;
    jbyte *src_i420_u_data = src_i420_data + src_y_size;
    jbyte *src_i420_v_data = src_i420_data + src_y_size + src_u_size;


    libyuv::I420ToNV21(
            (const uint8_t *) src_i420_y_data, width,
            (const uint8_t *) src_i420_u_data, width >> 1,
            (const uint8_t *) src_i420_v_data, width >> 1,
            (uint8_t *) src_nv21_y_data, width,
            (uint8_t *) src_nv21_uv_data, width,
            width, height);
}

void i420ToPng(uint8_t *i420) {
    AVFrame *yuv_frame = av_frame_alloc();
    AVFrame *rgb_frame = av_frame_alloc();
    const char *png_path = "/data/user/0/com.mt.mediacodec2demo/files/filter_test.png";

    FILE *file = fopen(png_path, "w+");

    const AVCodec *codec = avcodec_find_encoder(AV_CODEC_ID_PNG);
    if (!codec) {
        return;
    }
    AVCodecContext *ctx = avcodec_alloc_context3(codec);
    if (!ctx) {
        return;
    }
    ctx->height = video_parametres->height;
    ctx->width = video_parametres->width;
    ctx->pix_fmt = AV_PIX_FMT_RGB24;
    ctx->time_base.den = 25;
    ctx->time_base.num = 1;
    ctx->gop_size = 10;
    ctx->max_b_frames = 1;
    ctx->bit_rate = video_parametres->bit_rate;

    int ret = avcodec_open2(ctx, codec, nullptr);
    if (ret < 0) {
        return;
    }
    int size = av_image_get_buffer_size(AV_PIX_FMT_RGB24, ctx->width, ctx->height, 1);
    uint8_t *out_buf = (uint8_t *) av_malloc(size);
    av_image_fill_arrays(rgb_frame->data, rgb_frame->linesize, out_buf, AV_PIX_FMT_RGB24,
                         ctx->width, ctx->height, 1);


    av_image_fill_arrays(yuv_frame->data, yuv_frame->linesize, i420,
                         AV_PIX_FMT_YUV420P, video_parametres->width,
                         video_parametres->height, 1);

    SwsContext *swx_ctx = sws_getContext(ctx->width, ctx->height, AV_PIX_FMT_YUV420P, ctx->width,
                                         ctx->height, AV_PIX_FMT_RGB24, SWS_BICUBIC,
                                         nullptr, nullptr, nullptr);
    rgb_frame->format = AV_PIX_FMT_RGB24;
    rgb_frame->width = ctx->width;
    rgb_frame->height = ctx->height;

    sws_scale(swx_ctx, (const uint8_t *const *) yuv_frame->data, yuv_frame->linesize, 0,
              ctx->height, rgb_frame->data,
              rgb_frame->linesize);


    ret = avcodec_send_frame(ctx, rgb_frame);
    if (ret < 0) {
        char *str = av_err2str(ret);
        return;
    }
    AVPacket *pkt = av_packet_alloc();
    ret = avcodec_receive_packet(ctx, pkt);
    if (ret < 0) {
        return;
    }
    fwrite(pkt->data, 1, pkt->size, file);
    fclose(file);
    av_frame_free(&yuv_frame);
    av_frame_free(&rgb_frame);
    av_free(out_buf);
    sws_freeContext(swx_ctx);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_mt_mediacodec2demo_MainActivity_i420ToNv21(JNIEnv *env, jobject thiz, jbyteArray i420,
                                                    jint w, jint h, jbyteArray nv21) {
    jbyte *src_i420_data = env->GetByteArrayElements(i420, NULL);
    jbyte *dst_nv21_data = env->GetByteArrayElements(nv21, NULL);
    // i420转化为nv21
    i420ToNv21(src_i420_data, w, h, dst_nv21_data);
}

void frameToPng(AVFrame *frame, const char *png_path) {
    // 创建一个AVFrame结构体，用于存储RGB数据
    AVFrame *rgb_frame = av_frame_alloc();
    rgb_frame->format = AV_PIX_FMT_RGB24;
    rgb_frame->width = frame->width;
    rgb_frame->height = frame->height;

    av_frame_get_buffer(rgb_frame, 0);

    // 如果输入帧的像素格式不是RGB24，那么使用sws_scale函数将其转换为RGB24
    if (frame->format != AV_PIX_FMT_RGB24) {
        SwsContext *sws_ctx = sws_getContext(frame->width, frame->height,
                                             (AVPixelFormat) frame->format,
                                             frame->width, frame->height, AV_PIX_FMT_RGB24,
                                             SWS_BICUBIC, nullptr, nullptr, nullptr);
        sws_scale(sws_ctx, frame->data, frame->linesize, 0, frame->height,
                  rgb_frame->data, rgb_frame->linesize);
        sws_freeContext(sws_ctx);
    } else {
        av_frame_copy(rgb_frame, frame);
    }

    // 使用PNG编码器对RGB数据进行编码
    const AVCodec *codec = avcodec_find_encoder(AV_CODEC_ID_PNG);
    AVCodecContext *ctx = avcodec_alloc_context3(codec);
    ctx->pix_fmt = AV_PIX_FMT_RGB24;
    ctx->width = rgb_frame->width;
    ctx->height = rgb_frame->height;
    ctx->time_base.den = 25;
    ctx->time_base.num = 1;
    ctx->gop_size = 10;
    ctx->max_b_frames = 1;
    ctx->bit_rate = video_parametres->bit_rate;
    if (avcodec_open2(ctx, codec, nullptr) < 0) {
        return;
    }

    AVPacket *pkt = av_packet_alloc();
    if (avcodec_send_frame(ctx, rgb_frame) < 0) {
        return;
    }

    if (avcodec_receive_packet(ctx, pkt) < 0) {
        return;
    }

    // 将编码后的数据写入文件
    FILE *file = fopen(png_path, "wb");
    fwrite(pkt->data, 1, pkt->size, file);
    fclose(file);

    // 释放资源
    av_packet_free(&pkt);
    avcodec_free_context(&ctx);
    av_frame_free(&rgb_frame);
}

// nv21 --> i420
void nv21ToI420(jbyte *src_nv21_data, jint width, jint height, jbyte *src_i420_data) {
    jint src_y_size = width * height;
    jint src_u_size = (width >> 1) * (height >> 1);

    jbyte *src_nv21_y_data = src_nv21_data;
    jbyte *src_nv21_vu_data = src_nv21_data + src_y_size;

    jbyte *src_i420_y_data = src_i420_data;
    jbyte *src_i420_u_data = src_i420_data + src_y_size;
    jbyte *src_i420_v_data = src_i420_data + src_y_size + src_u_size;

    libyuv::NV21ToI420((const uint8_t *) src_nv21_y_data, width,
                       (const uint8_t *) src_nv21_vu_data, width,
                       (uint8_t *) src_i420_y_data, width,
                       (uint8_t *) src_i420_u_data, width >> 1,
                       (uint8_t *) src_i420_v_data, width >> 1,
                       width, height);
}


// 镜像
void mirrorI420(uint8_t *src_i420_data, jint width, jint height, uint8_t *dst_i420_data) {
    jint src_i420_y_size = width * height;
    // jint src_i420_u_size = (width >> 1) * (height >> 1);
    jint src_i420_u_size = src_i420_y_size >> 2;

    uint8_t *src_i420_y_data = src_i420_data;
    uint8_t *src_i420_u_data = src_i420_data + src_i420_y_size;
    uint8_t *src_i420_v_data = src_i420_data + src_i420_y_size + src_i420_u_size;

    uint8_t *dst_i420_y_data = dst_i420_data;
    uint8_t *dst_i420_u_data = dst_i420_data + src_i420_y_size;
    uint8_t *dst_i420_v_data = dst_i420_data + src_i420_y_size + src_i420_u_size;

    libyuv::I420Mirror((const uint8_t *) src_i420_y_data, width,
                       (const uint8_t *) src_i420_u_data, width >> 1,
                       (const uint8_t *) src_i420_v_data, width >> 1,
                       (uint8_t *) dst_i420_y_data, width,
                       (uint8_t *) dst_i420_u_data, width >> 1,
                       (uint8_t *) dst_i420_v_data, width >> 1,
                       width, height);
}






extern "C"
JNIEXPORT void JNICALL
Java_com_mt_mediacodec2demo_MainActivity_nv21ToI420(JNIEnv *env, jobject thiz, jbyteArray nv21,
                                                    jint w, jint h, jbyteArray i420) {
    jbyte *src_nv21_data = env->GetByteArrayElements(nv21, NULL);
    jbyte *dst_i420_data = env->GetByteArrayElements(i420, NULL);
    // nv21转化为i420
    nv21ToI420(src_nv21_data, w, h, dst_i420_data);
    env->ReleaseByteArrayElements(i420, dst_i420_data, 0);
}