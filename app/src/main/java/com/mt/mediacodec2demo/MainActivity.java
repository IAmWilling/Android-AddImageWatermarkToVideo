package com.mt.mediacodec2demo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.mt.mediacodec2demo.databinding.ActivityMainBinding;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'mediacodec2demo' library on application startup.
    static {
        System.loadLibrary("jpeg");
        System.loadLibrary("turbojpeg");
        System.loadLibrary("yuv");
        System.loadLibrary("x264");
        System.loadLibrary("mediacodec2demo");
        System.loadLibrary("avcodec");
        System.loadLibrary("avfilter");
        System.loadLibrary("avformat");
        System.loadLibrary("avutil");
        System.loadLibrary("swresample");
        System.loadLibrary("swscale");
        System.loadLibrary("postproc");
        System.loadLibrary("avdevice");

    }

    private ActivityMainBinding binding;
    private static final String M_FILE_NAME = "test.mp4";
    private File mMp4File;
    //视频流轨道索引
    private int mVideoTrackIndex = -1;
    //视频流信息提取器
    private final MediaExtractor mVideoMediaExtractor = new MediaExtractor();
    //视频流解码器
    private MediaCodec mVideoDecode;
    //视频流解码后媒体格式
    private MediaFormat mVideoDecodeMediaFormat;
    //视频流编码器
    private MediaCodec mVideoEncode;
    //媒体合成器
    private MediaMuxer mediaMuxer;
    //视频流编码媒体格式
    private MediaFormat mVideoEncodeMediaFormat;
    //音频流解码媒体格式
    private MediaFormat mAudioDecodeMediaFormat;
    //音频流信息提取器
    private final MediaExtractor mAudioMediaExtractor = new MediaExtractor();
    //音频流数据缓冲区
    private ByteBuffer mAudioByteBuffer;
    private int mAudioTrackIndex = -1;
    private boolean isMediaMuxerStatr = false;
    long startTime = System.nanoTime();
    long endTime = System.nanoTime();
    long duration = (endTime - startTime) / 1000000000;
    private String filterPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        copyAssetsToFilePath();
        copyAssetsToLogoPath();
        String path = getFilesDir().getAbsolutePath() + "/" + M_FILE_NAME;
        String logoPath = getFilesDir().getAbsolutePath() + "/logo.jpeg";
        filterPath = getFilesDir().getAbsolutePath() + "/filter_test.mp4";
        File png = new File(filterPath);
        try {
            png.delete();
            png.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        native_find_video_info(path);
        native_filter_logo(logoPath);
        findVideoStreamInfo(path);
        findAudioStreamInfo(path);
        initVideoDecode();
        initVideoEncode();
        initMediaMuxer(filterPath);
        //音频流缓冲区初始化
        mAudioByteBuffer = ByteBuffer.allocate(mAudioDecodeMediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
        startTime = System.nanoTime();
        start();
    }

    /**
     * 初始化视频编码器
     */
    private void initVideoEncode() {
        try {
            int width = mVideoDecodeMediaFormat.getInteger(MediaFormat.KEY_WIDTH);
            int height = mVideoDecodeMediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
            mVideoEncode = MediaCodec.createEncoderByType(mVideoDecodeMediaFormat.getString(MediaFormat.KEY_MIME));
            mVideoEncodeMediaFormat = MediaFormat.createVideoFormat(mVideoDecodeMediaFormat.getString(MediaFormat.KEY_MIME), width, height);
            // 编码器输入是NV12格式
            mVideoEncodeMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            mVideoEncodeMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 3);
            mVideoEncodeMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mVideoDecodeMediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE));
            mVideoEncodeMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            mVideoEncodeMediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
            mVideoEncodeMediaFormat.setByteBuffer("csd-0", mVideoDecodeMediaFormat.getByteBuffer("csd-0"));
            mVideoEncodeMediaFormat.setByteBuffer("csd-1", mVideoDecodeMediaFormat.getByteBuffer("csd-1"));
            mVideoEncode.configure(mVideoEncodeMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * 初始化媒体合成器
     *
     * @param filterPath
     */
    private void initMediaMuxer(String filterPath) {
        try {
            mediaMuxer = new MediaMuxer(filterPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 寻找视频流信息
     *
     * @param path
     */
    private void findVideoStreamInfo(String path) {
        try {
            mVideoMediaExtractor.setDataSource(path);
            //寻找视频流
            for (int i = 0; i < mVideoMediaExtractor.getTrackCount(); i++) {
                MediaFormat fmt = mVideoMediaExtractor.getTrackFormat(i);
                if (fmt.getString(MediaFormat.KEY_MIME).contains("video/")) {
                    mVideoDecodeMediaFormat = fmt;
                    mVideoMediaExtractor.selectTrack(i);
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 寻找音频信息
     *
     * @param path
     */
    private void findAudioStreamInfo(String path) {
        try {
            mAudioMediaExtractor.setDataSource(path);
            //寻找音频流
            for (int i = 0; i < mVideoMediaExtractor.getTrackCount(); i++) {
                MediaFormat fmt = mVideoMediaExtractor.getTrackFormat(i);
                if (fmt.getString(MediaFormat.KEY_MIME).contains("audio/")) {
                    mAudioDecodeMediaFormat = fmt;
                    mAudioMediaExtractor.selectTrack(i);
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 初始化视频解码器
     */
    private void initVideoDecode() {
        try {
            int colorFmtType = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
            mVideoDecodeMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFmtType);
            mVideoDecodeMediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mVideoDecodeMediaFormat.getInteger(MediaFormat.KEY_WIDTH) * mVideoDecodeMediaFormat.getInteger(MediaFormat.KEY_HEIGHT) * 3 / 2);
            mVideoDecode = MediaCodec.createDecoderByType(mVideoDecodeMediaFormat.getString(MediaFormat.KEY_MIME));
            mVideoDecode.configure(mVideoDecodeMediaFormat, null, null, 0);
            mVideoDecode.setCallback(mediaCallback);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 开始视频编解码
     */
    private void start(){
        mVideoDecode.start();
        mVideoEncode.start();
    }

    /**
     * 全部结束
     */
    private void stopAll(){
        mVideoEncode.stop();
        mVideoDecode.stop();
        mediaMuxer.stop();
    }

    /**
     * 直接写入音频流数据
     * @param buffer
     * @param info
     */
    private void writeAudioData(ByteBuffer buffer,MediaCodec.BufferInfo info){
        int size = mAudioMediaExtractor.readSampleData(buffer, 0);
        info.size = size;
        info.presentationTimeUs = mAudioMediaExtractor.getSampleTime();
        info.flags = 0;
        mediaMuxer.writeSampleData(mAudioTrackIndex, buffer, info);
    }

    private void muxerAudio() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mAudioTrackIndex = mediaMuxer.addTrack(mAudioDecodeMediaFormat);
                //start需要音频和视频轨道全部添加后才能开始
                mediaMuxer.start();
                isMediaMuxerStatr = true;
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                writeAudioData(mAudioByteBuffer,info);
                while (mAudioMediaExtractor.advance()) {
                   writeAudioData(mAudioByteBuffer,info);
                }
                audioEncodeStop = true;
                if (videoEncodeStop && !isStop) {
                    stopAll();
                    isStop = true;
                    endTime = System.nanoTime();
                    duration = (endTime - startTime) / 1000000000; // 计算结果为秒
                    Log.d(TAG, "extime = " + duration + " s");
                    playVideo();
                }
            }
        }).start();
    }

    private boolean isStop = false;
    private String TAG = "yumi";
    private boolean videoEncodeStop = false;
    private boolean audioEncodeStop = false;
    private final MediaCodec.Callback mediaCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            if (index >= 0) {
                ByteBuffer inputBuffer = codec.getInputBuffer(index);
                if (inputBuffer != null) {
                    inputBuffer.clear();
                }
                int sampleSize = mVideoMediaExtractor.readSampleData(inputBuffer, 0);
                if (sampleSize < 0) {
                    codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    codec.queueInputBuffer(index, 0, sampleSize, mVideoMediaExtractor.getSampleTime(), 0);
                    //读取下一帧
                    mVideoMediaExtractor.advance();
                }
            }
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(TAG, "videoEncodeStop mediaMuxer");
                videoEncodeStop = true;
                if (audioEncodeStop && !isStop) {
                    mVideoEncode.stop();
                    mVideoDecode.stop();
                    mediaMuxer.stop();
                    isStop = true;
                    endTime = System.nanoTime();
                    duration = (endTime - startTime) / 1000000000; // 计算结果为秒
                    Log.d(TAG, "extime = " + duration + " s");
                    playVideo();
                }
                return;
            }
            if (index >= 0) {
                Image image = mVideoDecode.getOutputImage(index);
                if (image == null) {
                    codec.releaseOutputBuffer(index, true);
                    return;
                }
                //转换成I420
                byte[] i420 = getDataFromImage(image, COLOR_FormatI420);
                //native加水印
                byte[] nv12 = native_filter(i420, new INativeCallback() {
                    @Override
                    public void onFrame(byte[] data) {
                    }
                });
                // 将输入数据送入编码器
                int inputBufferIndex = mVideoEncode.dequeueInputBuffer(100000);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = mVideoEncode.getInputBuffer(inputBufferIndex);
                    inputBuffer.put(nv12);
                    mVideoEncode.queueInputBuffer(inputBufferIndex, 0, nv12.length, info.presentationTimeUs, 0);
                }
                // 获取编码后的输出数据
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                int outputBufferIndex = mVideoEncode.dequeueOutputBuffer(bufferInfo, 100000);
                if (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = mVideoEncode.getOutputBuffer(outputBufferIndex);
                    //处理编码后的数据
                    if (isMediaMuxerStatr && !videoEncodeStop) {
                        mediaMuxer.writeSampleData(mVideoTrackIndex, outputBuffer, bufferInfo);
                    }
                    mVideoEncode.releaseOutputBuffer(outputBufferIndex, false);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        long c = mVideoMediaExtractor.getSampleTime();
                        long d = mVideoDecodeMediaFormat.getLong(MediaFormat.KEY_DURATION);
                        float a = (c * 1.0f / d) * 100;
                        ((ProgressBar) binding.progress).setIndeterminate(false);
                        ((ProgressBar) binding.progress).setProgress((int) a);
                        ((ProgressBar) binding.progress).setMax(100);
                    }
                });

                codec.releaseOutputBuffer(index, false);
            }
        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
            codec.reset();
            Log.e(TAG, e.getMessage());
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
            if (mVideoTrackIndex == -1) {
                MediaFormat outputFormat = mVideoEncode.getOutputFormat();
                mVideoTrackIndex = mediaMuxer.addTrack(outputFormat);
                muxerAudio();
            }
        }
    };

    private void playVideo() {
        MediaController mediaController = new MediaController(this);
        //点击上一个视频和下一个视频的监听
        binding.videoView.setMediaController(mediaController);
        binding.videoView.setVideoPath(filterPath);
        binding.videoView.start();
        binding.srcVideoView.setVideoPath(getFilesDir().getAbsolutePath() + "/" + M_FILE_NAME);
        binding.srcVideoView.start();
    }


    public native byte[] native_filter(byte[] src, INativeCallback iNativeCallback);

    public native void native_find_video_info(String path);

    public native void native_filter_logo(String path);

    public native void i420ToNv21(byte[] i420, int w, int h, byte[] nv21);

    public native void nv21ToI420(byte[] nv21, int w, int h, byte[] i420);


    void copyAssetsToFilePath() {
        try {
            InputStream is = getAssets().open("gulou.mp4");
            String filesDirPath = getFilesDir().getAbsolutePath();
            mMp4File = new File(filesDirPath + "/" + M_FILE_NAME);
            FileOutputStream fos = new FileOutputStream(mMp4File);
            byte[] bytes = new byte[1024];
            int len = -1;
            len = is.read(bytes);
            while (len != -1) {
                fos.write(bytes);
                len = is.read(bytes);
            }
            is.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void copyAssetsToLogoPath() {
        try {
            InputStream is = getAssets().open("logo.jpeg");
            String filesDirPath = getFilesDir().getAbsolutePath();
            mMp4File = new File(filesDirPath + "/logo.jpeg");
            FileOutputStream fos = new FileOutputStream(mMp4File);
            byte[] bytes = new byte[1024];
            int len = -1;
            len = is.read(bytes);
            while (len != -1) {
                fos.write(bytes);
                len = is.read(bytes);
            }
            is.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final int COLOR_FormatI420 = 1;
    private static final int COLOR_FormatNV21 = 2;

    private static boolean isImageFormatSupported(Image image) {
        int format = image.getFormat();
        switch (format) {
            case ImageFormat.YUV_420_888:
            case ImageFormat.NV21:
            case ImageFormat.YV12:
                return true;
        }
        return false;
    }

    private static byte[] getDataFromImage(Image image, int colorFormat) {
        if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
            throw new IllegalArgumentException("only support COLOR_FormatI420 " + "and COLOR_FormatNV21");
        }
        if (!isImageFormatSupported(image)) {
            throw new RuntimeException("can't convert Image to byte array, format " + image.getFormat());
        }
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];
        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = width * height;
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height + 1;
                        outputStride = 2;
                    }
                    break;
                case 2:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = (int) (width * height * 1.25);
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height;
                        outputStride = 2;
                    }
                    break;
            }
            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();

            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
        }
        return data;
    }
}