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
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
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

    private int mVideoTrackIndex = -1;
    private static final int YUV420P = 1;
    private static final int YUV420SP = 2;
    private static final int NV21 = 3;
    private ActivityMainBinding binding;
    private static final String M_FILE_NAME = "test.mp4";
    private File mMp4File;
    MediaExtractor mediaExtractor = new MediaExtractor();
    MediaCodec decode;
    MediaFormat finalVideoMediaFmt;
    MediaCodec encode;
    MediaMuxer mediaMuxer;
    MediaFormat mediaFormat;
    MediaFormat readAudioMediaFormat;
    MediaExtractor audioMediaExtractor = new MediaExtractor();
    private int audioExtractorSelectIndex = -1;
    private ByteBuffer audioBuf;
    private int mAudioTrackIndex = -1;
    private boolean isMediaMuxerStatr = false;
    long startTime = System.nanoTime();
    long endTime = System.nanoTime();
    long duration = (endTime - startTime) / 1000000000;
    MediaFormat audioFormat;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        copyAssetsToFilePath();
        copyAssetsToLogoPath();
        String path = getFilesDir().getAbsolutePath() + "/" + M_FILE_NAME;
        String logoPath = getFilesDir().getAbsolutePath() + "/logo.jpeg";
        String pngPath = getFilesDir().getAbsolutePath() + "/filter_test.mp4";
        File png = new File(pngPath);
        try {
            png.delete();
            png.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        native_find_video_info(path);
        native_filter_logo(logoPath);
        MediaFormat videoMediaFmt = null;
        try {
            mediaExtractor.setDataSource(path);
            //寻找视频流
            for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
                MediaFormat mediaFormat = mediaExtractor.getTrackFormat(i);
                if (mediaFormat.getString(MediaFormat.KEY_MIME).contains("video/")) {
                    videoMediaFmt = mediaFormat;
                    mediaExtractor.selectTrack(i);
                } else if (mediaFormat.getString(MediaFormat.KEY_MIME).contains("audio/")) {
                    readAudioMediaFormat = mediaFormat;
                    audioExtractorSelectIndex = i;
                }
            }
            int colorFmtType = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
            videoMediaFmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFmtType);
            videoMediaFmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, videoMediaFmt.getInteger(MediaFormat.KEY_WIDTH) * videoMediaFmt.getInteger(MediaFormat.KEY_HEIGHT) * 3 / 2);
            decode = MediaCodec.createDecoderByType(videoMediaFmt.getString(MediaFormat.KEY_MIME));
            finalVideoMediaFmt = videoMediaFmt;
            decode.configure(finalVideoMediaFmt, null, null, 0);
            decode.setCallback(mediaCallback);

            mediaMuxer = new MediaMuxer(pngPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            encode = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoMediaFmt.getInteger(MediaFormat.KEY_WIDTH), videoMediaFmt.getInteger(MediaFormat.KEY_HEIGHT));
            // 编码器输入是NV12格式
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoMediaFmt.getInteger(MediaFormat.KEY_WIDTH) * videoMediaFmt.getInteger(MediaFormat.KEY_HEIGHT) * 3);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, videoMediaFmt.getInteger(MediaFormat.KEY_FRAME_RATE));
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
            mediaFormat.setByteBuffer("csd-0", videoMediaFmt.getByteBuffer("csd-0"));
            mediaFormat.setByteBuffer("csd-1", videoMediaFmt.getByteBuffer("csd-1"));
            encode.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            audioMediaExtractor = new MediaExtractor();
            audioMediaExtractor.setDataSource(path);
            audioMediaExtractor.selectTrack(audioExtractorSelectIndex);
            audioFormat = MediaFormat.createAudioFormat(readAudioMediaFormat.getString(MediaFormat.KEY_MIME),
                    44100,
                    readAudioMediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, readAudioMediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) * 3);
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioFormat.setByteBuffer("csd-0", readAudioMediaFormat.getByteBuffer("csd-0"));
            audioBuf = ByteBuffer.allocate(readAudioMediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
            startTime = System.nanoTime();


            decode.start();
            encode.start();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "出现错误  " + e.getMessage(), Toast.LENGTH_LONG);
        }

    }

    private void writeAAC(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                mAudioTrackIndex = mediaMuxer.addTrack(audioFormat);
                mediaMuxer.start();
                isMediaMuxerStatr = true;
                audioBuf.clear();
                int size = audioMediaExtractor.readSampleData(audioBuf,0);
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                info.size = size;
                info.presentationTimeUs = audioMediaExtractor.getSampleTime();
                info.flags = 0;
                mediaMuxer.writeSampleData(mAudioTrackIndex,audioBuf,info);
                while (audioMediaExtractor.advance()) {
                    size = audioMediaExtractor.readSampleData(audioBuf,0);
                    info.size = size;
                    info.presentationTimeUs = audioMediaExtractor.getSampleTime();
                    info.flags = 0;
                    mediaMuxer.writeSampleData(mAudioTrackIndex,audioBuf,info);
                }
                audioEncodeStop = true;
                if (videoEncodeStop && !isStop) {
                    encode.stop();
                    decode.stop();
                    mediaMuxer.stop();
                    isStop = true;
                    endTime = System.nanoTime();
                    duration = (endTime - startTime) / 1000000000; // 计算结果为秒
                    Log.d(TAG, "extime = " + duration + " s");
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
                int sampleSize = mediaExtractor.readSampleData(inputBuffer, 0);
                if (sampleSize < 0) {
                    codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    codec.queueInputBuffer(index, 0, sampleSize, mediaExtractor.getSampleTime(), 0);
                    //读取下一帧
                    mediaExtractor.advance();
                }
            }
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(TAG, "videoEncodeStop mediaMuxer");
                videoEncodeStop = true;
                if (audioEncodeStop && !isStop) {
                    encode.stop();
                    decode.stop();
                    mediaMuxer.stop();
                    isStop = true;
                    endTime = System.nanoTime();
                    duration = (endTime - startTime) / 1000000000; // 计算结果为秒
                    Log.d(TAG, "extime = " + duration + " s");
                }
                return;
            }
            if (index >= 0) {
                Image image = decode.getOutputImage(index);
                if (image == null) {
                    codec.releaseOutputBuffer(index, true);
                    return;
                }
                int w = image.getWidth();
                int h = image.getHeight();
                byte[] i420 = getDataFromImage(image, COLOR_FormatI420);

                byte[] nv12 = native_filter(i420, new INativeCallback() {
                    @Override
                    public void onFrame(byte[] data) {
                    }
                });
                // 将输入数据送入编码器
                int inputBufferIndex = encode.dequeueInputBuffer(100000);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = encode.getInputBuffer(inputBufferIndex);
                    inputBuffer.put(nv12);
                    encode.queueInputBuffer(inputBufferIndex, 0, nv12.length, info.presentationTimeUs, 0);
                }
                // 获取编码后的输出数据
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                int outputBufferIndex = encode.dequeueOutputBuffer(bufferInfo, 100000);
                if (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = encode.getOutputBuffer(outputBufferIndex);
                    //处理编码后的数据
                    if (isMediaMuxerStatr && !videoEncodeStop) {
                        mediaMuxer.writeSampleData(mVideoTrackIndex, outputBuffer, bufferInfo);
                    }
                    encode.releaseOutputBuffer(outputBufferIndex, false);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        long c = mediaExtractor.getSampleTime();
                        long d = finalVideoMediaFmt.getLong(MediaFormat.KEY_DURATION);
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
                mVideoTrackIndex = writeHeadInfo(null, null);
                writeAAC();
            }

//            if (mAudioTrackIndex != -1 && mVideoTrackIndex != -1 && !isMediaMuxerStatr) {
//                mediaMuxer.start();
//                Log.d(TAG, "isMediaMuxerStatr");
//                isMediaMuxerStatr = true;
//            }
        }
    };

    public native String stringFromJNI();

    public native byte[] native_filter(byte[] src, INativeCallback iNativeCallback);

    public native void native_find_video_info(String path);

    public native void native_filter_logo(String path);

    private static Bitmap nv21ToBitmap(byte[] nv21, int width, int height) {
        Bitmap bitmap = null;
        try {
            YuvImage image = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            //输出到对应流
            image.compressToJpeg(new Rect(0, 0, width, height), 100, stream);
            //对应字节流生成bitmap
            bitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }


    //根据image获取yuv值-------------------NEW
    public static byte[] getBytesFromImageAsType(Image image, int type) {
        try {
            //获取源数据，如果是YUV格式的数据planes.length = 3
            //plane[i]里面的实际数据可能存在byte[].length <= capacity (缓冲区总大小)
            final Image.Plane[] planes = image.getPlanes();

            //数据有效宽度，一般的，图片width <= rowStride，这也是导致byte[].length <= capacity的原因
            // 所以我们只取width部分
            int width = image.getWidth();
            int height = image.getHeight();

            //此处用来装填最终的YUV数据，需要1.5倍的图片大小，因为Y U V 比例为 4:1:1 （这里是YUV_420_888）
            byte[] yuvBytes = new byte[width * height * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
            //目标数组的装填到的位置
            int dstIndex = 0;

            //临时存储uv数据的
            byte uBytes[] = new byte[width * height / 4];
            byte vBytes[] = new byte[width * height / 4];
            int uIndex = 0;
            int vIndex = 0;

            int pixelsStride, rowStride;
            for (int i = 0; i < planes.length; i++) {
                pixelsStride = planes[i].getPixelStride();
                rowStride = planes[i].getRowStride();

                ByteBuffer buffer = planes[i].getBuffer();

                //如果pixelsStride==2，一般的Y的buffer长度=640*480，UV的长度=640*480/2-1
                //源数据的索引，y的数据是byte中连续的，u的数据是v向左移以为生成的，两者都是偶数位为有效数据
                byte[] bytes = new byte[buffer.capacity()];
                buffer.get(bytes);

                int srcIndex = 0;
                if (i == 0) {
                    //直接取出来所有Y的有效区域，也可以存储成一个临时的bytes，到下一步再copy
                    for (int j = 0; j < height; j++) {
                        System.arraycopy(bytes, srcIndex, yuvBytes, dstIndex, width);
                        srcIndex += rowStride;
                        dstIndex += width;
                    }
                } else if (i == 1) {
                    //根据pixelsStride取相应的数据
                    for (int j = 0; j < height / 2; j++) {
                        for (int k = 0; k < width / 2; k++) {
                            uBytes[uIndex++] = bytes[srcIndex];
                            srcIndex += pixelsStride;
                        }
                        if (pixelsStride == 2) {
                            srcIndex += rowStride - width;
                        } else if (pixelsStride == 1) {
                            srcIndex += rowStride - width / 2;
                        }
                    }
                } else if (i == 2) {
                    //根据pixelsStride取相应的数据
                    for (int j = 0; j < height / 2; j++) {
                        for (int k = 0; k < width / 2; k++) {
                            vBytes[vIndex++] = bytes[srcIndex];
                            srcIndex += pixelsStride;
                        }
                        if (pixelsStride == 2) {
                            srcIndex += rowStride - width;
                        } else if (pixelsStride == 1) {
                            srcIndex += rowStride - width / 2;
                        }
                    }
                }
            }
            //   image.close();
            //根据要求的结果类型进行填充
            switch (type) {
                case YUV420P:
                    System.arraycopy(uBytes, 0, yuvBytes, dstIndex, uBytes.length);
                    System.arraycopy(vBytes, 0, yuvBytes, dstIndex + uBytes.length, vBytes.length);
                    break;
                case YUV420SP:
                    for (int i = 0; i < vBytes.length; i++) {
                        yuvBytes[dstIndex++] = uBytes[i];
                        yuvBytes[dstIndex++] = vBytes[i];
                    }
                    break;
                case NV21:
                    for (int i = 0; i < vBytes.length; i++) {
                        yuvBytes[dstIndex++] = vBytes[i];
                        yuvBytes[dstIndex++] = uBytes[i];
                    }
                    break;
            }
            return yuvBytes;
        } catch (final Exception e) {
            if (image != null) {
                image.close();
            }
        }
        return null;
    }

    public native void i420ToNv21(byte[] i420, int w, int h, byte[] nv21);

    public native void nv21ToI420(byte[] nv21, int w, int h, byte[] i420);


    void copyAssetsToFilePath() {
        try {
            InputStream is = getAssets().open("trailer.mp4");
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


    // 写入头部信息，并启动 MediaMuxer
    private int writeHeadInfo(ByteBuffer outputBuffer, MediaCodec.BufferInfo bufferInfo) {
        MediaFormat outputFormat = encode.getOutputFormat();
        int videoTrackIndex = mediaMuxer.addTrack(outputFormat);
        return videoTrackIndex;
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