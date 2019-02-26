package com.mam.lambo.chimera;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jsvirzi on 1/10/17.
 */

public class H264Surface {
    private static final String TAG = "H264Surface";
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private CaptureRequest captureRequest;
    private Surface surface;
    private Handler handler;
    private HandlerThread thread;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private ConfigurationParameters configurationParameters;
    private final int MaxPayloadSize = 4 * 1024 * 1024;
    private byte[] encodedDataBuffer = new byte[MaxPayloadSize];
    private MediaCodec.Callback mediaCodecCallback;
    private int frameCounter = 0;
    private int framesProcessed = 0;
    private int fileFrameCounter = 0;
    private int fileKeyFrameCounter = 0;
    private int keyFramesPerFile = 0;
    private int fileCounter = 0;
    private MediaFormat mediaFormat;
    private MediaFormat outputMediaFormat = null;
    private MediaCodec encoder;
    private BufferedOutputStream bufferedOutputStreamH264;
    private long encodedSize = 0;
    private byte[] h264Header = null;
    private MediaMuxer mediaMuxer;
    private long[][] framePresentationTime = new long [2][500];
    private long[][] frameTimestamp = new long [2][500];
    private int framePresentationTimePhase = 0;
    private int framePresentationTimeIndex = 0;
    private int framePresentationTimeCount = 0;
    private int videoFileIndex = 0;
    private int keyFramesInCurrentFile;
    private int trackIndex;
    private int bufferSize = 8 * 1024 * 1024;
    private long exposureTime;
    private String currentOutputVideoFile; /* just what it says */
    private String loggingOutputVideoFile; /* this won't change until there's a new file. useful for logging */
    private long currentOutputVideoFileCreationTime;
    private long loggingOutputVideoFileCreationTime;

    public Surface getSurface() {
        return surface;
    }

    public Handler getHandler() {
        return handler;
    }

    public long getExposureTime() {
        return exposureTime;
    }

    public CameraCaptureSession.CaptureCallback getCaptureCallback() {
        return captureCallback;
    }

    public void setCaptureRequest(CaptureRequest inputCaptureRequest) {
        captureRequest = inputCaptureRequest;
    }

    public CaptureRequest getCaptureRequest() {
        return captureRequest;
    }

    private MediaCodec.Callback getMediaCodecCallback() {
        return mediaCodecCallback;
    }

    public H264Surface(ConfigurationParameters inputConfigurationParameters) {
        configurationParameters = inputConfigurationParameters;
        mediaCodecCallback = configurationParameters.useH264 ? mediaCodecCallbackH264 : mediaCodecCallbackMp4;
        String threadName = String.format("Camera_H264_%s", configurationParameters.deviceId);
        thread = new HandlerThread(threadName);
        thread.start();
        handler = new Handler(thread.getLooper());

        /* for handling some background tasks such as writing data to disk */
        threadName = String.format("EncoderBackground%s", configurationParameters.deviceId);
        backgroundThread = new HandlerThread(threadName);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        prepareEncoder(configurationParameters.imageWidth, configurationParameters.imageHeight,
            configurationParameters.bitRate, configurationParameters.frameRate);

        bufferedOutputStreamH264 = null;
    }

    boolean prepareNextFile() {
        if (bufferedOutputStreamH264 != null) {
            try {
                bufferedOutputStreamH264.close();
                bufferedOutputStreamH264 = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        FileOutputStream fileOutputStream = null;
        String filename = String.format(Common.LOCALE, "%s_%04d.h264", configurationParameters.videoOutputFile, fileCounter);
        Log.d(TAG, String.format(Common.LOCALE, "new video file = [%s]", filename));
        ++fileCounter;
        if (filename != null) {
            try {
                fileOutputStream = new FileOutputStream(filename);
            } catch (FileNotFoundException ex) {
                Log.d(TAG, "FileNotFoundException", ex);
                return false;
            }
            bufferedOutputStreamH264 = new BufferedOutputStream(fileOutputStream, bufferSize);
            try {
                bufferedOutputStreamH264.write(h264Header, 0, h264Header.length);
                fileKeyFrameCounter = 0;
                fileFrameCounter = 0;
            } catch (IOException e) {
                e.printStackTrace();;
            }
        }
        return (bufferedOutputStreamH264 != null);
    }

    private MediaCodec.Callback mediaCodecCallbackH264 = new MediaCodec.Callback() {

        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
            Log.d(TAG, "onInputBufferAvailable() called");
        }

        @Override
        public void onOutputBufferAvailable(final MediaCodec codec, final int index, final MediaCodec.BufferInfo info) {
            final long now = System.currentTimeMillis();

            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    String msg;

                    final boolean isConfig = ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0);
                    ByteBuffer encodedData = codec.getOutputBuffer(index);
                    if (isConfig) { /* trap h264 header */
                        Log.d(TAG, "H264 header found. size = " + info.size);
                        if (h264Header == null) {
                            h264Header = new byte[info.size];
                            encodedData.rewind();
                            encodedData.get(h264Header, 0, info.size);
                            prepareNextFile();
                        } else {
                            Log.d(TAG, "MediaCodec.BUFFER_FLAG_CODEC_CONFIG invoked again?");
                        }
                    }

                    if (h264Header == null) {
                        return; /* nothing to do until we get a header */
                    }

                    final boolean isKeyFrame = ((info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0);

                    if (isKeyFrame) {
                        Log.d(TAG, String.format(Common.LOCALE, "key frame %d at frame %d", fileKeyFrameCounter, fileFrameCounter));
                    }

                    if (isKeyFrame) {
                        ++fileKeyFrameCounter;
                        if (fileKeyFrameCounter == configurationParameters.keyFramesPerFile) {
                            prepareNextFile();
                        }
                    }

                    if (bufferedOutputStreamH264 != null) {
                        int payloadSize = info.size;
                        if (payloadSize > MaxPayloadSize) {
                            msg = String.format("payload size = %d bytes. allocated only for %d bytes", payloadSize, MaxPayloadSize);
                            Log.d(TAG, msg);
                            payloadSize = MaxPayloadSize;
                        }
                        try {
                            // byte[] encodedDataBuffer = new byte[info.size];
                            encodedData.rewind();
                            encodedData.get(encodedDataBuffer, info.offset, payloadSize);
                            bufferedOutputStreamH264.write(encodedDataBuffer, 0, payloadSize);
                            ++fileFrameCounter;
                            encodedSize += info.size;
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }

                    }

                    codec.releaseOutputBuffer(index, false);

                    if (isConfig == false) {
                        ++framesProcessed;
                        if ((framesProcessed % 30) == 0) {
                            String logString = "SIZE = " + encodedSize + " FRAMES: " + framesProcessed;
                            Log.d(TAG, logString);
                        }
                    }
                }
            };
            handler.post(runnable);
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException ex) {
            boolean isRecoverable = ex.isRecoverable();
            boolean isTransient = ex.isTransient();
            Log.e(TAG, "MediaCodec.onError() called. Will attempt to restart encoder");
            Log.e(TAG, ex.getDiagnosticInfo());
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            // capture output format for later use in making MP4
            if (outputMediaFormat == null) {
                outputMediaFormat = format;
            }
            int imageWidth = format.getInteger(MediaFormat.KEY_WIDTH);
            int imageHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
            String msg = String.format("MediaCodec.onOutputFormatChanged() called with dims = %dx%d", imageWidth, imageHeight);
            Log.d(TAG, msg);
        }
    };

    private MediaCodec.Callback mediaCodecCallbackMp4 = new MediaCodec.Callback() {

        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
            Log.d(TAG, "onInputBufferAvailable() called");
        }

        @Override
        public void onOutputBufferAvailable(final MediaCodec codec, final int index, final MediaCodec.BufferInfo bufferInfo) {

            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    final long now = System.currentTimeMillis();
                    String msg;

                    boolean isConfigurationFrame = ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0);
                    ByteBuffer encodedData = codec.getOutputBuffer(index);

                    if (outputMediaFormat == null) {
                        Log.d(TAG, "trying to pull a fast one on you");
                        return;
                    }

                    /* trap H264 header. this will be useful for making H.264 videos */
                    if (isConfigurationFrame) {
                        Log.d(TAG, "size of header = " + bufferInfo.size);
                        if (h264Header == null) {
                            h264Header = new byte[bufferInfo.size];
                            encodedData.rewind();
                            encodedData.get(h264Header, 0, bufferInfo.size);
                        } else {
                            Log.d(TAG, "MediaCodec.BUFFER_FLAG_CODEC_CONFIG invoked again?");
                        }
                        return;
                    } else if (h264Header == null) {
                        Log.d(TAG, "H.264 header not encountered yet");
                        return;
                    }

                    boolean isKeyFrame = ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0);
                    boolean startNewFile = isKeyFrame && (keyFramesInCurrentFile == 0);
                    if (startNewFile) {

                        loggingOutputVideoFile = currentOutputVideoFile;
                        loggingOutputVideoFileCreationTime = currentOutputVideoFileCreationTime;
                        framePresentationTimePhase = framePresentationTimePhase ^ 1; /* switch ping-pong phase */
                        framePresentationTimeCount = framePresentationTimeIndex; /* how many we had from previous phase */
                        framePresentationTimeIndex = 0; /* reset */
                        backgroundHandler.post(recordTimestampsRunnable); /* do this in background thread */

                        /* stop previous MediaMuxer */
                        if (mediaMuxer != null) {
                            try {
                                mediaMuxer.stop();
                                mediaMuxer.release();
                            } catch (IllegalStateException ex) {
                                Log.e(TAG, "IllegalStateException caught stopping MediaMuxer", ex);
                                return;
                            } catch (NullPointerException ex) {
                                Log.e(TAG, "NullPointerException caught stopping MediaMuxer", ex);
                                return;
                            }
                        }
                        mediaMuxer = null;

                        /* setup new filename for output */
                        currentOutputVideoFile = String.format(Common.LOCALE, "%s/%s_video_%d.mp4",
                            configurationParameters.videoOutputDirectory, configurationParameters.deviceName, videoFileIndex);
                        currentOutputVideoFileCreationTime = now;
                        ++videoFileIndex;

                        /* setup MediaMuxer for new session */
                        int format = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
                        try {
                            mediaMuxer = new MediaMuxer(currentOutputVideoFile, format);
                        } catch (IllegalStateException ex) {
                            msg = String.format(Common.LOCALE, "MediaMuxer(%s, %d) IllegalArgumentException", currentOutputVideoFile, format);
                            Log.e(TAG, msg, ex);
                            return;
                        } catch (IOException ex) {
                            msg = String.format(Common.LOCALE, "MediaMuxer(%s, %d) IOException", currentOutputVideoFile, format);
                            Log.e(TAG, msg, ex);
                            final int distressLevel = 1;
                            return;
                        }

                        if (mediaMuxer == null) {
                            msg = String.format(Common.LOCALE, "camera %s failed to start MediaMuxer", configurationParameters.deviceName);
                            final int distressLevel = 1;
                            Log.e(TAG, msg);
                            return;
                        } else {
                            msg = String.format(Common.LOCALE, "camera %s started new file [%s]", configurationParameters.deviceName, currentOutputVideoFile);
                            final int whichCamera = (configurationParameters.deviceId == SimpleCameraModule.DeviceIdExternal) ? 0 : 1;
                            Log.e(TAG, msg);
                        }

                        /* setup track */
                        trackIndex = -1;
                        try {
                            trackIndex = mediaMuxer.addTrack(outputMediaFormat); // output media format already validated
                        } catch (IllegalArgumentException ex) {
                            msg = String.format(Common.LOCALE, "IllegalArgumentException caught for MediaCodec.addTrack()");
                            Log.e(TAG, msg, ex);
                            return;
                        } catch (IllegalStateException ex) {
                            msg = String.format(Common.LOCALE, "IllegalStateException caught for MediaCodec.addTrack()");
                            Log.e(TAG, msg, ex);
                            return;
                        }

                        /* launch MediaMuxer and consume current output frame */
                        try {
                            mediaMuxer.start();
                            try {
                                mediaMuxer.writeSampleData(trackIndex, encodedData, bufferInfo);
                            } catch (IllegalArgumentException ex) {
                                msg = String.format(Common.LOCALE, "IllegalArgumentException caught. %d frames", framePresentationTimeIndex);
                                Log.e(TAG, msg, ex);
                                return;
                            } catch (IllegalStateException ex) {
                                msg = String.format(Common.LOCALE, "IllegalStateException caught. %d frames", framePresentationTimeIndex);
                                Log.e(TAG, msg, ex);
                                return;
                            }
                        } catch (IllegalStateException ex) {
                            msg = String.format(Common.LOCALE, "IllegalStateException encountered MediaMuxer.start()");
                            Log.e(TAG, msg, ex);
                            return;
                        }

                    } else { /* not a key frame. should just be regular encoder output */

                        if (mediaMuxer == null) {
                            Log.e(TAG, "MediaMuxer (null)");
                            return;
                        }

                        /* consume data */
                        try {
                            mediaMuxer.writeSampleData(trackIndex, encodedData, bufferInfo);
                        } catch (IllegalArgumentException ex) {
                            msg = String.format(Common.LOCALE, "IllegalArgumentException caught. %d frames", framePresentationTimeIndex);
                            Log.e(TAG, msg, ex);
                            return;
                        } catch (IllegalStateException ex) {
                            msg = String.format(Common.LOCALE, "IllegalStateException caught. %d frames", framePresentationTimeIndex);
                            Log.e(TAG, msg, ex);
                            return;
                        }
                    }

                    if (isKeyFrame) {
                        ++keyFramesInCurrentFile;
                        if (keyFramesInCurrentFile >= configurationParameters.keyFramesPerFile) {
                            keyFramesInCurrentFile = 0;
                        }
                    }

                    /* release buffers to encoder */
                    codec.releaseOutputBuffer(index, false);

                    /* capture presentation time index so we can analyze performance without diving into video file itself */
                    if (framePresentationTimeIndex < framePresentationTime[0].length) {
                        framePresentationTime[framePresentationTimePhase][framePresentationTimeIndex] = bufferInfo.presentationTimeUs;
                        frameTimestamp[framePresentationTimePhase][framePresentationTimeIndex] = now;
                        ++framePresentationTimeIndex;
                    }

                    /* overall statistics */
                    ++framesProcessed;
                    if ((framesProcessed % 30) == 0) {
                        String logString = "SIZE = " + encodedSize + " FRAMES: " + framesProcessed;
                        Log.d(TAG, logString);
                    }
                }
            };
            handler.post(runnable);
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException ex) {
            boolean isRecoverable = ex.isRecoverable();
            boolean isTransient = ex.isTransient();
            Log.e(TAG, "MediaCodec.onError() called. Will attempt to restart encoder");
            Log.e(TAG, ex.getDiagnosticInfo());
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            // capture output format for later use in making MP4
            if (outputMediaFormat == null) {
                outputMediaFormat = format;
            }
            Log.d(TAG, "MediaCodec.onOutputFormatChanged() called");
        }
    };

    public CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            ++frameCounter;
            exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            // String msg = String.format("%s camera exposure time = %d", configurationParameters.deviceName, exposureTime);
            // Log.d(TAG, msg);
        }
    };

    public void prepareEncoder(int width, int height, int bitRate, int frameRate) {

        int colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
        mediaFormat = new MediaFormat();
        mediaFormat.setString(MediaFormat.KEY_MIME, MIME_TYPE);
        mediaFormat.setInteger(MediaFormat.KEY_WIDTH, width);
        mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, height);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, configurationParameters.keyFrameInterval);
        Log.d(TAG, "format: " + mediaFormat);

        String codecName = MediaFormat.MIMETYPE_VIDEO_AVC;
        try {
            encoder = MediaCodec.createEncoderByType(codecName);
            if (encoder == null) {
                Log.wtf(TAG, "unable to initialize media codec");
                encoder = null;
                return;
            }
        } catch (IOException ex) {
            Log.wtf(TAG, "unable to create MediaCodec by name = " + codecName);
            ex.printStackTrace();
            encoder = null;
            return;
        }

        try {
            if (configurationParameters.useH264) {
                encoder.setCallback(mediaCodecCallbackH264);
            } else {
                encoder.setCallback(mediaCodecCallbackMp4);
            }
            encoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface = encoder.createInputSurface();
            encoder.start();
        } catch (MediaCodec.CodecException ex) {
            Log.e(TAG, "MediaCodec exception caught. resetting...");
        } catch (IllegalStateException ex) {
            Log.e(TAG, "mediaCodec in funky state. resetting...");
        }

        keyFramesInCurrentFile = 0;
    }

    public void destroy() {
        if (encoder != null) {
            encoder.stop();
            encoder.release();
            encoder = null;
        }

        if (bufferedOutputStreamH264 != null) {
            try {
                bufferedOutputStreamH264.flush();
                bufferedOutputStreamH264.close();
                bufferedOutputStreamH264 = null;
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        Utils.goodbyeThread(thread);
    }

    private Runnable recordTimestampsRunnable = new Runnable() {
        @Override
        public void run() {
            if (framePresentationTimeCount == 0) {
                return; /* there isn't anything to do */
            }
            int phase = framePresentationTimePhase ^ 1; /* we're acquiring data on other phase */
            List<String> lines = new ArrayList<>(framePresentationTimeCount); /* how many lines we are expecting */
            String line = String.format("vid,%s,filename,%s,%d\n", configurationParameters.deviceId, loggingOutputVideoFile, loggingOutputVideoFileCreationTime);
            lines.add(line);
            for (int i = 0; i < framePresentationTimeCount; i++) {
                line = String.format("vid,%s,timestamp,%d,%d\n", configurationParameters.deviceId, framePresentationTime[phase][i], frameTimestamp[phase][i]);
                lines.add(line);
            }
            DataLogger dataLogger = DataLogger.getInstance();
            if (dataLogger != null) {
                dataLogger.record(lines);
            }
        }
    };
}
