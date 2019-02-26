package com.mam.lambo.chimera;

import android.app.Activity;
import android.content.Context;
import android.view.Surface;
import android.widget.ImageView;

/**
 * Created by jsvirzi on 11/30/16.
 */

public class ConfigurationParameters implements Cloneable {
    public Context context;
    public String deviceName;
    public String deviceId;
    public int cameraIndex;
    public String outputDirectory;
    public String videoOutputDirectory;
    public String videoOutputFile;
    public Surface displaySurface;
    public boolean useJpeg;
    public boolean jpegActive = false;
    public long deltaJpegTime;
    public long nextJpegTime;
    public byte jpegQuality;
    public boolean useH264;
    public boolean useAutoExposure;
    public boolean useAutoFocus;
    public boolean useOpticalStabilization;
    public boolean nightMode;
    public int imageHeight;
    public int imageWidth;
    public int frameRate;
    public int bitRate;
    public int keyFrameInterval;
    public Activity activity;
    public CustomSurfaceView overlayView;
    public CustomSurfaceView oppositeOverlayView;
    public boolean useEncoder;
    public int keyFramesPerFile;
    public JpegSurface jpegSurface;
    public H264Surface h264Surface;
    public ImageView imageView;
    public boolean isExternal;

    public ConfigurationParameters() {
        /* these are all the default Java initialization values. just to be explicit */
        displaySurface = null;
        overlayView = null;
        oppositeOverlayView = null;
        nightMode = false;
    }

    public ConfigurationParameters(String deviceId, int imageWidth, int imageHeight, int bitRate, int frameRate, int keyFrameInterval, int keyFramesPerFile) {

        /* these are all the default Java initialization values. just to be explicit */
        displaySurface = null;
        overlayView = null;
        oppositeOverlayView = null;

        /* from input parameters */
        this.deviceId = deviceId;
        if (deviceId == SimpleCameraModule.DeviceIdExternal) {
            deviceName = "external";
            isExternal = true;
        } else if (deviceId == SimpleCameraModule.DeviceIdInternal) {
            deviceName = "internal";
            isExternal = false;
        }
        useEncoder = true;
        if (useEncoder) {
            this.bitRate = bitRate;
            this.frameRate = frameRate;
            this.keyFrameInterval = keyFrameInterval;
            this.keyFramesPerFile = keyFramesPerFile;
        }
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;

    }

    public static final ConfigurationParameters DEFAULT_EXTERNAL_CONFIGURATION_HD = new ConfigurationParameters(
        SimpleCameraModule.DeviceIdExternal,
        Common.IMAGE_WIDTH_HD,
        Common.IMAGE_HEIGHT_HD,
        Common.DEFAULT_BIT_RATE_HD,
        Common.DEFAULT_FRAME_RATE_HD,
        Common.DEFAULT_KEY_FRAME_INTERVAL,
        Common.DEFAULT_KEY_FRAMES_PER_FILE);

    public static final ConfigurationParameters DEFAULT_INTERNAL_CONFIGURATION_HD = new ConfigurationParameters(
        SimpleCameraModule.DeviceIdInternal,
        Common.IMAGE_WIDTH_HD,
        Common.IMAGE_HEIGHT_HD,
        Common.DEFAULT_BIT_RATE_HD,
        Common.DEFAULT_FRAME_RATE_HD,
        Common.DEFAULT_KEY_FRAME_INTERVAL,
        Common.DEFAULT_KEY_FRAMES_PER_FILE);

    public static final ConfigurationParameters DEFAULT_EXTERNAL_CONFIGURATION_LD = new ConfigurationParameters(
        SimpleCameraModule.DeviceIdExternal,
        Common.IMAGE_WIDTH_LD,
        Common.IMAGE_HEIGHT_LD,
        Common.DEFAULT_BIT_RATE_LD,
        Common.DEFAULT_FRAME_RATE_LD,
        Common.DEFAULT_KEY_FRAME_INTERVAL,
        Common.DEFAULT_KEY_FRAMES_PER_FILE);

    public static final ConfigurationParameters DEFAULT_INTERNAL_CONFIGURATION_LD = new ConfigurationParameters(
        SimpleCameraModule.DeviceIdInternal,
        Common.IMAGE_WIDTH_LD, Common.IMAGE_HEIGHT_LD,
        Common.DEFAULT_BIT_RATE_LD,
        Common.DEFAULT_FRAME_RATE_LD,
        Common.DEFAULT_KEY_FRAME_INTERVAL,
        Common.DEFAULT_KEY_FRAMES_PER_FILE);

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
