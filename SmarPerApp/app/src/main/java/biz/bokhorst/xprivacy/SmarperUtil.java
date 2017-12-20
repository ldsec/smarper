package biz.bokhorst.xprivacy;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Spinner;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by olejnik on 14/08/15.
 *
 *
 */
public class SmarperUtil {


    public static int NOTIFY_DATA_UPLOAD_ERROR = 8; //KO: For showing notification
    public static int db_schema_version = 19;
    public static int smarper_db_schema_version = 12;
    public static List<Integer> cacheTimes =  Arrays.asList(new Integer(0), new Integer(15), new Integer(30), new Integer(60), new Integer(2*60), new Integer(5 * 60), new Integer(10*60), new Integer(20*60), new Integer(60*60));

    // KO: For storage obfuscation
    public static String picturesPublicDir = null;
    public static String moviesPublicDir = null;
    public static String musicPublicDir = null;
    public static String dcimPublicDir = null;

    public static Uri dcimURI = null;
    public static Uri moviesURI = null;
    public static Uri musicURI = null;
    public static Uri picturesURI = null;

    public static boolean forbiddenPathsInitialized = false;

    //KO: Forbidden paths for storage obfuscation
    public static boolean initializeForbiddenPaths(){
        picturesPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath(); //KO
        moviesPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath(); //KO
        musicPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath(); //KO
        dcimPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath(); //KO

        dcimURI = Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM));
        moviesURI = Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES));
        musicURI = Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC));
        picturesURI = Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES));

        if (picturesPublicDir != null && moviesPublicDir != null && musicPublicDir != null && dcimPublicDir != null && dcimURI != null && moviesURI != null && musicURI != null && picturesURI != null) {
            forbiddenPathsInitialized = true;
            return true;
        }
        else {
            return false;
        }
    }

    public static byte[] bitmapToByteArray(Bitmap image){

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.PNG, 100, baos);
        return baos.toByteArray();

    }

    public static Bitmap byteArrayToBitmap(byte[] imageBytes){

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

    }


    //Executes a Linux shell command
    public String exec(String command) {
        String result = "";
        try {
            java.lang.Process process = Runtime.getRuntime().exec("id");
            //From stack overflow: http://stackoverflow.com/questions/23608005/execute-shell-commands-and-get-output-in-a-textview
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            int read;
            char[] buffer = new char[4096];
            StringBuffer output = new StringBuffer();
            while ((read = reader.read(buffer)) > 0) {
                output.append(buffer, 0, read);
            }
            reader.close();

            // Waits for the command to finish.
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Log.e("Smarper-Error", "Interrupted when executing command " + command);
                e.printStackTrace();
            }

            result = output.toString();
        } catch (IOException e) {
            Log.e("Smarper-Error", "IOException executing command " + command);
            e.printStackTrace();
        }

        return result;
    }

    public static String getUidInfo(int uid){

        String groupName = "";

        try {
            //Dynamically invoke hidden method getpwuid from Os

            Class libcoreClass = Class.forName("libcore.io.Libcore");
            Field f = libcoreClass.getDeclaredField("os");
            Object os = f.get(libcoreClass);
            Method[] methods = os.getClass().getMethods();

            ArrayList<Method> list = new ArrayList<Method>(Arrays.asList(methods));
            //Log.d("Smarper-Uid", "OS object is: " + os.getClass().getName());
            //Log.d("Smarper-Uid", "Methods declared in OS object: " + list.toString());

            Method getpwuid = null;
            for (Method m : list){
                if (m.getName().contains("getpwuid")){
                    getpwuid = m;
                }
            }

            Object structPasswd = getpwuid.invoke(os, uid);
            Field pw_name_field = structPasswd.getClass().getField("pw_name");
            pw_name_field.setAccessible(true);

            groupName =  (String) pw_name_field.get(structPasswd);
            //Log.d("Smarper-Uid", "result is " + groupName);

        } catch (Throwable ex) {
            Log.e("Smarper-Uid", "Error : ");
            ex.printStackTrace();
        }

        return groupName;

    }

    //KO: Check for Xposed framework
    public static boolean isXposedInstalled(Context c){

        PackageInfo pInfo = null;
        try {
            pInfo = c.getPackageManager().getPackageInfo("de.robv.android.xposed.installer", 0);
            if (pInfo != null && pInfo.applicationInfo.enabled) {
                return true;
            }
            else{
                return false;
            }
        } catch (PackageManager.NameNotFoundException e){
            Log.w("Smarper-Warn", "Could not find package with name de.robv.android.xposed.installer");
            return false;
        }

    }

    //KO: Check for rooted device
    /** @author Kevin Kowalewski
     *  https://stackoverflow.com/questions/1101380/determine-if-running-on-a-rooted-device
     * */
    public static boolean isDeviceRooted() {
        return checkRootMethod1() || checkRootMethod2() || checkRootMethod3();
    }

    private static boolean checkRootMethod1() {
        String buildTags = android.os.Build.TAGS;
        return buildTags != null && buildTags.contains("test-keys");
    }

    private static boolean checkRootMethod2() {
        String[] paths = { "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
                "/system/bin/failsafe/su", "/data/local/su" };
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }
        return false;
    }

    private static boolean checkRootMethod3() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[] { "/system/xbin/which", "su" });
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            if (in.readLine() != null) return true;
            return false;
        } catch (Throwable t) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

}
