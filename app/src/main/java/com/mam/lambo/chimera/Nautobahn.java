package com.mam.lambo.chimera;

import android.app.Application;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;

//camera.Common;
//import com.nauto.modules.camera.SimpleCameraModule;
//import com.nauto.modules.distraction.AttentionDeficit;
//import com.nauto.modules.facedetection.GoogleFaceDetection;
//import com.nauto.modules.nightvision.Location;
//import com.nauto.modules.nightvision.SunriseSunsetCalculator;
//import com.nauto.modules.obd.ObdModule;
//import com.nauto.modules.server.SimpleServer;
//import com.nauto.modules.utils.SoundPlayer;
//import com.nauto.modules.utils.Utils;

public class Nautobahn extends Application {

    private static final String TAG = "Nautobahn";
    public static Nautobahn instance;
    public Context context;
    public SimpleCameraModule simpleCameraModuleExternal = null;
    public SimpleCameraModule simpleCameraModuleInternal = null;
    public final int imageWidth = 1920;
    public final int imageHeight = 1080;
    public String outputDataDirectory = null;

    public static Nautobahn getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        context = getApplicationContext();
        instance = this;

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd");
        long now = System.currentTimeMillis();
        SimpleDateFormat finalFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
        String dataDirectory = null;

        String sdCardDirectory = null; /* LG Flex5 */
        try {

            File rootDirectory = new File("/storage");
            File[] mountPoints = rootDirectory.listFiles();
            for (File file : mountPoints) {
                if (file.isDirectory() == false) continue;
                String directoryString = file.toString();
                if (directoryString.equals("/storage/emulated")) continue;
                if (directoryString.equals("/storage/self")) continue;
                if (directoryString.equals("/storage/sdcard0")) continue;
//                if (directoryString == "/storage/external_SD") continue;
                String directoryName = String.format("%s/Android/data", directoryString);
                File tmpFile = new File(directoryName);
                if (tmpFile.isDirectory()) {
                    sdCardDirectory = directoryString;
                    break;
                }
            }

//            sdCardDirectory = "/storage/external_SD"; /* LG Flex2 */
//            sdCardDirectory = "/storage/0123-4567"; /* LG Flex5 */
//            sdCardDirectory = "/storage/8A69-7F25";
//            sdCardDirectory = "/storage/8AE8-1DFD";
//            sdCardDirectory = "/storage/emulated/legacy"; /* for M9 */ // /Android/data/com.nauto.nautobahn/files";
//            sdCardDirectory = "/storage/self/primary"; // TODO TODO TODO
//            String sdCardDirectory = "/storage/DFD5-A4F6";
            String externalDir = context.getExternalFilesDir(null).getAbsolutePath();
            String deviceDir = Environment.getExternalStorageDirectory().getAbsolutePath();
            int pos = deviceDir.length();
            outputDataDirectory = sdCardDirectory + externalDir.substring(pos) + "/data";
            String msg = String.format(Common.LOCALE, "[%s] = output data directory", outputDataDirectory);
            Log.d(TAG, msg);
            File dir = new File(outputDataDirectory);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        if (sdCardDirectory == null) {
            Log.e(TAG, "unable to open output directories for application");
            return;
        }
    }

    // TODO shut Nautobahn down properly
//    public void terminate() {
//        int pid = android.os.Process.myPid();
//        android.os.Process.killProcess(pid);
//        finishAffinity();
//    }

    public void shutDown() {

//        if (sensorModule != null) {
//            sensorModule.release();
//            sensorModule = null;
//        }
        if (simpleCameraModuleExternal != null) {
            simpleCameraModuleExternal.destroy();
            simpleCameraModuleExternal = null;
        }
        if (simpleCameraModuleInternal != null) {
            simpleCameraModuleInternal.destroy();
            simpleCameraModuleInternal = null;
        }

        System.exit(0);
    }

}
