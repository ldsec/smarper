package biz.bokhorst.xprivacy;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.StrictMode;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.GZIPOutputStream;

/**
 * Created by wirving on 10/18/16.
 */
public class Smarper {

    public static int imageCaptureIntentUid = 0; ///KO: most recent intercepted ACTION_IMAGE_CAPTURE, app name
    public static long imageCaptureIntentTime = 0; //KO: the timestamp for this intent, should expire after 10 seconds

    public static Thread bgThread = null; //KO: Background thread for features

    public static final String cTableUsageFull = "usage_full"; //KO: our new table

    private static FeatureSet currentFeatures = new FeatureSet(); //KO: The current set of features
    private static HashMap<Object, ApplicationInfoEx> userApps = new HashMap<Object, ApplicationInfoEx>(); //KO
    private static ReentrantLock featuresLock = new ReentrantLock();  //KO: Lock for accessing current feature set
    private static ReentrantLock userAppsLock = new ReentrantLock(); //KO: Lock for accessing the list of user apps

    private ActivityManager am; //KO: For getting running tasks

    public static ArrayList<Throwable> dbErrors = new ArrayList<Throwable>(); //KO: Collect DB errors
    public static long lastDbErrorTime = -1; //KO: Time of last error
    public boolean error; //KO: We are paused and in error-checking mode, recording of requests disabled
    private int numberOfCorruptBackups; //KO: Number of backed up corrupted databases
    private int cMaxCorruptBackups = 5;

    public static String UploadDataURL = "https://spism.epfl.ch/smarper/uploadData.php";                 //JS

    public static int UploadTimeHour = 21;                    //JS: hour of the periodic upload date
    public static long UploadPeriodinMinutes = 24L * 60L;                           //JS: Period (or frequency) of the main alarm for data upload, in minutes  (set to 24 hours)
    public static long PeriodicCheckingTimeForBackupAlarm = 1L * 60L;        //JS: Period (or frequency) of the back up alarm for checking WIFI connection (and data upload), in minutes (set to 1 hour)
    public static int NumberofTimesforBackupAlarm = 0;                    //JS: current number of times that the back up alarm (in case of no WIFI) went off
    public static int MaxNumberofTimesforBackupAlarm = 3;                   //JS: maximum number of times that the back up alarm (in case of no WIFI) is allowed to go off

    public String debugTagForCacheClear = "smarper_ClearCache";

    private static String dataUploadSecret = "lT5enkK3o92rb3shT3Du"; //KO
    private static String Hash_UserId; //KO

    private static InterruptibilityStatus interruptibilityStatus = new InterruptibilityStatus(); //KO: Keeps track of whether the user is typing, using the camera, or in a call
    //and should not be interrupted

    private static boolean userIdInitialized; //KO: Do we have a unique ID for this user? One-time setup thing.
    private static boolean ready = false; //KO: Ready to intercept and record

    private static long rateLimitIntervalExpiryTimestamp = -1;

    private static ArrayList<Integer> defaultCameraApps = new ArrayList<Integer>(); //KO: Store UIDs of default camera apps in memory, don't record these requests

    public static String getSecret(){ //KO
        return dataUploadSecret;
    }
    public static String getHash(){//KO
        return Hash_UserId;
    }


    //KO: Update the status of the camera (open or not)
    public void updateCameraStatus(boolean newStatus){
        interruptibilityStatus.cameraOpen = newStatus;
        //Log.d("smarper-debug", "Camera open (PrivacyService): " + newStatus);
    }

    //KO: Update the status of typing (typing or not)
    public void updateTypingStatus(boolean newStatus){
        interruptibilityStatus.isTyping = newStatus;
        //Log.d("smarper-debug", "Typing (PrivacyService): " + newStatus);
    }

    //KO: Convenience method for applying the template
    public int[] getInstalledAppUids(SQLiteDatabase dbUsage, ReentrantReadWriteLock mLockUsage){

        int[] uids = null;
        //SQLiteDatabase dbUsage = getDbUsage();
        try {
            mLockUsage.readLock().lock();

            try {
                dbUsage.beginTransaction();
                Cursor cur = dbUsage.query("package_names", new String[]{"uid"}, "uninstalled" + "=?", new String[]{""+0}, null, null, null);

                if (cur != null && cur.getCount() != 0) {
                    cur.moveToFirst();

                    uids = new int[cur.getCount()];

                    for (int i = 0; i < cur.getCount(); i++){
                        uids[i]= cur.getInt(0);
                        cur.moveToNext();
                    }
                    cur.close();
                    dbUsage.setTransactionSuccessful();
                }


            } finally {
                dbUsage.endTransaction();
            }

        } finally {
            mLockUsage.readLock().unlock();

        }

        //Log.d("Smarper-debug", "Installed UIDs are: " + uids);
        return uids;
    }

    //JS: Get Hash-user id from setting
    public String getHashFromDB(SQLiteDatabase db, ReentrantReadWriteLock mLock) throws RemoteException {
        String HASH = "";
        try {
            //enforcePermission(-1);
           // SQLiteDatabase db = getDb();
            mLock.readLock().lock();
            try {
                db.beginTransaction();
                try {
                    Cursor cursor;
                    cursor = db.query("setting", new String[]{"value"}, "name='hash'", null, null, null, null, null);

                    if (cursor == null || cursor.getCount() == 0)
                        Log.d("Smarper-Warn", "no hash value");
                    else
                        try {
                            cursor.moveToFirst();
                            HASH = cursor.getString(0);
                        } finally {
                            cursor.close();
                        }
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            } finally {
                mLock.readLock().unlock();
            }
        } catch (Throwable ex) {
            Util.bug(null, ex);
            Log.e("Smarper-Error", "Error when reading from database: ");
            dbErrors.add(ex);
            checkDbErrors(db);
            throw new RemoteException(ex.toString());
        }
        return HASH;
    }


    //KO: Get value of data upload pointer, datauploadstatus, and datauploadtimestamp
    public long[] getDataUploadStats(SQLiteDatabase db, ReentrantReadWriteLock mLock) throws RemoteException {
        long[] values = new long[3];
        try {
            //enforcePermission(-1);
            //SQLiteDatabase db = getDb();
            mLock.readLock().lock();
            try {
                db.beginTransaction();
                try {
                    Cursor cursor;
                    cursor = db.query("setting", new String[]{"value"}, "name='DataUploadPointer'", null, null, null, null, null);

                    if (cursor == null || cursor.getCount() == 0) {
                        Log.d("Smarper-Warn", "no data upload pointer value");
                        values[0] = 0;
                    }
                    else
                        try {
                            cursor.moveToFirst();
                            values[0] = cursor.getLong(0);
                        } finally {
                            cursor.close();
                        }

                    cursor = db.query("setting", new String[]{"value"}, "name='DataUploadStatus'", null, null, null, null, null);

                    if (cursor == null || cursor.getCount() == 0) {
                        Log.d("Smarper-Warn", "no data upload status code");
                        values[1] = -1;
                    }
                    else
                        try {
                            cursor.moveToFirst();
                            values[1] = cursor.getInt(0);
                        } finally {
                            cursor.close();
                        }

                    cursor = db.query("setting", new String[]{"value"}, "name='DataUploadTimestamp'", null, null, null, null, null);

                    if (cursor == null || cursor.getCount() == 0) {
                        Log.d("Smarper-Warn", "no data upload timestamp");
                        values[2] = new Date().getTime();
                    }
                    else
                        try {
                            cursor.moveToFirst();
                            values[2] = cursor.getLong(0);
                        } finally {
                            cursor.close();
                        }

                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            } finally {
                mLock.readLock().unlock();
            }
        } catch (Throwable ex) {
            Util.bug(null, ex);
            Log.e("Smarper-Error", "Error when reading from database: ");
            dbErrors.add(ex);
            checkDbErrors(db);
            throw new RemoteException(ex.toString());
        }
        return values;
    }

    //KO: Get highest _id from usage database
    public long getMostRecentDecisionId(SQLiteDatabase db, ReentrantReadWriteLock mLockUsage) throws RemoteException {
        long id = 0;
        try {
            //enforcePermission(-1);
            //SQLiteDatabase db = getDbUsage();
            mLockUsage.readLock().lock();
            try {
                db.beginTransaction();
                try {
                    Cursor cursor;
                    cursor = db.query(cTableUsageFull, new String[]{"max(_id)"}, null, null, null, null, null, "1");

                    if (cursor == null || cursor.getCount() == 0)
                        Log.d("Smarper-Warn", "no value");
                    else
                        try {
                            cursor.moveToFirst();
                            id = cursor.getLong(0);
                        } finally {
                            cursor.close();
                        }
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            } finally {
                mLockUsage.readLock().unlock();
            }
        } catch (Throwable ex) {
            Util.bug(null, ex);
            Log.e("Smarper-Error", "Error when reading from database: ");
            dbErrors.add(ex);
            checkDbErrors(db);
            throw new RemoteException(ex.toString());
        }
        return id;
    }


    //KO: Update the stats about the data upload in smarper database after a successful or failed upload
    public void updateDataUploadStats(SQLiteDatabase db, int status, ReentrantReadWriteLock mLock){

        try {
            //SQLiteDatabase db = getDb();

            mLock.readLock().lock();
            try {
                db.beginTransaction();
                try {
                    ContentValues values = new ContentValues();
                    values.put("value", status);
                    db.update("setting", values, "name=?", new String[]{"DataUploadStatus"});
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }

                db.beginTransaction();
                try {
                    ContentValues values = new ContentValues();
                    values.put("value", new Date().getTime());
                    db.update("setting", values, "name=?", new String[]{"DataUploadTimestamp"});
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }

            } finally {
                mLock.readLock().unlock();
            }
        } catch (Throwable ex) {
            Log.e("Smarper-Error", "Error when updating data upload stats: ");
            ex.printStackTrace();
            dbErrors.add(ex);
            lastDbErrorTime = new Date().getTime();
        }

    }

    public static class SendDataToServer extends AsyncTask<Context, Void, Void> {                              //JS

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Context... context) {
            int NumberOfRowsReceived = PrivacyManager.ConnectToServerandSendData(UploadDataURL);
            Log.d("smarper-alarm", "NumberOfRowsReceived " + NumberOfRowsReceived);

            Intent i = new Intent("biz.bokhorst.xprivacy.DATA_UPLOADED");
            i.putExtra("DataUploadTimestamp", new Date().getTime());

            if (NumberOfRowsReceived >= 0) {
                i.putExtra("DataUploadStatus", 0);
                if (NumberofTimesforBackupAlarm == 0)
                    Log.d("smarper-alarm", "Entered to Main Alarm, Connected To WIFI And Connection Was Successful, Do Not Start Back Up Alarm, " + NumberofTimesforBackupAlarm);
                else {
                    Log.d("smarper-alarm", "Entered to Back Up Alarm, Connected To WIFI And Connection Was Successful, Do not Reset Back Up Alarm, " + NumberofTimesforBackupAlarm);
                    NumberofTimesforBackupAlarm = 0;
                }

            } else if (NumberofTimesforBackupAlarm <= MaxNumberofTimesforBackupAlarm) {                 //JS: NumberOfRowsReceived < 0 => error
                i.putExtra("DataUploadStatus", 1);
                if (NumberofTimesforBackupAlarm == 0)
                    Log.e("smarper-alarm", "Entered to Main Alarm, Connected To WIFI But Connection Was Not Successful, Set Back Up Alarm, " + NumberofTimesforBackupAlarm);
                else
                    Log.e("smarper-alarm", "Entered to Back Up Alarm, Connected To WIFI But Connection Was Not Successful, Reset Back Up Alarm, " + NumberofTimesforBackupAlarm);
                Log.e("smarper-alarm", "Error type was " + NumberOfRowsReceived);
                AlarmForSendingData alarm2 = new AlarmForSendingData();                     //JS: Set or Reset Back Up Alarm
                alarm2.SetAlarm(context[0], 1);

            } else {
                i.putExtra("DataUploadStatus", 2);
                Log.e("smarper-alarm", "Entered to Back Up Alarm, Connected To WIFI But Connection Was Not Successful, Cancel Back Up Alarm, " + NumberofTimesforBackupAlarm);
                Log.e("smarper-alarm", "Error type was " + NumberOfRowsReceived);
                NumberofTimesforBackupAlarm = 0;
                notifyContactUs(context[0]); //KO: Notification that an error occurred and participant should contact us
            }

            context[0].sendBroadcast(i);
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
        }
    }

    //JS
    public int ConnectToServerandSendData(String url, SQLiteDatabase dbUsage, ReentrantReadWriteLock mLockUsage, SQLiteDatabase smarperDb, ReentrantReadWriteLock mLock) throws RemoteException {
        ClientDataUpload CDU = new ClientDataUpload();
        //SQLiteDatabase dbUsage = getDbUsage();
        //SQLiteDatabase smarperDb = getDb();
        int NumberOfRowsReceived = CDU.ClientDataUpload(mLockUsage, dbUsage, url, mLock, smarperDb);
        return NumberOfRowsReceived;
    }


    public int ClearCacheOfSelectedApp(int uid, Map<CRestriction,CRestriction> mAskedOnceCache) {
        int removedEntriesCounter = 0;
        List<CRestriction> entriesToBeRemoved = new ArrayList<CRestriction>();

        synchronized (mAskedOnceCache) {
            for (CRestriction crestriction : mAskedOnceCache.keySet())
                if (crestriction.getUid() == uid && !crestriction.isExpired()) {                 //JS: We only remove non expired cached entries
                    entriesToBeRemoved.add(crestriction);
                }
        }

        for (CRestriction crestriction : entriesToBeRemoved) {
            mAskedOnceCache.remove(crestriction);
            Log.d(debugTagForCacheClear, "Cleared the cache of the following recoded decision/permission: " + crestriction.toString());
            removedEntriesCounter++;
        }

        return removedEntriesCounter;
    }


    public void UpdateDBwithNewDecision(int radioId, int idValue, SQLiteDatabase dbUsage, ReentrantReadWriteLock mLockUsage) throws RemoteException { //JS
        try {
           // SQLiteDatabase dbUsage = getDbUsage();

            mLockUsage.readLock().lock();
            try {
                dbUsage.beginTransaction();
                try {
                    ContentValues values = new ContentValues();
                    values.put("decision_modified", radioId);
                    values.put("decision_type", 3);
                    String[] L = {""};
                    L[0] = String.valueOf(idValue);
                    dbUsage.update("usage_compact", values, "_id=?", L);       //JS: Use usage_compact instead of usage_full because usage_full is a view
                    dbUsage.setTransactionSuccessful();
                } finally {
                    dbUsage.endTransaction();
                }
            } finally {
                mLockUsage.readLock().unlock();
            }
        } catch (Throwable ex) {
            Util.bug(null, ex);
            dbErrors.add(ex);
            lastDbErrorTime = new Date().getTime();
            throw new RemoteException(ex.toString());
        }
    }


    public List<String> PermissionInfoOnClick(int idValue, SQLiteDatabase dbUsage, ReentrantReadWriteLock mLockUsage) throws RemoteException { //JS
        List<String> InfoList = new ArrayList<String>();
        try {
            //SQLiteDatabase dbUsage = getDbUsage();

            mLockUsage.readLock().lock();
            try {
                dbUsage.beginTransaction();
                try {
                    Cursor cursor = null;
                    String ID = String.valueOf(idValue);
                    cursor = dbUsage.query(cTableUsageFull, new String[]{"_id", "uid", "gids", "package_name", "app_name", "version", "app_category", "method_category", "method", "parameters", "is_dangerous", "decision", "cached_duration", "decision_type", "decision_elapsed", "decision_time", "decision_modified", "foreground_package_name", "foreground_app_name", "foreground_activity", "screen_interactive", "screen_lock", "ringer_state", "headphones_plugged", "headphones_type", "headphones_mike", "battery_percent", "charging_state", "charge_plug", "conn_type", "dock", "lat", "long", "type_of_place", "provider"}, "_id=" + "?", new String[]{ID}, null, null, "decision_time DESC");

                    if (cursor == null)
                        Util.log(null, Log.WARN, "Database cursor null (usage data)");
                    else
                        try {
                            while (cursor.moveToNext()) {
                                InfoList.add("_id = " + cursor.getInt(0));
                                InfoList.add("uid = " + cursor.getInt(1));
                                InfoList.add("gids = " + cursor.getString(2));
                                InfoList.add("package_name = " + cursor.getString(3));
                                InfoList.add("app_name = " + cursor.getString(4));
                                InfoList.add("version = " + cursor.getString(5));
                                InfoList.add("app_category = " + cursor.getString(6));
                                InfoList.add("method_category = " + cursor.getString(7));
                                InfoList.add("method = " + cursor.getString(8));
                                InfoList.add("parameters = " + cursor.getString(9));
                                InfoList.add("is_dangerous = " + cursor.getInt(10));
                                InfoList.add("Decision = " + cursor.getInt(11));
                                InfoList.add("cached_duration = " + cursor.getInt(12));
                                InfoList.add("decision_type = " + cursor.getInt(13));
                                InfoList.add("decision_elapsed = " + cursor.getLong(14));
                                InfoList.add("decision_time = " + cursor.getLong(15));
                                InfoList.add("decision_modified = " + cursor.getInt(16));
                                InfoList.add("foreground_package_name = " + cursor.getString(17));
                                InfoList.add("foreground_app_name = " + cursor.getString(18));
                                InfoList.add("foreground_activity = " + cursor.getString(19));
                                InfoList.add("screen_interactive = " + +cursor.getInt(20));
                                InfoList.add("screen_lock = " + +cursor.getInt(21));
                                InfoList.add("ringer_state = " + +cursor.getInt(22));
                                InfoList.add("headphones_plugged = " + +cursor.getInt(23));
                                InfoList.add("headphones_type = " + cursor.getString(24));
                                InfoList.add("headphones_mike = " + cursor.getInt(25));
                                InfoList.add("battery_percent" + cursor.getInt(26));
                                InfoList.add("charging_state = " + cursor.getInt(27));
                                InfoList.add("charge_plug = " + cursor.getInt(28));
                                InfoList.add("conn_type = " + cursor.getInt(29));
                                InfoList.add("dock = " + cursor.getInt(30));
                                InfoList.add("lat = " + cursor.getFloat(31));
                                InfoList.add("long = " + cursor.getFloat(32));
                                InfoList.add("type_of_place = " + cursor.getInt(33));
                                InfoList.add("provider = " + cursor.getString(34));
                            }
                        } finally {
                            cursor.close();
                        }

                    dbUsage.setTransactionSuccessful();
                } finally {
                    dbUsage.endTransaction();
                }
            } finally {
                mLockUsage.readLock().unlock();
            }
        } catch (Throwable ex) {
            Util.bug(null, ex);
            dbErrors.add(ex);
            lastDbErrorTime = new Date().getTime();
            throw new RemoteException(ex.toString());
        }
        return InfoList;
    }

    //KO: Get initial features before receiving an update from broadcast receivers
    private static void getInitialState(LocationManager manager, LocationMonitor m, Context c) {
        //Network
        long token = Binder.clearCallingIdentity();
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        KeyguardManager km = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        Binder.restoreCallingIdentity(token);

        //Dock
        //Docked? If so, where?
        IntentFilter dockFilter = new IntentFilter(Intent.ACTION_DOCK_EVENT);

        //If there has never been a change in dock state then dockStatus will be null
        Intent dockStatus = c.registerReceiver(null, dockFilter);


        //Battery
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = c.registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float batteryPercent = (level / (float) scale) * 100;
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

        //Location
        Location l = manager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);

        //Audio
        int ringer = am.getRingerMode();
        boolean headset_plugged = am.isWiredHeadsetOn();

        //Screen
        boolean screenLocked = km.isKeyguardLocked();
        boolean screenInteractive;
        if (Build.VERSION.SDK_INT < 20)
            screenInteractive = pm.isScreenOn();
        else
            screenInteractive = pm.isInteractive();

        featuresLock.lock();
        try {

            //Network
            if (!isConnected) {
                currentFeatures.connectionType = -1;
                //Log.d("Smarper-Features", "Not connected");
            } else {
                currentFeatures.connectionType = activeNetwork.getType();
                //Log.d("Smarper-Features", "Active network type is " + activeNetwork.getTypeName());
            }

            //Dock
            if (dockStatus != null) {
                currentFeatures.dockingState = dockStatus.getIntExtra(Intent.EXTRA_DOCK_STATE, -1);
            } else {
                currentFeatures.dockingState = 0; //same as Undocked
                //Log.d("Smarper", "Can't get dock status");
            }


            //Battery
            currentFeatures.batteryPercent = batteryPercent;
            currentFeatures.isCharging = status;
            currentFeatures.chargePlug = chargePlug;

            //Audio
            currentFeatures.ringer = ringer;
            currentFeatures.headsetPlugged = headset_plugged;

            //Screen
            currentFeatures.screenOn = screenInteractive;
            currentFeatures.screenLocked = screenLocked;


            currentFeatures.initialized = true;

            if (l != null)
                currentFeatures.currentLocation = new Location(l);

            //Log.d("Smarper-Features", "Getting initial state. Battery: " + batteryPercent + ", chargingState: " + status + ", chargePlug: " + chargePlug);

        } finally {
            featuresLock.unlock();
        }


    }

    //KO: Query the db to see if user id field is populated
    private boolean checkUserId(SQLiteDatabase db, ReentrantReadWriteLock mLock) {

        Log.d("Smarper-Debug", "Checking if user-id exists...");
        boolean populated = false;
       // SQLiteDatabase db = getDb();

        mLock.readLock().lock();
        try {
            db.beginTransaction();
            try {

                Cursor cursor;
                cursor = db.query("setting", new String[]{"name", "value"}, "name=?", new String[]{"hash"}, null, null, null, null);

                if (cursor.getCount() != 0) {
                    cursor.moveToFirst();
                    Hash_UserId = cursor.getString(1);
                    Log.d("Smarper-Debug", "The user id is: " + Hash_UserId);
                    populated = true;
                    userIdInitialized = true;
                }

                cursor.close(); //Here we can add more code to verify that the hash of the IMEI is the same

                db.setTransactionSuccessful();

            } catch (Exception e) {
                Log.e("Smarper-Error", "Error when querying for user id: ");
                dbErrors.add(e);
                lastDbErrorTime = new Date().getTime();
                e.printStackTrace();
            } finally {
                db.endTransaction();
            }

        } finally {
            mLock.readLock().unlock();
        }

        Log.d("Smarper-Debug", "User id exists: " + populated);
        return populated;
    }


    //KO: Record the hash of the IMEI in the db table
    private void initUserId(Context c, SQLiteDatabase db, ReentrantReadWriteLock mLock) {

        //Make a query first to check if the table is empty

        if (!checkUserId(db, mLock)) {
            Log.d("Smarper-Debug", "Creating user-id");
            String hash = ""; //Hash will go here

            TelephonyManager tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
            String IMEI = tm.getDeviceId(); //Get IMEI

            //Hash the IMEI with a random string
            SecureRandom sr = new SecureRandom();
            byte[] random = new byte[8];
            sr.nextBytes(random);

            try {
                hash = Util.sha1WithSalt(IMEI, random.toString()).substring(0,8); //Generate the hash


            } catch (Exception e) {
                Log.e("Smarper-Error", "Error creating user id: ");
                e.printStackTrace();
            }

            if (!hash.isEmpty()) { //If we didn't fail at the previous step

                //SQLiteDatabase db = getDb();

                mLock.readLock().lock();
                try {
                    db.beginTransaction();
                    try {

                        ContentValues hash_row = new ContentValues();
                        hash_row.put("uid", ""+0);
                        hash_row.put("name", "hash");
                        hash_row.put("value", hash);

                        db.insertOrThrow("setting", null, hash_row);

                        Hash_UserId = hash;
                        Log.d("Smarper-Debug", "User id initialized to " + Hash_UserId);
                        userIdInitialized = true;
                        db.setTransactionSuccessful();

                    } catch (Exception e) {
                        Log.e("Smarper-Error", "Error when writing user id: ");
                        userIdInitialized = false;
                        dbErrors.add(e);
                        lastDbErrorTime = new Date().getTime();
                        e.printStackTrace();
                    } finally {
                        db.endTransaction();
                    }

                } finally {
                    mLock.readLock().unlock();

                }


            }


        }

    }

    //KO
    public class DataUploadedReceiver extends BroadcastReceiver {

        SQLiteDatabase db = null;
        ReentrantReadWriteLock lock = null;

        public DataUploadedReceiver(SQLiteDatabase dbUsage, ReentrantReadWriteLock mLockUsage){
            lock = mLockUsage;
            db = dbUsage;
        }

        @Override
        public void onReceive(Context context, Intent intent) {

            int DataUploadStatus = intent.getIntExtra("DataUploadStatus", -2);

            if (DataUploadStatus == -2){
                //KO: Shouldn't happen, but just in case

            } else{
                updateDataUploadStats(db, DataUploadStatus, lock);
            }
        }
    }


    //KO: set up the background features thread
    public void initialize(final Context c, final SQLiteDatabase db, final ReentrantReadWriteLock mLock, final SQLiteDatabase dbUsage, final ReentrantReadWriteLock mLockUsage) {

        // Background thread
        bgThread = new Thread(new Runnable() {

            @Override
            public void run() {

                try {
                    Looper.prepare();
                    Handler bgHandler = new Handler();
                    //Context c = getContext();
                    //getDbUsage();
                    //getDb();

                    //Now that we have a context object, we can do things
                    //User ID
                    if (!userIdInitialized)
                        initUserId(c, db, mLock);

                    Intent i = new Intent("biz.bokhorst.xprivacy.USER_ID_CREATED");
                    c.sendBroadcast(i);

                    //List of user apps
                    setUserAppList(c, dbUsage, mLockUsage);

                    //Register receivers, then loop
                    Log.d("Smarper-Bgthread", "Registering receivers");

                    //KO: Data uploaded receiver
                    DataUploadedReceiver dur = new DataUploadedReceiver(dbUsage, mLockUsage);
                    IntentFilter durFilter = new IntentFilter("biz.bokhorst.xprivacy.DATA_UPLOADED");
                    c.registerReceiver(dur, durFilter, null, bgHandler);

                    BatteryMonitor bm = new BatteryMonitor();
                    ConnectionMonitor cm = new ConnectionMonitor();
                    DockMonitor dm = new DockMonitor();
                    ScreenMonitor sm = new ScreenMonitor();
                    AudioMonitor am = new AudioMonitor();
                    PackageMonitor pm = new PackageMonitor(dbUsage, mLockUsage);

                    //Register receivers
                    //Change to installed applications
                    IntentFilter packageFilter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
                    packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
                    //packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED); //don't think we need this one?
                    packageFilter.addDataScheme("package");
                    c.registerReceiver(pm, packageFilter, null, bgHandler);

                    //Battery
                    IntentFilter powerFilter = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
                    powerFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
                    powerFilter.addAction(Intent.ACTION_BATTERY_CHANGED);

                    c.registerReceiver(bm, powerFilter, null, bgHandler);

                    //Network
                    IntentFilter connectionFilter = new IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION);
                    c.registerReceiver(cm, connectionFilter, null, bgHandler);

                    //Dock
                    IntentFilter dockFilter = new IntentFilter(Intent.ACTION_DOCK_EVENT);
                    c.registerReceiver(dm, dockFilter, null, bgHandler);

                    //Screen status
                    IntentFilter screenFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
                    screenFilter.addAction(Intent.ACTION_SCREEN_ON);
                    screenFilter.addAction(Intent.ACTION_USER_PRESENT);
                    c.registerReceiver(sm, screenFilter, null, bgHandler);

                    //Location
                    LocationManager manager = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
                    LocationMonitor m = new LocationMonitor();
                    manager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 1000, 5, m, Looper.myLooper());

                    //Audio
                    IntentFilter audioFilter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
                    audioFilter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
                    c.registerReceiver(am, audioFilter);

                    getInitialState(manager, m, c);

                    AlarmForSendingData alarm = new AlarmForSendingData();                     //JS: Define new alarm
                    alarm.SetAlarm(c, 0);                               //JS: Initialize and set alarm

                    //ArrayList<Integer> systemCameraUids = ApplicationInfoEx.getDefaultCameraAppUids(c);
                    //Log.d("Smarper-Camera", "There are " + systemCameraUids.size() + " system camera apps, uids are: " + systemCameraUids.toString());

                    ready = true;

                    Looper.loop();
                } catch (Throwable ex) {
                    Log.e("Smarper-Error", "Exception in bg-thread :" + ex);
                    ex.printStackTrace();
                }

            }

        });
        bgThread.start();

    }


    //KO: For keeping list of apps up to date
    private class PackageMonitor extends BroadcastReceiver {

        SQLiteDatabase dbUsage = null;
        ReentrantReadWriteLock mLockUsage = null;

        public PackageMonitor(SQLiteDatabase db, ReentrantReadWriteLock lock){
            dbUsage = db;
            mLockUsage = lock;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            //Log.d("Smarper-Debug", "Got " + intent.getAction() + ", setting app list");

            String action = intent.getAction();

            switch (action){
                case (Intent.ACTION_PACKAGE_ADDED):
                    setUserAppList(context, dbUsage, mLockUsage);
                    break;

                case (Intent.ACTION_PACKAGE_REMOVED):
                    int removedUid = intent.getIntExtra(Intent.EXTRA_UID, -1); //For this one, the removed UID is always included as an extra
                    removeApp(context, removedUid, dbUsage, mLockUsage);
                    break;


            }

        }

    }


    //KO: for monitoring battery state
    private static class BatteryMonitor extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {


            float batteryPercent;
            int status;
            int chargePlug;
            featuresLock.lock();
            try {
                //Update state in currentFeatures
                if (intent.hasExtra(BatteryManager.EXTRA_LEVEL) && intent.hasExtra(BatteryManager.EXTRA_SCALE)) {
                    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                    batteryPercent = (level / (float) scale) * 100;
                    currentFeatures.batteryPercent = batteryPercent;

                }

                if (intent.hasExtra(BatteryManager.EXTRA_STATUS)) {
                    status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    currentFeatures.isCharging = status;
                }

                if (intent.hasExtra(BatteryManager.EXTRA_PLUGGED)) {
                    chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                    currentFeatures.chargePlug = chargePlug;
                }

                //Log.d("Smarper-Features", "Got battery update:" + batteryPercent + " " + status + " " + chargePlug);

            } finally {
                featuresLock.unlock();
            }

        }

    }

    //KO: For monitoring ringer and headphone state
    private static class AudioMonitor extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            //Ringer mode changed
            if (intent.hasExtra(AudioManager.EXTRA_RINGER_MODE)) {

                int ringer = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, 2); //2 is loud mode


                featuresLock.lock();
                try {

                    currentFeatures.ringer = ringer;

                } finally {
                    featuresLock.unlock();
                }


            }

            //Headphones state changed
            else if (intent.hasExtra("state")) {
                String type = "none";

                int state = (intent.getIntExtra("state", 0));

                if (intent.getStringExtra("name") != null) {
                    type = intent.getStringExtra("name");
                }

                int mike = intent.getIntExtra("microphone", 0);

                featuresLock.lock();
                try {
                    currentFeatures.headsetPlugged = ((state == 0) ? false : true);
                    currentFeatures.headsetType = type;
                    currentFeatures.headsetHasMike = ((mike == 0) ? false : true);
                } finally {
                    featuresLock.unlock();
                }


            }


        }

    }

    //KO: For monitoring network state
    private static class ConnectionMonitor extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            //Log.d("Smarper-Features", "Got connectivity update");
            long token = Binder.clearCallingIdentity();
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

            featuresLock.lock();
            try {
                if (!isConnected) {
                    currentFeatures.connectionType = -1;
                    //Log.d("Smarper-Features", "Not connected");
                } else {
                    currentFeatures.connectionType = activeNetwork.getType();
                    //Log.d("Smarper-Features", "Active network type is " + activeNetwork.getTypeName());
                }
            } finally {
                featuresLock.unlock();
            }

            Binder.restoreCallingIdentity(token);

        }
    }

    //KO: for monitoring dock state
    private static class DockMonitor extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            //Log.d("Smarper-Features", "Got dock update");
            featuresLock.lock();
            try {
                currentFeatures.dockingState = intent.getIntExtra(Intent.EXTRA_DOCK_STATE, -1);
            } finally {
                featuresLock.unlock();
            }
        }
    }

    //KO: for screen interactive/non-interactive state
    private static class ScreenMonitor extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);

            //Log.d("Smarper-Features", "Got screen update");
            featuresLock.lock();
            try {
                if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                    currentFeatures.screenOn = true;
                    //Log.d("Smarper-Debug", "Screen became interactive");
                } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {

                    currentFeatures.screenOn = false;
                    //Log.d("Smarper-Debug", "Screen became not interactive");

                    //Update locked state:
                    currentFeatures.screenLocked = km.isKeyguardLocked();
                    //Log.d("Smarper-Debug", "Screen is locked: " + currentFeatures.screenLocked);

                } else { //ACTION_USER_PRESENT, i.e. screen is unlocked
                    currentFeatures.screenLocked = false;
                    // Log.d("Smarper-Debug", "Screen unlocking");
                }


            } finally {
                featuresLock.unlock();
            }
        }
    }


    private static class LocationMonitor implements LocationListener {

        @Override
        public void onLocationChanged(Location location) {

            //Log.d("Smarper-Features", "Got location update: " + location.getLatitude() + ", " + location.getLongitude());
            //KO: This is the important one

            featuresLock.lock();
            try {
                currentFeatures.currentLocation = new Location(location); //don't want pass by reference to happen here
            } finally {
                featuresLock.unlock();
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            //status of a LocationProvider has changed
            //Log.d("Smarper-Features", "Provider " + provider + " has changed status to " + status);
        }

        @Override
        public void onProviderEnabled(String provider) {
            //Provider enabled by the user
            //Log.d("Smarper-Features", "Provider " + provider + " was enabled by user");
        }

        @Override
        public void onProviderDisabled(String provider) {
            //Provider disabled by the user
            //Log.d("Smarper-Features", "Provider " + provider + " was disabled by user");
        }


    }
    //KO: Store user applications in a list
    public void setUserAppList(Context context, SQLiteDatabase dbUsage, ReentrantReadWriteLock mLockUsage) {

        try {
            userAppsLock.lock();
            try {
                userApps.clear();
                defaultCameraApps.clear();

                //int[] uids, String[] package_names, String[] app_names, String[] versions
                List<ApplicationInfoEx> appList = ApplicationInfoEx.getXApplicationListQuiet(context);
                defaultCameraApps = ApplicationInfoEx.getDefaultCameraAppUids(context);
                for (ApplicationInfoEx app : appList) {
                    if (!app.isSystem()) {

                        //Info about this app
                        int uid = app.getUid();
                        String packageName = TextUtils.join(", ", app.getPackageName());
                        String appName = TextUtils.join(", ", app.getApplicationName());

                        userApps.put(packageName, app);
                        userApps.put(uid, app);

                        int package_id = queryForPackageIdAndUpdate(dbUsage, packageName, app, appName, mLockUsage, context);

                    }
                }

                //Log.d("Smarper-Debug", "set user app list, userAppList contains: " + userApps.toString());

            } catch (Exception e) {
                Log.e("Smarper-Error", "Exception when setting user app list: ");
                e.printStackTrace();
            }

        } finally {
            userAppsLock.unlock();
        }
    }

    //KO: Remove an uninstalled app from the user app list
    public void removeApp(Context c, int removedUid, SQLiteDatabase dbUsage, ReentrantReadWriteLock mLockUsage){

        try {
            userAppsLock.lock();
            try {

                ApplicationInfoEx removedApp = userApps.get(removedUid);
                if (removedApp != null) {
                    String packageName = TextUtils.join(", ", removedApp.getPackageName());
                    //userApps.remove(removedUid);
                    //userApps.remove(packageName);
                    setUninstallFlag(packageName, dbUsage, mLockUsage);
                }
                else {
                    Log.d("Smarper-Warn", "Want to remove " + removedUid + " but it's not in the user app list!");
                }

            } catch (Exception e) {

                Log.e("Smarper-Error", "Exception in removeApp:" );
                e.printStackTrace();
            }

        } finally {
            userAppsLock.unlock();

        }

        setUserAppList(c, dbUsage, mLockUsage);


    }
    //KO
    private void getRunningTaskInfo(Context c) {

        long token = Binder.clearCallingIdentity();
        if (am == null)
            am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE); //KO: Initialize activity manager for getting tasks

        PackageManager pm = c.getPackageManager();

        if (Build.VERSION.SDK_INT < 21) { //KO: We can use getRunningTasks


            List<ActivityManager.RunningTaskInfo> taskList = am.getRunningTasks(1);
            featuresLock.lock();
            try {
                if (taskList.size() == 0 || taskList == null) { //KO: Just in case there are no recent tasks
                    currentFeatures.topApp_package = "";
                    currentFeatures.topApp_name = "";
                    currentFeatures.topActivity = "";
                } else {
                    currentFeatures.topApp_package = taskList.get(0).baseActivity.getPackageName();
                    //Must ask packagemanager
                    currentFeatures.topApp_name = pm.getApplicationInfo(currentFeatures.topApp_package, PackageManager.GET_META_DATA).loadLabel(pm).toString();
                }

                currentFeatures.topActivity = taskList.get(0).topActivity.getShortClassName();


            } catch (Exception e) {
                Log.e("Smarper-Error", "Error getting top app: ");
                e.printStackTrace();


            } finally {
                featuresLock.unlock();
            }

        } else { //KO: Use the code from stack overflow
            // http://stackoverflow.com/questions/24625936/getrunningtasks-doesnt-work-in-android-l
            final int START_TASK_TO_FRONT = 2;
            ActivityManager.RunningAppProcessInfo currentInfo = null;
            Field field = null;
            try {
                field = ActivityManager.RunningAppProcessInfo.class.getDeclaredField("processState");
            } catch (Exception e) {
                e.printStackTrace();
                return; //KO: Something went wrong, can't get the info
            }

            List<ActivityManager.RunningAppProcessInfo> appList = am.getRunningAppProcesses();
            for (ActivityManager.RunningAppProcessInfo app : appList) {
                if (app.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    Integer state = null;
                    try {
                        state = field.getInt(app);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return; //KO: Something went wrong, can't get the info
                    }
                    if (state != null && state == START_TASK_TO_FRONT) {
                        currentInfo = app;
                        break;
                    }
                }
            }

            featuresLock.lock();
            if (currentInfo != null) {
                currentFeatures.topApp_package = currentInfo.processName;
            }
            else {
                currentFeatures.topApp_package = "";
            }
            currentFeatures.topApp_name = "";
            currentFeatures.topActivity = "";
            //Can't get top Activity in the process...
            //TODO: look for a fix? if there even is one

            featuresLock.unlock();


        }

        Binder.restoreCallingIdentity(token);

    }

    //KO
    private int queryForId(SQLiteDatabase dbUsage, ReentrantReadWriteLock mLockUsage, String tableName, String idColumnName, String nameColumnName, String parameter) {

        int result = -1;


        try {
            mLockUsage.readLock().lock();

            try {
                dbUsage.beginTransaction();
                Cursor cur = dbUsage.query(tableName, new String[]{idColumnName}, nameColumnName + "=?", new String[]{parameter}, null, null, null);

                if (cur.getCount() != 0) {
                    cur.moveToFirst();
                    result = cur.getInt(0);

                }

                cur.close();

                dbUsage.setTransactionSuccessful();

            } finally {
                dbUsage.endTransaction();
            }

        } finally {
            mLockUsage.readLock().unlock();

        }


        //Failure
        return result;
    }

    //KO: For tables which should be updated as we see new stuff (headphones types)
    private int queryForIdAndUpdate(SQLiteDatabase dbUsage, String tableName, String idColumnName, String nameColumnName, String parameter, ReentrantReadWriteLock mLockUsage) {

        int result = -1;

        try {
            mLockUsage.readLock().lock();
            Cursor cur = dbUsage.query(tableName, new String[]{idColumnName}, nameColumnName + "=?", new String[]{parameter}, null, null, null);
            mLockUsage.readLock().unlock();

            if (cur.getCount() != 0) {
                cur.moveToFirst();
                result = cur.getInt(0);

            } else {
                //Add the unknown item
                ContentValues c = new ContentValues();
                c.put(nameColumnName, parameter);
                mLockUsage.writeLock().lock();
                dbUsage.beginTransaction();
                try {
                    result = (int) dbUsage.insert(tableName, null, c); // Get the rowid of the inserted row. Assume we never delete from this table so _id = _rowid_
                    mLockUsage.writeLock().unlock();

                    if (result == -1) {
                        Log.e("Smarper-Error", "Error updating ID!");
                    } else {
                        dbUsage.setTransactionSuccessful();
                    }
                } finally {
                    dbUsage.endTransaction();
                }

                //Log.d("Smarper-debug", "Adding item " + parameter + ", of type" + nameColumnName + " to " + tableName + " table");

            }


            cur.close();
        } catch (Exception e){
            Log.e("Smarper-Error", "Error updating ID!");
            e.printStackTrace();
        }


        return result;
    }

    //KO: Query and update the gids table
    private int queryForGidAndUpdate(int[] gids, SQLiteDatabase dbUsage, ReentrantReadWriteLock mLockUsage) {

        //Sort them, so we don't have duplicate entries with different orderings
        ArrayList<Integer> gidsList = new ArrayList();

        for (int gid : gids){
            gidsList.add(new Integer(gid));
        }

        Collections.sort(gidsList);

        String gids_str = "";
        for (Integer gid : gidsList){ //There's got to be a better way to do this
            gids_str += gid.intValue();

            if (gidsList.lastIndexOf(gid) != gidsList.size() - 1){
                gids_str += ", ";
            }
        }

        //SQLiteDatabase dbUsage = getDbUsage();
        int rowid = 0;

        try {
            mLockUsage.readLock().lock();
            Cursor cur = dbUsage.query("group_ids", new String[]{"gid_set_id"}, "group_ids=?", new String[]{"" + gids_str}, null, null, null);
            mLockUsage.readLock().unlock();

            if (cur.getCount() != 0) {
                cur.moveToFirst();
                rowid = cur.getInt(0);

            } else {
                //Add the unknown item
                String groupNames = "";
                for (Integer gid : gidsList) {
                    groupNames += SmarperUtil.getUidInfo(gid);

                    if (gidsList.lastIndexOf(gid) != gidsList.size() - 1) {
                        groupNames += ", ";
                    }
                }


                ContentValues c = new ContentValues();
                c.put("group_ids", gids_str);
                c.put("group_names", groupNames);

                mLockUsage.writeLock().lock();
                dbUsage.beginTransaction();
                try {
                    rowid = (int) dbUsage.insert("group_ids", null, c); //shouldn't be over int max value... there won't be that many entries
                    mLockUsage.writeLock().unlock();
                    if (rowid != -1) {
                        dbUsage.setTransactionSuccessful();
                    } else {
                        Log.e("Smarper-Error", "Error when inserting gids into gids table!");
                    }

                } finally {
                    dbUsage.endTransaction();
                }
            }

            cur.close();


        } catch (Exception e) {
            Log.e("Smarper-Error", "Error when fetching gids ID: ");
            e.printStackTrace();
        }

        return rowid;
    }

    //KO
    private void setUninstallFlag(String packageName, SQLiteDatabase dbUsage, ReentrantReadWriteLock mLockUsage){

        //SQLiteDatabase dbUsage = getDbUsage();

        try {
            mLockUsage.writeLock().lock();
            try {
                dbUsage.beginTransaction();

                ContentValues c = new ContentValues();
                c.put("uninstalled", 1);
                c.put("uid", ""); //Clear the UID also, since it can get re-assigned to a different app

                int rows = dbUsage.update("package_names", c, "package_name=?", new String[]{packageName});
                Log.d("Smarper-Db", "Set uninstall flag for " + packageName + ", affected " + rows + " rows");

                dbUsage.setTransactionSuccessful();
            } finally {
                dbUsage.endTransaction();
            }
        } finally {

            mLockUsage.writeLock().unlock();
        }


    }


    //KO: For tables which store package names and icons
    private int queryForPackageIdAndUpdate(SQLiteDatabase dbUsage, String packageName, ApplicationInfoEx thisApp, String appName, ReentrantReadWriteLock mLockUsage, Context context) {

        int id = -1;

        try {
            mLockUsage.readLock().lock();
            Cursor cur = dbUsage.query("package_names", new String[]{"package_id", "uninstalled", "icon"}, "package_name" + "=?", new String[]{packageName}, null, null, null);
            mLockUsage.readLock().unlock();

            //Prepare the entry
            ContentValues cv = new ContentValues();
            cv.put("uninstalled", 0);
            cv.put("uid", thisApp.getUid());

            Bitmap appIcon = thisApp.getIconBitmap(context);

            //Make sure it's not huge
            Bitmap appIconScaled = Bitmap.createScaledBitmap(appIcon, 48, 48, false);
            byte[] appIconBytes = SmarperUtil.bitmapToByteArray(appIconScaled);

            cv.put("icon", appIconBytes);

            // If the entry already exists
            if (cur.getCount() != 0) {
                cur.moveToFirst();
                id = cur.getInt(0);

                if (cur.getInt(1) == 1) //Was re-installed, update the flag and UID and icon
                {
                    mLockUsage.writeLock().lock();
                    dbUsage.beginTransaction();
                    try {
                        //Log.d("Smarper-debug", "Updating package " + packageName + ", UID:" + thisApp.getUid() + " in package_names table");
                        int rowsUpdated = dbUsage.update("package_names", cv, "package_id=?", new String[]{"" + id});
                        mLockUsage.writeLock().unlock();

                        if (rowsUpdated == 0) {
                            Log.e("Smarper-Error", "Tried to update " + packageName + ", " + thisApp.getUid() + " in package_names table but failed!");
                        } else {
                            dbUsage.setTransactionSuccessful();
                        }
                    } finally {
                        dbUsage.endTransaction();
                    }

                }


            } else {
                cv.put("package_name", packageName);
                mLockUsage.writeLock().lock();
                dbUsage.beginTransaction();
                try {
                    id = (int) dbUsage.insert("package_names", null, cv); // Get the rowid of the inserted row. Assume we never delete from this table so _id = _rowid_
                    mLockUsage.writeLock().unlock();
                    //Log.d("Smarper-debug", "Adding package " + packageName + ", UID:" + thisApp.getUid() + " to package_names table");

                    if (id == -1) {
                        Log.e("Smarper-Error", "Error adding " + packageName + ", " + thisApp.getUid() + " to package_names table!");
                    } else {
                        dbUsage.setTransactionSuccessful();
                    }
                } finally {
                    dbUsage.endTransaction();
                }
            }

            cur.close();

        } catch (Exception e){
            Log.e("Smarper-Error", "Error when trying to update package_names table!");
            e.printStackTrace();
        }


        return id;
    }


    //KO: Only record things for monitored apps
   /* private boolean shouldRecord(PRestriction mresult){
        try {
            return getSettingBool(mresult.uid, PrivacyManager.cSettingOnDemand, false);
        } catch (RemoteException e){
            return false;
        }
    }*/

    //KO: Write to our table and record features
    private void storeFullUsageData(final Hook hook, String secret, final PRestriction mresult, final FeatureSet features, final PrivacyService.OnDemandResult oResult, final ApplicationInfoEx thisApp, final ApplicationInfoEx foregroundApp,
                                    final String version, final int[] gids, final String appName, final ReentrantReadWriteLock mLockUsage, final Context context, ExecutorService mExecutor, final SQLiteDatabase dbUsage)  throws RemoteException {

        // Check if enabled
       /* final int userId = Util.getUserId(mresult.uid);
        if (getSettingBool(userId, PrivacyManager.cSettingUsage, true)) {
            // Check secret
            boolean allowed = true;
            if (Util.getAppId(Binder.getCallingUid()) != getXUid()) {
                if (mSecret == null || !mSecret.equals(secret)) {
                    allowed = false;
                    Util.log(null, Log.WARN, "Invalid secret restriction=" + mresult);
                }
            } */


            //if (allowed) {

                mExecutor.execute(new Runnable() {
                    public void run() {
                        try {
                            if (XActivityManagerService.canWriteUsageData()) {
                                //SQLiteDatabase dbUsage = getDbUsage();
                                boolean dbWriteError = false;
                                boolean dbReadError = false;

                                if (dbUsage == null) {
                                    Log.e("Smarper-Error", "Could not get usage.db, entry not recorded!");
                                    return;
                                }



                                //Get package_id, method_category_id, method_id, headphones_id
                                //If package_id or headphones_id not in db, record a new one

                                int method_category_id = -1;
                                int method_id = -1;
                                int foreground_package_id = -1;
                                int package_id = -1;
                                int headphones_id = -1;
                                int gids_id = -1;


                                try {
                                    method_category_id = queryForId(dbUsage, mLockUsage, "method_categories", "method_category_id", "method_category", mresult.restrictionName);
                                    method_id = queryForId(dbUsage, mLockUsage, "method_names", "method_id", "method", mresult.methodName);

                                    //Check if the foreground package is a user app
                                    if (foregroundApp != null) {
                                        foreground_package_id = queryForPackageIdAndUpdate(dbUsage, features.topApp_package, foregroundApp, features.topApp_name, mLockUsage, context);
                                    }

                                    else { //System app, can't record icon, will have no foregroundApp object
                                        foreground_package_id = queryForIdAndUpdate(dbUsage, "package_names", "package_id", "package_name", features.topApp_package, mLockUsage);
                                    }

                                    package_id = queryForPackageIdAndUpdate(dbUsage, mresult.package_name, thisApp, appName, mLockUsage, context);

                                    headphones_id = 1; //Code for no headphones, default
                                    if (features.headsetType != null) {
                                        headphones_id = queryForIdAndUpdate(dbUsage, "headphones_types", "headphones_id", "headphones_type", features.headsetType, mLockUsage);
                                    }

                                    //Get names of gids
                                    if (gids != null){
                                        gids_id = queryForGidAndUpdate(gids, dbUsage, mLockUsage);
                                    }
                                    else {
                                        gids_id = 1; //The first entry in this table corresponds to "no gids"
                                    }



                                } catch (Exception e) {
                                    Log.e("Smarper-Error", "Exception when querying db for ids of repetitive data types:");
                                    e.printStackTrace();
                                    dbErrors.add(e);
                                    lastDbErrorTime = new Date().getTime();
                                    return;
                                    //checkDbErrors();

                                }



                                //Only if we didn't fail at previous step
                                if (package_id != -1 && method_id != -1 && method_category_id != -1 && foreground_package_id != -1 && gids_id != -1) {

                                    try {
                                        mLockUsage.writeLock().lock();

                                        try {
                                            dbUsage.beginTransaction();
                                            ContentValues values = new ContentValues();

                                            //UID
                                            values.put("uid", mresult.uid);


                                            values.put("package_id", package_id); //KO
                                            values.put("app_name", appName);



                                            values.put("gid_set_id", gids_id); //KO
                                            values.put("version", version); //KO
                                            //values.put("app_category", ""); //KO: this field will be populated post-data-collection


                                            //DECISION INFO
                                            // all PRestriction
                                            values.put("method_category_id", method_category_id);
                                            values.put("method_id", method_id);
                                            //values.put("params", restriction.params);
                                            values.put("parameters", mresult.extra);
                                            values.put("is_dangerous", hook.isDangerous()); //KO: Is dangerous?
                                            values.put("decision", mresult.restrictState);
                                            values.put("cached_duration", mresult.cached_duration);


                                            values.put("decision_type", mresult.decision_type);
                                            values.put("decision_elapsed", ((oResult == null) ? 0 : oResult.elapsed));
                                            values.put("decision_time", mresult.time); //KO: Use timestamp we got in XHook
                                            //values.put("decision_modified", ""); //KO: will be populated post-data-collection

                                            //App category
                                            values.put("app_category", LcaSmarperPolicy.getCategory(mresult.package_name));

                                            //CONTEXT
                                            if (features.initialized != false) {
                                                values.put("foreground_package_id", foreground_package_id); //KO: App currently in foreground
                                                values.put("foreground_app_name", features.topApp_name); //KO: app name, currently in foreground, from pm
                                                values.put("foreground_activity", features.topActivity); //KO: top activity in the foreground app
                                                values.put("screen_interactive", features.screenOn); //KO Screen interactive or not
                                                values.put("screen_lock", features.screenLocked); //KO: Screen locked?
                                                values.put("ringer_state", features.ringer); //KO: Loud, silent, or vibrate mode
                                                values.put("headphones_plugged", features.headsetPlugged); //KO: headset plugged in?
                                                values.put("headphones_id", headphones_id); //KO: type of headphones. No headphones = type 1
                                                values.put("headphones_mike", features.headsetHasMike); //KO: headphones has mike? boolean
                                                values.put("battery_percent", features.batteryPercent);
                                                values.put("charging_state", features.isCharging);
                                                values.put("charge_plug", features.chargePlug);
                                                values.put("conn_type", features.connectionType);
                                                values.put("dock", features.dockingState);
                                                values.put("type_of_place", ""+((oResult == null) ? "" : oResult.semanticLocationChoice));

                                                //Location
                                                if (features.currentLocation != null) {
                                                    values.put("lat", features.currentLocation.getLatitude());
                                                    values.put("long", features.currentLocation.getLongitude());
                                                    values.put("provider_id", features.providerStringToInt());
                                                } else {
                                                    values.put("provider_id", 1); // IDP: provider_id can't be NULL, otherwise usage_full view fails
                                                }

                                            }

                                            long row_id = dbUsage.insertWithOnConflict("usage_compact", null, values, SQLiteDatabase.CONFLICT_ABORT); //KO: Use our new table here

                                            if (row_id == -1) { // Error occurred, row not inserted
                                                Log.e("Smarper-Error", "Error inserting row into db: " + mresult.uid + " " + mresult.restrictionName + " " + mresult.methodName + " " + mresult.decision_type);

                                            } else {

                                                dbUsage.setTransactionSuccessful();
                                            }

                                        } catch (SQLiteException ex) {
                                            Log.e("Smarper-Error", "SQLite Exception when attempting to write to db: " + ex.toString());
                                            ex.printStackTrace();
                                            dbErrors.add(ex);
                                            lastDbErrorTime = new Date().getTime();
                                            // dbWriteError = true;

                                        } catch (Throwable ex) {
                                            Log.e("Smarper-Error", "Other type of exception when attempting to write to db: " + ex);
                                            ex.printStackTrace();
                                            dbErrors.add(ex);
                                            lastDbErrorTime = new Date().getTime();
                                            //dbWriteError = true;
                                        } finally {
                                            dbUsage.endTransaction();
                                            //Check db errors here
                                            //if (dbWriteError)
                                            //    checkDbErrors();
                                        }

                                    }finally{
                                        mLockUsage.writeLock().unlock();

                                    }

                                    //KO: make sure that it's actually doing something
                                    Log.d("usage-db", "sent write to db: " + mresult.uid + " " + mresult.restrictionName + " " + mresult.methodName + " " + mresult.decision_type);

                                } else {
                                    Log.e("Smarper-Error", "Could not fetch IDs for repetitive data types to insert! " + " package_id: " + package_id + ", method_category_id: " + method_category_id + " , method_id: " + method_id + " foreground_package_id: " + foreground_package_id + " gids_id:" + gids_id + " headphones_id: " + headphones_id);
                                }




                            } else {
                                Log.w("Smarper-Warn", "Activity manager doesn't allow writing usage data right now, " + mresult.restrictionName + " " + mresult.methodName + " not recorded");
                            }

                        } catch (Exception e) {
                            Log.e("Smarper-Error", "Exception when trying to write usage data: ");
                            e.printStackTrace();
                            //checkDbErrors();
                        }


                    }


                });



            }







    //KO: Our version of this method, with time parameter
    public PRestriction getRestrictionTime(final PRestriction restriction, boolean usage, String secret, long time, Context c, Hook hook, PRestriction mresult, Semaphore mOndemandSemaphore,
                                           ReentrantReadWriteLock mLockUsage, boolean settingOnDemandSystem, int xuid, boolean onDemandUserId, boolean onDemandUid, String version, boolean shouldRecord,
                                           ExecutorService mExecutor, SQLiteDatabase dbUsage, boolean isAMLocked, Handler mHandler, Map<CRestriction,CRestriction> mRestrictionCache,
                                           Map<CRestriction,CRestriction> mAskedOnceCache, Map<CSetting,CSetting> mSettingCache, String[] exExtra)
            throws RemoteException {


        //Log.d("Smarper-Restrictions", "getRestrictionTime with restriction=" + restriction.toString();

            //KO: Record the last image capture intent that happened and the timestamp
            if (restriction.methodName.equals("android.media.action.IMAGE_CAPTURE") && restriction.uid > 1000) { //KO: Ignore requests from Android system
                //Log.d("Smarper-Debug", "Recorded most recent intent from app: " + restriction.uid + ", at " + time);
                imageCaptureIntentUid = restriction.uid;
                imageCaptureIntentTime = time;
            }

            // Ask to restrict
            PrivacyService.OnDemandResult oResult = null;

            if (ready && !error) {
                try {
                    userAppsLock.lock();
                    boolean isUserApp = (userApps.containsKey(restriction.uid) || defaultCameraApps.contains(restriction.uid));
                    userAppsLock.unlock();

                    if (isUserApp && (hook!= null)) { //KO: We only care about user apps *and system camera*
                        Log.d("Smarper-Restrictions", "=======================================");

                        //Context c = getContext();

                        if (c != null)
                            getRunningTaskInfo(c);

                        //KO: Get current set of features here
                        FeatureSet features;
                        featuresLock.lock();
                        try {
                            features = new FeatureSet(currentFeatures);
                        } finally {
                            featuresLock.unlock();
                        }

                        //KO: Get app info
                        userAppsLock.lock();
                        ApplicationInfoEx thisApp = userApps.get(mresult.uid);
                        ApplicationInfoEx foregroundApp = userApps.get(features.topApp_package);
                        userAppsLock.unlock();

                        //KO: Should never happen, but just in case we don't have the app in our list
                        String appName = "";
                        String app_version = "";
                        int[] gids = null;

                        if (thisApp != null) {
                            mresult.package_name = TextUtils.join(", ", thisApp.getPackageName());
                            appName = TextUtils.join(", ", thisApp.getApplicationName());
                            app_version = thisApp.getPackageVersionName(mresult.package_name);
                            gids = thisApp.getGids(mresult.package_name);
                        } else {
                            Log.w("Smarper-Warn", "Could not find app info for app uid " + mresult.uid);
                        }


                        mresult.time = time; //KO: Set the time we got in XHook when we intercepted

                        //KO: Here we determine if we should prompt
                        if (!mresult.asked && usage && shouldOnDemand(hook, restriction, mresult, settingOnDemandSystem, xuid, onDemandUserId, onDemandUid, version, c)) {
                            Log.d("Smarper-Restrictions", mresult.toString() + " // Should on demand, attempting to show dialog or check if cached");


                            mOndemandSemaphore.acquireUninterruptibly(); //KO: This part must be synchronized
                            try {
                                oResult = new PrivacyService.OnDemandResult(); //KO: Results from the dialog will go here

                                if (!checkCache(hook, restriction, mresult, oResult, mRestrictionCache, mAskedOnceCache, mSettingCache,exExtra)) { //KO: If not in the cache, try to show the dialog
                                    onDemandDialog(features, hook, restriction, mresult, oResult, c, isAMLocked, dbUsage, mLockUsage, mHandler, mAskedOnceCache);

                                    if (mresult.decision_type == 7){ //KO: Special case for user was busy and had no previous decision
                                        //We statically allow
                                        mresult.restrictState = 0;
                                    }

                                    else { //KO: On demand
                                        //KO: Set decision type appropriately below
                                        mresult.asked = oResult.dialogSucceeded;
                                        mresult.cached_duration = oResult.cached_duration;
                                        if (oResult.timedOut) {
                                            mresult.decision_type = 2;
                                            mresult.restrictState = 0; //KO: Allow timeouts, for consistency
                                        } else if (oResult.dialogSucceeded && !oResult.timedOut)
                                            mresult.decision_type = 1;
                                    }

                                } else {
                                    //KO: Was cached
                                    mresult.asked = oResult.dialogSucceeded; //KO: is this really needed? Not sure.
                                    mresult.cached_duration = oResult.cached_duration;
                                    mresult.decision_type = 4;

                                }


                            } catch (Exception e) {
                                Log.e("Smarper-Error", mresult.toString() + " // Error when trying to show dialog, or checking cache: " + e.getMessage());
                                e.printStackTrace();
                                mresult.decision_type = 5;
                                mresult.restrictState = 1;
                                //KO: Should have shown the dialog, but couldn't (e.g. screen locked)
                                //KO: On demand to static obfuscate
                                //KO: TODO: Can attempt to take previous decision here as well, if one exists. If there is none, do static obfuscate

                            } finally { //KO: Synchronized section is over
                                mOndemandSemaphore.release();
                            }


                        } else { //KO: We should not on demand, i.e. use static policy
                            mresult.decision_type = 0;
                            mresult.restrictState = ((mresult.restricted) ? 2 : 0);
                            Log.d("Smarper-Restrictions", mresult.toString() + " // Should not on demand");
                        }


                        //KO: Record the entry and features in our new table, *except for system camera*
                        //if (!defaultCameraApps.contains(mresult.uid)) {
                        if (shouldRecord){
                            Log.d("Smarper-Restrictions", mresult.toString() + " // Sending write to database");
                            storeFullUsageData(hook, secret, mresult, features, oResult, thisApp, foregroundApp, app_version, gids, appName, mLockUsage, c, mExecutor, dbUsage);
                        }

                    }

                } catch (Exception e) {
                    Log.e("Smarper-Error", mresult.toString() + " // Failure checking if should on demand, quitting, result will be (restrictState): " + mresult.restrictState + " restricted: " + mresult.restricted); //KO:
                    e.printStackTrace();
                }

            } else {

                if (!ready && !error)
                    Log.d("Smarper-Warn", mresult.toString() + " // intercepted request but not ready!");
                else if (ready && error)
                    Log.d("Smarper-Warn", mresult.toString() + " // In error-recovery mode, not recording");
                else
                    Log.d("Smarper-Warn", mresult.toString() + " // not recording ");
            }


        return mresult;
    }

    //KO: Modified heavily
    //KO: This whole method is now "we should on demand, so now we're going to try"
    public PrivacyService.OnDemandResult onDemandDialog(FeatureSet f, final Hook hook, final PRestriction restriction, final PRestriction result, final PrivacyService.OnDemandResult oResult,
                                                        final Context context, final boolean isAMLocked, SQLiteDatabase dbUsage, ReentrantReadWriteLock mLockUsage,final Handler mHandler, final Map<CRestriction,CRestriction> mAskedOnceCache) throws Exception {


        long token = 0;
        try {
            token = Binder.clearCallingIdentity();

            //KO: if (!checkCache(hook, restriction, result, oResult)) {

            // Check if activity manager agrees
            if (!XActivityManagerService.canOnDemand()) {
                Log.e("Smarper-Error", result.toString() + " // Could not display on demand dialog -- Activity Manager Service"); //ERROR
                throw new Exception("On demand dialog: Activity Manager Service canOnDemand() failed");
            }

            // Check if activity manager locked
            if (isAMLocked) {
                Util.log(null, Log.WARN, "On demand locked " + restriction);
                Log.e("Smarper-Error", result.toString() + " // Activity manager is locked, not asking"); //ERROR
                throw new Exception("On demand dialog: Actitivy manager is locked");
            }

            //Get context and app info here
           // final Context context = getContext();
            if (context == null) { //This shouldn't happen
                Log.e("Smarper-Error", result.toString() + " // Could not display on demand dialog -- couldn't get context");
                throw new Exception("On demand dialog: context is null");
            }

            final ApplicationInfoEx appInfo = new ApplicationInfoEx(context, restriction.uid);


            //KO: At this point we have passed all the checks and can actually prompt. Make sure the user isn't going to be interrupted
            boolean isForeground = (f.topApp_package.equals(result.package_name)); //KO: Is the app in the foreground?
            if (doNotInterruptNow(context, isForeground)){
                if (takePreviousDecision(f,result, isForeground, dbUsage, mLockUsage))
                {
                    return oResult;
                }

                else {

                    Log.d("Smarper-Debug", "Interruptibility: No previous decision, will statically allow");
                    result.decision_type = 7; //KO: We statically allowed because there was no previous decision and user was busy
                    return oResult;
                }
            }

            //KO: Check rate-limits
            if (rateLimitExceeded(isForeground)) {
                if (takePreviousDecision(f, result, isForeground, dbUsage, mLockUsage)) {
                    return oResult;
                } else {
                    //KO: No previous decision, do nothing, continue to try to prompt
                    Log.d("Smarper-Debug", "Rate-limit: No previous decision, will prompt anyway");
                }

            }


            //KO: Getting ready to show the dialog
            //KO: semaphore was here originally

            // Check if activity manager still agrees
            if (!XActivityManagerService.canOnDemand()) {
                Log.e("Smarper-Error", result.toString() + " // Could not display on demand dialog -- Activity Manager Service"); //ERROR
                throw new Exception("On demand dialog: activity manager service canOnDemand() check failed");

            }

            // Check if activity manager locked now
            if (isAMLocked) {
                Log.e("Smarper-Error", result.toString() + " // Activity manager is still locked, not asking");
                //Throw an exception here too
                throw new Exception("On demand dialog: activity manager is locked");

            }

            //On demanding
            final PrivacyService.OnDemandDialogHolder holder = new PrivacyService.OnDemandDialogHolder();

            // Build dialog parameters
            final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.type = WindowManager.LayoutParams.TYPE_PHONE;
            params.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            params.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE;
            params.dimAmount = 0.85f;
            params.width = WindowManager.LayoutParams.WRAP_CONTENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.format = PixelFormat.TRANSLUCENT;
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
            params.gravity = Gravity.CENTER;

            // Get window manager
            final WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

            // Show dialog
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Build dialog
                        holder.dialog = getOnDemandView(restriction, hook, appInfo, result, context, holder,
                                oResult, mAskedOnceCache);

                        // Handle reset button
                        ((Button) holder.dialog.findViewById(R.id.btnReset))
                                .setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        ((ProgressBar) holder.dialog.findViewById(R.id.pbProgress))
                                                .setProgress(PrivacyService.cMaxOnDemandDialog * 20);
                                        holder.reset = true;
                                        holder.latch.countDown();
                                    }
                                });

                        // Make dialog visible
                        wm.addView(holder.dialog, params);

                        final AtomicBoolean wasAMLocked = new AtomicBoolean(false); //KO: For getting a value out of the Runnable
                        // Update progress bar
                        Runnable runProgress = new Runnable() {

                            @Override
                            public void run() {
                                if (holder.dialog != null && holder.dialog.isShown()) {
                                    // Update progress bar
                                    ProgressBar progressBar = (ProgressBar) holder.dialog
                                            .findViewById(R.id.pbProgress);
                                    if (progressBar.getProgress() > 0) {
                                        progressBar.incrementProgressBy(-1);
                                        mHandler.postDelayed(this, 50);
                                    }

                                    // Check if activity manager locked
                                    if (isAMLocked) {
                                        Util.log(null, Log.WARN, "On demand dialog locked " + restriction);
                                        wasAMLocked.set(true); //KO: Can't throw an exception here so we set this flag and check
                                        Log.e("Smarper-Error", "Couldn't on demand, AM was locked!");
                                        //(holder.dialog.findViewById(R.id.btnObfuscate)).callOnClick(); //KO: This should be treated as an error
                                    }

                                }
                            }

                        };

                        mHandler.postDelayed(runProgress, 50);

                        // Enabled buttons after one second
                                /*boolean repeat = (SystemClock.elapsedRealtime() - mOnDemandLastAnswer < 1000);
                                mHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (holder.dialog != null && holder.dialog.isShown()) {
                                            holder.dialog.findViewById(R.id.btnAllow).setEnabled(true);
                                            //holder.dialog.findViewById(R.id.btnDontKnow).setEnabled(true); //KO: Remove this button
                                            holder.dialog.findViewById(R.id.btnObfuscate).setEnabled(true); //KO: Add our buttons
                                            holder.dialog.findViewById(R.id.btnDeny).setEnabled(true);
                                        }
                                    }
                                }, repeat ? 0 : 1000); */ //KO: Enable buttons after semantic location is input by the user, see method enableOnDemandButtons and the click listeners for the semantic buttons

                        if (wasAMLocked.get()) //KO: check this here, to make sure thread had enough time to be executed by handler
                            throw new Exception("Couldn't on demand, AM was locked!");

                    } catch (PackageManager.NameNotFoundException ex) {
                        Log.e("Smarper-Error", "NameNotFound exception in onDemandDialog" + ex.toString()); //ERROR, assume dialog wasn't shown
                        ex.printStackTrace();
                    } catch (Throwable ex) {
                        Log.e("Smarper-Error", "Other exception in onDemandDialog" + ex.toString());
                        ex.printStackTrace();
                    }
                }
            });

            // Wait for choice, reset or timeout
            long start = new Date().getTime(); //KO: Start timing
            do {

                holder.reset = false;
                boolean choice = holder.latch.await(PrivacyService.cMaxOnDemandDialog, TimeUnit.SECONDS);
                if (holder.reset) {
                    holder.latch = new CountDownLatch(1);
                    Log.d("Smarper-Restrictions", "On demand reset " + restriction);
                } else if (choice) {
                    oResult.ondemand = true;
                    oResult.dialogSucceeded = true;
                }
                else {
                    Log.d("Smarper-Restrictions", "On demand timeout " + restriction);
                    oResult.dialogSucceeded = true;
                    oResult.timedOut = true; //KO: Log timeouts
                }
            } while (holder.reset);
            PrivacyService.mOnDemandLastAnswer = SystemClock.elapsedRealtime();
            oResult.elapsed = new Date().getTime() - start; //KO: Record elapsed time

            // Dismiss dialog
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    View dialog = holder.dialog;
                    if (dialog != null)
                        wm.removeView(dialog);
                }
            });

            //KO: Update rate limit interval info only if this was a request from a background app and it was not a timeout
            if (!oResult.timedOut && !isForeground)
                updateRateLimits();


        } finally {
            Binder.restoreCallingIdentity(token);
        }

        return oResult;
    }


    @SuppressLint("InflateParams")
    private View getOnDemandView(final PRestriction restriction, final Hook hook, ApplicationInfoEx appInfo,
                                 final PRestriction result, Context context, final PrivacyService.OnDemandDialogHolder holder, final PrivacyService.OnDemandResult oResult, final Map<CRestriction,CRestriction> mAskedOnceCache)
            throws PackageManager.NameNotFoundException, RemoteException {
        // Get resources
        String self = PrivacyService.class.getPackage().getName();
        Resources resources = context.getPackageManager().getResourcesForApplication(self);

        // Reference views
        final View view = LayoutInflater.from(context.createPackageContext(self, 0)).inflate(R.layout.ondemand, null);
        ImageView ivAppIcon = (ImageView) view.findViewById(R.id.ivAppIcon);
        TextView tvAppName = (TextView) view.findViewById(R.id.tvAppName);
        TextView tvCategory = (TextView) view.findViewById(R.id.tvCategory);
        TextView tvFunction = (TextView) view.findViewById(R.id.tvFunction);
        TextView tvParameters = (TextView) view.findViewById(R.id.tvParameters);
        TableRow rowParameters = (TableRow) view.findViewById(R.id.rowParameters);
        TextView tvInfoCategory = (TextView) view.findViewById(R.id.tvInfoCategory);
        final CheckBox cbCategory = (CheckBox) view.findViewById(R.id.cbCategory);
        final Spinner spOnce = (Spinner) view.findViewById(R.id.spOnce);
        final CheckBox cbWhitelist = (CheckBox) view.findViewById(R.id.cbWhitelist);
        final CheckBox cbWhitelistExtra1 = (CheckBox) view.findViewById(R.id.cbWhitelistExtra1);
        final CheckBox cbWhitelistExtra2 = (CheckBox) view.findViewById(R.id.cbWhitelistExtra2);
        final CheckBox cbWhitelistExtra3 = (CheckBox) view.findViewById(R.id.cbWhitelistExtra3);
        ProgressBar mProgress = (ProgressBar) view.findViewById(R.id.pbProgress);
        Button btnDeny = (Button) view.findViewById(R.id.btnDeny);
        Button btnObfuscate = (Button) view.findViewById(R.id.btnObfuscate); //KO: Obfuscate button
        Button btnAllow = (Button) view.findViewById(R.id.btnAllow);

        final Button btnHome = (ToggleButton) view.findViewById(R.id.btnHome); //KO: Home button
        final Button btnWork = (ToggleButton) view.findViewById(R.id.btnWork); //KO: Work button
        final Button btnTransit = (ToggleButton) view.findViewById(R.id.btnTransit); //KO: Transit button
        final Button btnOther = (ToggleButton) view.findViewById(R.id.btnOther); //KO: Other button
        final RadioGroup semanticLocationButtons = (RadioGroup) view.findViewById(R.id.semanticLocsGroup);

        final Button btnHelp = (Button) view.findViewById(R.id.btnHelp); //KO: Help button
        final TextView tvHelp = (TextView) view.findViewById(R.id.tvHelpOnClick); //KO: TextView with help text

        final int userId = Util.getUserId(Process.myUid());
        boolean category = true; //getSettingBool(userId, PrivacyManager.cSettingODCategory, true); //KO: Always true
        final boolean once = true; //getSettingBool(userId, PrivacyManager.cSettingODOnce, false);  //KO: Here we can enable ask once by default

        // Set values
        if ((hook != null && hook.isDangerous()) || appInfo.isSystem())
            view.setBackgroundResource(R.color.color_dangerous_dialog);
        else
            view.setBackgroundResource(R.color.material_blue_grey_800); //KO: Dialog color


        // Application information
        ivAppIcon.setImageDrawable(appInfo.getIcon(context));
        tvAppName.setText(TextUtils.join(", ", appInfo.getApplicationName()));

        // Restriction information
        int catId = resources.getIdentifier("restrict_" + restriction.restrictionName, "string", self);
        tvCategory.setText(resources.getString(catId));
        tvFunction.setText(restriction.methodName);
        if (restriction.extra == null)
            rowParameters.setVisibility(View.GONE);
        else
            tvParameters.setText(restriction.extra);


        // Help
        int helpId = resources.getIdentifier("restrict_help_" + restriction.restrictionName, "string", self);
        tvInfoCategory.setText(resources.getString(helpId));

        // Category
        cbCategory.setChecked(category);

        // Once
        /*int osel = Integer
                .parseInt(getSetting(new PSetting(userId, "", PrivacyManager.cSettingODOnceDuration, "0")).value);
        spOnce.setSelection(osel);*/ //KO

        // Whitelisting
        /*if (hook != null && hook.whitelist() != null && restriction.extra != null) {
            cbWhitelist.setText(resources.getString(R.string.title_whitelist, restriction.extra));
            cbWhitelist.setVisibility(View.VISIBLE);
            String[] xextra = getXExtra(restriction, hook);
            if (xextra.length > 0) {
                cbWhitelistExtra1.setText(resources.getString(R.string.title_whitelist, xextra[0]));
                cbWhitelistExtra1.setVisibility(View.VISIBLE);
            }
            if (xextra.length > 1) {
                cbWhitelistExtra2.setText(resources.getString(R.string.title_whitelist, xextra[1]));
                cbWhitelistExtra2.setVisibility(View.VISIBLE);
            }
            if (xextra.length > 2) {
                cbWhitelistExtra3.setText(resources.getString(R.string.title_whitelist, xextra[2]));
                cbWhitelistExtra3.setVisibility(View.VISIBLE);
            }
        }*/


        // Category, once and whitelist exclude each other
        cbCategory.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    cbWhitelist.setChecked(false);
                    cbWhitelistExtra1.setChecked(false);
                    cbWhitelistExtra2.setChecked(false);
                    cbWhitelistExtra3.setChecked(false);
                }
            }
        });

        cbWhitelist.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    cbCategory.setChecked(false);
                    //cbOnce.setChecked(false); //KO
                    cbWhitelistExtra1.setChecked(false);
                    cbWhitelistExtra2.setChecked(false);
                    cbWhitelistExtra3.setChecked(false);
                }
            }
        });
        cbWhitelistExtra1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    cbCategory.setChecked(false);
                    //cbOnce.setChecked(false); //KO: Remove once checkbox
                    cbWhitelist.setChecked(false);
                    cbWhitelistExtra2.setChecked(false);
                    cbWhitelistExtra3.setChecked(false);
                }
            }
        });
        cbWhitelistExtra2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    cbCategory.setChecked(false);
                    //cbOnce.setChecked(false); //KO: Remove once checkbox
                    cbWhitelist.setChecked(false);
                    cbWhitelistExtra1.setChecked(false);
                    cbWhitelistExtra3.setChecked(false);
                }
            }
        });
        cbWhitelistExtra3.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    cbCategory.setChecked(false);
                    //cbOnce.setChecked(false); //KO: Remove once checkbox
                    cbWhitelist.setChecked(false);
                    cbWhitelistExtra1.setChecked(false);
                    cbWhitelistExtra2.setChecked(false);
                }
            }
        });


        // Setup progress bar
        mProgress.setMax(PrivacyService.cMaxOnDemandDialog * 20);
        mProgress.setProgress(PrivacyService.cMaxOnDemandDialog * 20);

        //KO: Populate help button text
        switch (restriction.restrictionName) {
            case "contacts":
                tvHelp.setText(R.string.help_contacts);
                break;
            case "storage":
                tvHelp.setText(R.string.help_storage);
                break;
            case "location":
                tvHelp.setText(R.string.help_location);
                break;
        }

        //KO: Click listener for the help button
        btnHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (tvHelp.getVisibility() == View.GONE) {
                    tvHelp.setVisibility(View.VISIBLE);
                    //KO: Reset timer
                    holder.reset = true;
                    holder.latch.countDown();
                } else {
                    tvHelp.setVisibility(View.GONE);
                }
            }

        });


        //KO: ToggleListener for the radio group for semantic locations
        RadioGroup.OnCheckedChangeListener ToggleListener = new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final RadioGroup radioGroup, final int i) {
                for (int j = 0; j < radioGroup.getChildCount(); j++) {
                    final ToggleButton view = (ToggleButton) radioGroup.getChildAt(j);
                    view.setChecked(view.getId() == i);
                }

            }
        };

        //KO: Set the listener created above
        semanticLocationButtons.setOnCheckedChangeListener(ToggleListener);

        //KO: Click listener for the home button
        btnHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Log.d("Smarper-debug", "Got home button press");
                Log.d("Smarper-debug", "Semantic location is checked:" + holder.semanticLocationChecked);
                if (holder.semanticLocationChecked == false) {
                    holder.semanticLocationChecked = true;
                    semanticLocationButtons.check(btnHome.getId());
                    oResult.semanticLocationChoice = 0;
                    //Log.d("Smarper-debug", "Set semantic choice to home");
                    enableOnDemandButtons(holder);
                } else if (holder.semanticLocationChecked == true && oResult.semanticLocationChoice == 0) {  //This button is pressed, unpress it
                    holder.semanticLocationChecked = false;
                    oResult.semanticLocationChoice = -1;
                    semanticLocationButtons.clearCheck();
                    //Log.d("Smarper-debug", "Cleared home button press");
                    disableOnDemandButtons(holder);
                } else if (holder.semanticLocationChecked == true && oResult.semanticLocationChoice != 0) {
                    //Log.d("Smarper-debug", "Got home button press but another choice is active, clearing");
                    holder.semanticLocationChecked = true;
                    oResult.semanticLocationChoice = 0;
                    semanticLocationButtons.clearCheck();
                    semanticLocationButtons.check(btnHome.getId());


                }


            }
        });

        //KO: Click listener for the work button
        btnWork.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Log.d("Smarper-debug", "Got work button press");
                if (holder.semanticLocationChecked == false) {
                    holder.semanticLocationChecked = true;
                    semanticLocationButtons.check(btnWork.getId());
                    oResult.semanticLocationChoice = 1;
                    // Log.d("Smarper-debug", "Set semantic choice to work");
                    enableOnDemandButtons(holder);
                } else if (holder.semanticLocationChecked == true && oResult.semanticLocationChoice == 1) {  //This button is pressed, unpress it
                    holder.semanticLocationChecked = false;
                    oResult.semanticLocationChoice = -1;
                    semanticLocationButtons.clearCheck();
                    //Log.d("Smarper-debug", "Cleared work button press");
                    disableOnDemandButtons(holder);
                } else if (holder.semanticLocationChecked == true && oResult.semanticLocationChoice != 1) {
                    // Log.d("Smarper-debug", "Got work button press but another choice is active, clearing");
                    holder.semanticLocationChecked = true;
                    oResult.semanticLocationChoice = 1;
                    semanticLocationButtons.clearCheck();
                    semanticLocationButtons.check(btnWork.getId());
                }


            }
        });

        //KO: Click listener for the transit button
        btnTransit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Log.d("Smarper-debug", "Got transit button press");
                if (holder.semanticLocationChecked == false) {
                    holder.semanticLocationChecked = true;
                    semanticLocationButtons.check(btnTransit.getId());
                    oResult.semanticLocationChoice = 2;
                    //Log.d("Smarper-debug", "Set semantic choice to transit");
                    enableOnDemandButtons(holder);
                } else if (holder.semanticLocationChecked == true && oResult.semanticLocationChoice == 2) {  //This button is pressed, unpress it
                    holder.semanticLocationChecked = false;
                    oResult.semanticLocationChoice = -1;
                    semanticLocationButtons.clearCheck();
                    //Log.d("Smarper-debug", "Cleared transit button press");
                    disableOnDemandButtons(holder);

                } else if (holder.semanticLocationChecked == true && oResult.semanticLocationChoice != 2) {
                    //Log.d("Smarper-debug", "Got transit button press but another choice is active, clearing");
                    holder.semanticLocationChecked = true;
                    oResult.semanticLocationChoice = 2;
                    semanticLocationButtons.clearCheck();
                    semanticLocationButtons.check(btnTransit.getId());
                }


            }
        });

        //KO: Click listener for the other button
        btnOther.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Log.d("Smarper-debug", "Got other button press");
                if (holder.semanticLocationChecked == false) {
                    holder.semanticLocationChecked = true;
                    semanticLocationButtons.check(btnOther.getId());
                    oResult.semanticLocationChoice = 3;
                    // Log.d("Smarper-debug", "Set semantic choice to other");
                    enableOnDemandButtons(holder);

                } else if (holder.semanticLocationChecked == true && oResult.semanticLocationChoice == 3) {  //This button is pressed, unpress it
                    holder.semanticLocationChecked = false;
                    oResult.semanticLocationChoice = -1;
                    semanticLocationButtons.clearCheck();
                    // Log.d("Smarper-debug", "Cleared other button press");
                    disableOnDemandButtons(holder);
                } else if (holder.semanticLocationChecked == true && oResult.semanticLocationChoice != 3) {
                    //Log.d("Smarper-debug", "Got other button press but another choice is active, clearing");
                    holder.semanticLocationChecked = true;
                    oResult.semanticLocationChoice = 3;
                    semanticLocationButtons.clearCheck();
                    semanticLocationButtons.check(btnOther.getId());
                }

            }
        });

        btnAllow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (holder.semanticLocationChecked != false) { //KO: Don't do anything unless this is set
                    // Allow
                    result.restricted = false;
                    result.asked = true;
                    result.restrictState = 0; //KO

                    /*if (cbWhitelist.isChecked())
                        onDemandWhitelist(restriction, null, result, oResult, hook);
                    else if (cbWhitelistExtra1.isChecked())
                        onDemandWhitelist(restriction, getXExtra(restriction, hook)[0], result, oResult, hook);
                    else if (cbWhitelistExtra2.isChecked())
                        onDemandWhitelist(restriction, getXExtra(restriction, hook)[1], result, oResult, hook);
                    else if (cbWhitelistExtra3.isChecked())
                        onDemandWhitelist(restriction, getXExtra(restriction, hook)[2], result, oResult, hook);*/
                    //else {
                    try {
                        PrivacyService.getClient().setSetting(new PSetting(userId, "", PrivacyManager.cSettingODCategory, Boolean.toString(cbCategory.isChecked())));//setSettingBool(userId, "", PrivacyManager.cSettingODCategory, cbCategory.isChecked());
                        PrivacyService.getClient().setSetting(new PSetting(userId, "", PrivacyManager.cSettingODOnce, Boolean.toString(once))); //KO: change cbOnce.isChecked() to once (always true)
                    } catch (Exception e){
                        Log.e("Smarper-Error", "Error calling setSetting from getOnDemandView");
                        e.printStackTrace();
                    }
                        if (once) //KO
                            onDemandOnce(restriction, cbCategory.isChecked(), result, oResult, spOnce,mAskedOnceCache);
                        /* else
                            onDemandChoice(restriction, cbCategory.isChecked(), false);*/
                    //}
                }
                holder.latch.countDown();

            }
        });


        //KO: Obfuscate button
        btnObfuscate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (holder.semanticLocationChecked != false) { //KO

                    result.restricted = true;
                    result.asked = true;
                    result.restrictState = 1; //KO

                    /*if (cbWhitelist.isChecked())
                        onDemandWhitelist(restriction, null, result, oResult, hook);
                    else if (cbWhitelistExtra1.isChecked())
                        onDemandWhitelist(restriction, getXExtra(restriction, hook)[0], result, oResult, hook);
                    else if (cbWhitelistExtra2.isChecked())
                        onDemandWhitelist(restriction, getXExtra(restriction, hook)[1], result, oResult, hook);
                    else if (cbWhitelistExtra3.isChecked())
                        onDemandWhitelist(restriction, getXExtra(restriction, hook)[2], result, oResult, hook);
                    else {*/
                    try {
                        PrivacyService.getClient().setSetting(new PSetting(userId, "", PrivacyManager.cSettingODCategory, Boolean.toString(cbCategory.isChecked())));//setSettingBool(userId, "", PrivacyManager.cSettingODCategory, cbCategory.isChecked());
                        PrivacyService.getClient().setSetting(new PSetting(userId, "", PrivacyManager.cSettingODOnce, Boolean.toString(once))); //KO: change cbOnce.isChecked() to once (always true)
                    } catch (Exception e){
                        Log.e("Smarper-Error", "Error calling setSetting from getOnDemandView");
                        e.printStackTrace();
                    }

                    if (once) //KO
                            onDemandOnce(restriction, cbCategory.isChecked(), result, oResult, spOnce, mAskedOnceCache);
                        /*else
                            onDemandChoice(restriction, cbCategory.isChecked(), false);*/
                    //}
                }
                holder.latch.countDown();

            }
        });


        btnDeny.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (holder.semanticLocationChecked != false) { //KO

                    // Deny
                    result.restricted = true;
                    result.asked = true;
                    result.restrictState = 2; //KO

                    /*if (cbWhitelist.isChecked())
                        onDemandWhitelist(restriction, null, result, oResult, hook);
                    else if (cbWhitelistExtra1.isChecked())
                        onDemandWhitelist(restriction, getXExtra(restriction, hook)[0], result, oResult, hook);
                    else if (cbWhitelistExtra2.isChecked())
                        onDemandWhitelist(restriction, getXExtra(restriction, hook)[1], result, oResult, hook);
                    else if (cbWhitelistExtra3.isChecked())
                        onDemandWhitelist(restriction, getXExtra(restriction, hook)[2], result, oResult, hook);
                    else {*/try {
                        PrivacyService.getClient().setSetting(new PSetting(userId, "", PrivacyManager.cSettingODCategory, Boolean.toString(cbCategory.isChecked())));//setSettingBool(userId, "", PrivacyManager.cSettingODCategory, cbCategory.isChecked());
                        PrivacyService.getClient().setSetting(new PSetting(userId, "", PrivacyManager.cSettingODOnce, Boolean.toString(once))); //KO: change cbOnce.isChecked() to once (always true)
                    } catch (Exception e){
                        Log.e("Smarper-Error", "Error calling setSetting from getOnDemandView");
                        e.printStackTrace();
                    }

                    if (once) //KO
                            onDemandOnce(restriction, cbCategory.isChecked(), result, oResult, spOnce, mAskedOnceCache);
                        //else
                       //     onDemandChoice(restriction, cbCategory.isChecked(), true);
//                    }
                }
                holder.latch.countDown();

            }
        });

        return view;
    }

    private void onDemandOnce(PRestriction restriction, boolean category, PRestriction result, PrivacyService.OnDemandResult oResult, Spinner spOnce, Map<CRestriction,CRestriction> mAskedOnceCache) { //KO: Remove Spinner from arguments
        oResult.once = true;

        // Get duration
        String value = (String) spOnce.getSelectedItem();
        int pos = 8; //KO: Static cache time of one hour
        oResult.cached_duration = pos; //KO

        long cached_duration_time = 0;
        if (value == null)
            cached_duration_time = new Date().getTime() + PrivacyManager.cRestrictionCacheTimeoutMs;
        else {

            cached_duration_time = new Date().getTime() + SmarperUtil.cacheTimes.get(pos) * 1000; //KO: Use the list of possible cache times in SmarperUtil instead
            //Log.d("Smarper-Decisions", "Will be cached for " + SmarperUtil.cacheTimes.get(pos) + " seconds");

            try {
                int userId = Util.getUserId(restriction.uid);
                String sel = Integer.toString(spOnce.getSelectedItemPosition());
                PrivacyService.getClient().setSetting(new PSetting(userId, "", PrivacyManager.cSettingODOnceDuration, sel));//setSettingInternal(new PSetting(userId, "", PrivacyManager.cSettingODOnceDuration, sel));
            } catch (Throwable ex) {
                Util.bug(null, ex);
            }
        }

        Log.d("Smarper-Dialogs", "On demanding once " + restriction.uid + " " + restriction.restrictionName + ", " + restriction.methodName + ", " + restriction.extra + ", " + restriction.value + ", restrictState: " + result.restrictState); //KO: Log these
        Util.log(null, Log.WARN, (result.restricted ? "Deny" : "Allow") + " once " + restriction + " category="
                + category + " until=" + new Date(cached_duration_time));

        CRestriction key = new CRestriction(result, null);
        key.setExpiry(cached_duration_time);
        if (category) {
            key.setMethodName(null);
            key.setExtra(null);
        }
        synchronized (mAskedOnceCache) {
            if (mAskedOnceCache.containsKey(key))
                mAskedOnceCache.remove(key);
            mAskedOnceCache.put(key, key);
        }
    }


    //KO: Our version for displaying recent requests
    public List<PRestriction> getFullUsageList(int uid, String restrictionName, SQLiteDatabase dbUsage, ReentrantReadWriteLock mLockUsage) throws RemoteException {
        List<PRestriction> result = new ArrayList<PRestriction>();
        try {
            //SQLiteDatabase dbUsage = getDbUsage();
            int userId = Util.getUserId(Binder.getCallingUid());

            mLockUsage.readLock().lock();
            try {
                dbUsage.beginTransaction();
                try {
                    Cursor cursor;
                    if (uid == 0) {
                        if ("".equals(restrictionName))
                            cursor = dbUsage.query(cTableUsageFull, new String[]{"method_category", "method", "decision", "decision_time", "parameters", "decision_type",
                                    "cached_duration", "uid", "_id", "decision_modified", "package_name"}, "decision_type=1 OR decision_type=3", null, null, null, "decision_time DESC");
                        else
                            cursor = dbUsage.query(cTableUsageFull, new String[]{"method_category", "method",
                                    "decision", "decision_time", "parameters", "decision_type", "cached_duration", "uid", "_id", "decision_modified", "package_name"}, "method_category=? AND (decision_type=1 OR decision_type=3)", new String[]{
                                    restrictionName}, null, null, "decision_time DESC");
                    } else {
                        if ("".equals(restrictionName))
                            cursor = dbUsage.query(cTableUsageFull, new String[]{"method_category", "method",
                                    "decision", "decision_time", "parameters", "decision_type", "cached_duration", "uid", "_id", "decision_modified", "package_name"}, "uid=? AND (decision_type=1 OR decision_type=3)", new String[]{
                                    Integer.toString(uid)}, null, null, "decision_time DESC");
                        else
                            cursor = dbUsage.query(cTableUsageFull, new String[]{"method_category", "method", "decision", "decision_time", "parameters", "decision_type", "cached_duration",
                                            "uid", "_id", "decision_modified", "package_name"}, "uid=? AND decision=? AND (decision_type=1 OR decision_type=3)", new String[]{Integer.toString(uid), restrictionName}, null, null,
                                    "decision_time DESC");
                    }

                    if (cursor == null)
                        Util.log(null, Log.WARN, "Database cursor null (usage data)");
                    else
                        try {

                            int count = 0;
                            while (count++ < PrivacyService.cMaxUsageDataCount && cursor.moveToNext()) {
                                PRestriction data = new PRestriction();
                                //KO: Modify these for our column set
                                data.restrictionName = cursor.getString(0);
                                data.methodName = cursor.getString(1);

                                if (cursor.getString(9) == null || cursor.getString(9) == "") {
                                    data.restrictState = cursor.getInt(2);
                                    data.decision_type = cursor.getInt(5);
                                } else {
                                    data.restrictState = cursor.getInt(9);
                                    data.decision_type = 3;
                                }

                                data.time = cursor.getLong(3);
                                data.extra = cursor.getString(4);
                                data.asked = (cursor.getInt(5) > 0); //KO: Remove this later, shouldn't be used
                                data.cached_duration = (cursor.getInt(6));
                                data.uid = cursor.getInt(7);
                                data.id = cursor.getInt(8);
                                data.package_name = cursor.getString(10);             //JS

                                if (userId == 0 || Util.getUserId(data.uid) == userId)
                                    result.add(data);
                            }
                        } finally {
                            cursor.close();
                        }

                    dbUsage.setTransactionSuccessful();
                } finally {
                    dbUsage.endTransaction();
                }
            } finally {
                mLockUsage.readLock().unlock();
            }
        } catch (Throwable ex) {
            Util.bug(null, ex);
            Log.e("Smarper-Error", "Error when reading from database: ");
            dbErrors.add(ex);
            checkDbErrors(dbUsage);
            throw new RemoteException(ex.toString());
        }
        return result;
    }


    //JS: Fetch the applications icons from database
    public List<appPackNameAndIcon> getAppIcons(SQLiteDatabase dbUsage, ReentrantReadWriteLock mLockUsage) throws RemoteException {
        List<appPackNameAndIcon> result = new ArrayList<appPackNameAndIcon>();
        try {
            //SQLiteDatabase dbUsage = getDbUsage();

            mLockUsage.readLock().lock();
            try {
                dbUsage.beginTransaction();
                try {
                    Cursor cursor = null;
                    cursor = dbUsage.query("package_names", new String[]{"package_name", "icon", "app_name", "uid"}, null, null, null, null, null);

                    if (cursor == null)
                        Util.log(null, Log.WARN, "Database cursor null (app icons)");
                    else
                        try {
                            while (cursor.moveToNext()) {
                                if (cursor.getBlob(1) != null){
                                    appPackNameAndIcon data = new appPackNameAndIcon();
                                    data.app_PackageName = cursor.getString(0);
                                    data.app_Icon = cursor.getBlob(1);
                                    data.app_Icon_size = data.app_Icon.length;
                                    data.app_Name = cursor.getString(2);
                                    data.app_uid = cursor.getInt(3);
                                    result.add(data);
                                }
                            }
                        } finally {
                            cursor.close();
                        }
                    dbUsage.setTransactionSuccessful();
                } finally {
                    dbUsage.endTransaction();
                }
            } finally {
                mLockUsage.readLock().unlock();
            }
        } catch (Throwable ex) {
            Util.bug(null, ex);
            Log.e("Smarper-Error", "Error when reading from database: ");
            dbErrors.add(ex);
            checkDbErrors(dbUsage);
            throw new RemoteException(ex.toString());
        }
        return result;
    }


    //JS: Method used to fetch the required filtered data from the database
    public List<PRestriction> GetFilteredData(int[] decision, int[] decisiontype, int time, List<String> MethCat, List<String> AppList, SQLiteDatabase dbUsage, ReentrantReadWriteLock mLockUsage)
            throws RemoteException {          //JS

        List<PRestriction> result = new ArrayList<PRestriction>();

        try {
            //SQLiteDatabase dbUsage = getDbUsage();
            int userId = Util.getUserId(Binder.getCallingUid());

            mLockUsage.readLock().lock();
            try {
                dbUsage.beginTransaction();
                try {
                    //JS: Define time categories of filter
                    String sFrom = "";
                    if (time == 0) {                     //JS: Past Hour
                        sFrom = Long.toString(new Date().getTime() - 1 * 60L * 60L * 1000L);
                    } else if (time == 1) {                     //JS: Today
                        Long x = new Date().getTime() % (24L * 60L * 60L * 1000L);
                        sFrom = Long.toString(new Date().getTime() - x);
                    } else if (time == 2) {                     //JS: Past Day
                        sFrom = Long.toString(new Date().getTime() - 24 * 60L * 60L * 1000L);
                    }

                    Cursor cursor = null;
                    String query;
                    int i = 0;

                    //JS: if decision[0] = 0, then the decision to be filtered is Allow
                    //JS: if decision[1] = 1, then the decision to be filtered is Obfuscate
                    //JS: if decision[2] = 2, then the decision to be filtered is Deny

                    //JS: if decisiontype[0] = 0, then the decision_type to be filtered is Static
                    //JS: if decisiontype[1] = 1, then the decision_type to be filtered is User
                    //JS: if decisiontype[2] = 2, then the decision_type to be filtered is User Cached
                    //JS: if decisiontype[3] = 3, then the decision_type to be filtered is User Modified
                    //JS: if decisiontype[4] = 4, then the decision_type to be filtered is Timeout



                    //JS: if at least one decision AND one decision type are checked (at least one of each)
                    if (((decision[0] + decision[1] + decision[2]) != -3) && ((decisiontype[0] + decisiontype[1] + decisiontype[2]
                            + decisiontype[3] + decisiontype[4]) != -5)) {

                        //JS: if a time category has been selected to be filtered
                        if (time != -1) query = "decision_time>? AND ( ( (decision=" + decision[0] + " OR decision=" + decision[1]
                                + " OR decision=" + decision[2] + ") AND decision_type!=3) OR ( (decision_modified=" + decision[0]
                                + " OR decision_modified=" + decision[1] + " OR decision_modified=" + decision[2] + ") AND decision_type=3) "
                                + ") AND (decision_type=" + decisiontype[0] + " OR decision_type=" + decisiontype[1] + " OR decision_type="
                                + decisiontype[2] + " OR decision_type=" + decisiontype[3] + " OR decision_type=" + decisiontype[4] + ")";

                            //JS: if no time categories have been selected to be filtered
                        else query = "( ( (decision=" + decision[0] + " OR decision=" + decision[1] + " OR decision=" + decision[2]
                                + ") AND decision_type!=3) OR ( (decision_modified=" + decision[0] + " OR decision_modified=" + decision[1]
                                + " OR decision_modified=" + decision[2] + ") AND decision_type=3) ) AND (decision_type=" + decisiontype[0]
                                + " OR decision_type=" + decisiontype[1] + " OR decision_type=" + decisiontype[2] + " OR decision_type="
                                + decisiontype[3] + " OR decision_type=" + decisiontype[4] + ")";

                        //JS: if at least one method category has been selected to be filtered
                        i = 0;
                        if (MethCat.size() != 0) query = query + " AND (";
                        while (i < MethCat.size()) {
                            query = query + "method_category=" + MethCat.get(i);
                            if (i < (MethCat.size() - 1)) {
                                query = query + " OR ";
                            }
                            i++;
                        }
                        if (MethCat.size() != 0) query = query + ")";

                        //JS: if at least one application has been selected to be filtered
                        i = 0;
                        if (AppList.size() != 0) query = query + " AND (";
                        while (i < AppList.size()) {
                            query = query + "app_name=" + AppList.get(i);
                            if (i < (AppList.size() - 1)) {
                                query = query + " OR ";
                            }
                            i++;
                        }
                        if (AppList.size() != 0) query = query + ")";

                        //JS: if a time category has been selected to be filtered
                        if (time != -1) cursor = dbUsage.query(cTableUsageFull, new String[]{"method_category", "method", "decision",
                                "decision_time", "parameters", "decision_type", "cached_duration", "uid", "_id", "decision_modified",
                                "package_name"}, query, new String[]{sFrom}, null, null, "decision_time DESC");

                            //JS: if no time categories have been selected to be filtered
                        else cursor = dbUsage.query(cTableUsageFull, new String[]{"method_category", "method", "decision", "decision_time",
                                        "parameters", "decision_type", "cached_duration", "uid", "_id", "decision_modified", "package_name"},
                                query, null, null, null, "decision_time DESC");
                    }

                    //JS: if at least one decision type is checked AND no decisions are checked
                    else if (((decision[0] + decision[1] + decision[2]) == -3) && ((decisiontype[0] + decisiontype[1] + decisiontype[2]
                            + decisiontype[3] + decisiontype[4]) != -5)) {

                        //JS: if a time category has been selected to be filtered
                        if (time != -1) query = "decision_time>? AND (decision_type=" + decisiontype[0] + " OR decision_type="
                                + decisiontype[1] + " OR decision_type=" + decisiontype[2] + " OR decision_type=" + decisiontype[3]
                                + " OR decision_type=" + decisiontype[4] + ")";

                            //JS: if no time categories have been selected to be filtered
                        else query = "(decision_type=" + decisiontype[0] + " OR decision_type=" + decisiontype[1] + " OR decision_type="
                                + decisiontype[2] + " OR decision_type=" + decisiontype[3] + " OR decision_type=" + decisiontype[4] + ")";

                        //JS: if at least one method category has been selected to be filtered
                        i = 0;
                        if (MethCat.size() != 0) query = query + " AND (";
                        while (i < MethCat.size()) {
                            query = query + "method_category=" + MethCat.get(i);
                            if (i < (MethCat.size() - 1)) {
                                query = query + " OR ";
                            }
                            i++;
                        }
                        if (MethCat.size() != 0) query = query + ")";


                        //JS: if at least one application has been selected to be filtered
                        i = 0;
                        if (AppList.size() != 0) query = query + " AND (";
                        while (i < AppList.size()) {
                            query = query + "app_name=" + AppList.get(i);
                            if (i < (AppList.size() - 1)) {
                                query = query + " OR ";
                            }
                            i++;
                        }
                        if (AppList.size() != 0) query = query + ")";

                        //JS: if a time category has been selected to be filtered
                        if (time != -1) cursor = dbUsage.query(cTableUsageFull, new String[]{"method_category", "method", "decision",
                                "decision_time", "parameters", "decision_type", "cached_duration", "uid", "_id", "decision_modified",
                                "package_name"}, query, new String[]{sFrom}, null, null, "decision_time DESC");

                            //JS: if no time categories have been selected to be filtered
                        else cursor = dbUsage.query(cTableUsageFull, new String[]{"method_category", "method", "decision", "decision_time",
                                        "parameters", "decision_type", "cached_duration", "uid", "_id", "decision_modified", "package_name"},
                                query, null, null, null, "decision_time DESC");

                    }

                    //JS: if at least one decision is checked AND no decision types are checked
                    else if (((decision[0] + decision[1] + decision[2]) != -3) && ((decisiontype[0] + decisiontype[1] + decisiontype[2]
                            + decisiontype[3] + decisiontype[4]) == -5)) {

                        //JS: if a time category has been selected to be filtered
                        if (time != -1) query = "decision_time>? AND ( ( (decision=" + decision[0] + " OR decision=" + decision[1]
                                + " OR decision=" + decision[2] + ") AND decision_type!=3) OR ( (decision_modified=" + decision[0]
                                + " OR decision_modified=" + decision[1] + " OR decision_modified=" + decision[2] + ") AND decision_type=3) )";

                            //JS: if no time categories have been selected to be filtered
                        else query = "( ( (decision=" + decision[0] + " OR decision=" + decision[1] + " OR decision=" + decision[2]
                                + ") AND decision_type!=3) OR ( (decision_modified=" + decision[0] + " OR decision_modified=" + decision[1]
                                + " OR decision_modified=" + decision[2] + ") AND decision_type=3) )";

                        //JS: if at least one method category has been selected to be filtered
                        i = 0;
                        if (MethCat.size() != 0) query = query + " AND (";
                        while (i < MethCat.size()) {
                            query = query + "method_category=" + MethCat.get(i);
                            if (i < (MethCat.size() - 1)) {
                                query = query + " OR ";
                            }
                            i++;
                        }
                        if (MethCat.size() != 0) query = query + ")";

                        //JS: if at least one application has been selected to be filtered
                        i = 0;
                        if (AppList.size() != 0) query = query + " AND (";
                        while (i < AppList.size()) {
                            query = query + "app_name=" + AppList.get(i);
                            if (i < (AppList.size() - 1)) {
                                query = query + " OR ";
                            }
                            i++;
                        }
                        if (AppList.size() != 0) query = query + ")";

                        //JS: if a time category has been selected to be filtered
                        if (time != -1) cursor = dbUsage.query(cTableUsageFull, new String[]{"method_category", "method", "decision",
                                "decision_time", "parameters", "decision_type", "cached_duration", "uid", "_id", "decision_modified",
                                "package_name"}, query, new String[]{sFrom}, null, null, "decision_time DESC");

                            //JS: if no time categories have been selected to be filtered
                        else cursor = dbUsage.query(cTableUsageFull, new String[]{"method_category", "method", "decision", "decision_time",
                                        "parameters", "decision_type", "cached_duration", "uid", "_id", "decision_modified", "package_name"},
                                query, null, null, null, "decision_time DESC");
                    }


                    //JS: if no decisions AND no decision types are checked
                    else {
                        //JS: if a time category has been selected to be filtered
                        if (time != -1) query = "decision_time>?";

                            //JS: if no time categories have been selected to be filtered
                        else query = "";

                        //JS: if at least one method category has been selected to be filtered
                        i = 0;
                        if (MethCat.size() != 0) {
                            if (time != -1) query = query + " AND (";
                            else query = query + "(";
                        }
                        while (i < MethCat.size()) {
                            query = query + "method_category=" + MethCat.get(i);
                            if (i < (MethCat.size() - 1)) {
                                query = query + " OR ";
                            }
                            i++;
                        }
                        if (MethCat.size() != 0) query = query + ")";

                        //JS: if at least one application has been selected to be filtered
                        i = 0;
                        if (AppList.size() != 0) {
                            if (time != -1 || MethCat.size() != 0) query = query + " AND (";
                            else query = query + "(";
                        }
                        while (i < AppList.size()) {
                            query = query + "app_name=" + AppList.get(i);
                            if (i < (AppList.size() - 1)) {
                                query = query + " OR ";
                            }
                            i++;
                        }
                        if (AppList.size() != 0) query = query + ")";

                        //JS: if a time category has been selected to be filtered
                        if (time != -1) cursor = dbUsage.query(cTableUsageFull, new String[]{"method_category", "method", "decision",
                                "decision_time", "parameters", "decision_type", "cached_duration", "uid", "_id", "decision_modified",
                                "package_name"}, query, new String[]{sFrom}, null, null, "decision_time DESC");

                            //JS: if no time categories have been selected to be filtered
                        else cursor = dbUsage.query(cTableUsageFull, new String[]{"method_category", "method", "decision", "decision_time",
                                        "parameters", "decision_type", "cached_duration", "uid", "_id", "decision_modified", "package_name"},
                                query, null, null, null, "decision_time DESC");
                    }



                    //JS: Cursor is null
                    if (cursor == null) {
                        Util.log(null, Log.WARN, "Database cursor null (filtered data)");
                    } else
                        try {
                            //JS: Fetching the data from the database
                            int count = 0;
                            while (cursor.moveToNext() && count++ < PrivacyService.cMaxUsageDataCount) {
                                PRestriction data = new PRestriction();
                                data.restrictionName = cursor.getString(0);
                                data.methodName = cursor.getString(1);
                                if (cursor.getString(9) == null || cursor.getString(9) == "") {
                                    data.restrictState = cursor.getInt(2);
                                    data.decision_type = cursor.getInt(5);
                                } else {
                                    data.restrictState = cursor.getInt(9);
                                    data.decision_type = 3;
                                }
                                data.time = cursor.getLong(3);
                                data.extra = cursor.getString(4);
                                data.cached_duration = (cursor.getInt(6));
                                data.uid = cursor.getInt(7);
                                data.id = cursor.getInt(8);
                                data.package_name = cursor.getString(10);

                                if (userId == 0 || Util.getUserId(data.uid) == userId)
                                    result.add(data);
                            }
                        } finally {
                            cursor.close();
                        }

                    dbUsage.setTransactionSuccessful();
                } finally {
                    dbUsage.endTransaction();
                }
            } finally {
                mLockUsage.readLock().unlock();
            }
        } catch (Throwable ex) {
            Util.bug(null, ex);
            dbErrors.add(ex);
            lastDbErrorTime = new Date().getTime();
            throw new RemoteException(ex.toString());
        }
        return result;
    }
    //KO: Add the info from userapplist into the PRestriction
    private void populatePackageName(PRestriction result, FeatureSet f){

        userAppsLock.lock();
        ApplicationInfoEx thisApp = userApps.get(result.uid);
        userAppsLock.unlock();

        //KO: Should never happen, but just in case we don't have the app in our list
        String packageName = "";

        if (thisApp != null) {
            result.package_name = TextUtils.join(", ", thisApp.getPackageName());
        } else {
            Log.w("Smarper-Warn", "Could not find app info for app uid " + result.uid);
        }


    }


    //KO: When rate-limited, or can't interrupt the user, we take the previous decision in that context
    //KO: Context in this case = requesting app is in the foreground or background
    //KO: return true: success, return false: no previous decision
    public boolean takePreviousDecision(FeatureSet f, PRestriction mresult, boolean isForeground, SQLiteDatabase dbUsage, ReentrantReadWriteLock mLockUsage){

        //Query the usage database

        //Is requesting app in the foreground?
        boolean hasPreviousDecision = false; //KO: Was there a previous decision to fetch?
        //SQLiteDatabase dbUsage = getDbUsage();
        try {
            mLockUsage.readLock().lock();
            try {
                dbUsage.beginTransaction();
                Cursor cur;
                if (isForeground) {
                    //cur = dbUsage.execSQL("SELECT decision, _id from usage_full WHERE (decision_type=1 OR decision_type=3) AND package_name=\"mresult.package_name\" AND foreground_package_name=\"f.topApp_package\" AND method_category=\"mresult.restricitonName\" order by _id DESC;");
                    cur = dbUsage.query(cTableUsageFull, new String[]{"decision", "_id"}, "(decision_type=? OR decision_type=?) AND package_name=? AND foreground_package_name=? AND method_category=?", new String[]{"1", "3", mresult.package_name, mresult.package_name, mresult.restrictionName}, null, null, "_id DESC");
                } else {
                    cur = dbUsage.query(cTableUsageFull, new String[]{"decision", "_id"}, "(decision_type=? OR decision_type=?) AND package_name=? AND foreground_package_name!=? AND method_category=?", new String[]{"1", "3", mresult.package_name, mresult.package_name, mresult.restrictionName}, null, null, "_id DESC");
                }

                if (cur.getCount() > 0) {
                    cur.moveToFirst();
                    int decision = cur.getInt(0);
                    mresult.restrictState = decision;
                    mresult.decision_type = 6;
                    hasPreviousDecision = true;
                    dbUsage.setTransactionSuccessful();

                    Log.d("Smarper-debug", mresult.toString() + " // Taking previous decision, app is in the foreground : "+ isForeground +
                            ", decision: " + decision + ", package_name: " + mresult.package_name + ", foreground_package: " + f.topApp_package + ", restrictionName: " + mresult.restrictionName);
                    Log.d("Smarper-debug", mresult.toString() + " // _id of returned row: " + cur.getInt(1));
                }
                else {
                    Log.w("Smarper-Warn", mresult.toString() + " // No previous decision!" );
                }

                cur.close();

            } finally {
                if (dbUsage.inTransaction())
                    dbUsage.endTransaction();
            }
        } finally {

            mLockUsage.readLock().unlock();
        }

        return hasPreviousDecision;
    }



    //KO: Update the rate limit information when an interval expires
    public void updateRateLimits(){

        long currentTime = System.currentTimeMillis();
        if (currentTime > rateLimitIntervalExpiryTimestamp) {

            //Generate a random number
            double rand = Math.random();

            //Convert this into a random number of minutes in the interval [10,20]
            double minutes = (rand * 10) + 10;

            //Convert to milliseconds
            long intervalMillis = (long) minutes * 60 * 1000;

            //Create new expiry time
            long expiry = currentTime + intervalMillis;

            //Set it
            rateLimitIntervalExpiryTimestamp = expiry;

            Log.d("Smarper-debug", "Current time is: " + currentTime + ", next prompt can appear in " + minutes + " minutes, at time " + expiry);
        }
    }


    //KO: Check if rate limit is exceeded for this request (background apps only)
    public boolean rateLimitExceeded(boolean isForeground){

        long currentTime = System.currentTimeMillis();

        if (isForeground){
            Log.d("Smarper-debug", "Ignoring rate limit info for foreground apps");
            return false;
        }

        //Check if expired
        else if (currentTime > rateLimitIntervalExpiryTimestamp){
            Log.d("Smarper-debug", "Rate limit interval is expired, we will prompt");
            return false;
        }

        else {
            Log.d("Smarper-debug", "Rate limit interval not expired yet!");
            return true;
        }

    }


    //KO: Check if the user is doing an activity that should not be interrupted
    public boolean doNotInterruptNow(Context c, boolean isForeground){

        //Check if in call
        TelephonyManager tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm.getCallState() != TelephonyManager.CALL_STATE_IDLE && !isForeground) {
            Log.d("Smarper-Debug", "User is in call, should not on demand");
            return true;
        }

        //Check if typing
        if (interruptibilityStatus.isTyping && !isForeground){
            Log.d("Smarper-Debug", "User is typing, should not on demand");
            return true;
        }

        //Check if camera is open
        if (interruptibilityStatus.cameraOpen && !isForeground){
            Log.d("Smarper-Debug", "User is using camera, should not on demand");
            return true;
        }


        return false;
    }

    //KO: Extracted some checks done at beginning of onDemandDialog into this separate method
    private boolean shouldOnDemand(final Hook hook, final PRestriction restriction, final PRestriction result, final boolean settingOnDemandSystem, int xuid,boolean onDemandUserId,boolean onDemandUid
    , String version, Context context) throws Exception {


        int userId = Util.getUserId(restriction.uid);

        // Check if application
        if (!PrivacyManager.isApplication(restriction.uid)) {
            if (!settingOnDemandSystem) {
                Log.d("Smarper-Restrictions", result.toString() + " // onDemandSystem is false, not asking"); //OK
                return false;
            }
        }

        // Check for exceptions
        if (hook != null && !hook.canOnDemand()) {
            Log.d("Smarper-Restrictions", result.toString() + " // can't onDemand this hook, not asking"); //OK
            return false;
        }
        if (!PrivacyManager.canRestrict(restriction.uid, xuid, restriction.restrictionName,
                restriction.methodName, false)) {
            Log.d("Smarper-Restrictions", result.toString() + " // PrivacyManager says can't restrict, not asking"); //OK
            return false;
        }

        // Check if enabled
        if (!onDemandUserId) {
            Log.d("Smarper-Restrictions", result.toString() + " // onDemand disabled for " + userId + ", not asking"); //OK
            return false;
        }
        if (!onDemandUid) {
            Log.d("Smarper-Restrictions", result.toString() + " // onDemand disabled for " + restriction.uid + ", not asking"); //OK
            return false;
        }

        // Check version
        //String version = getSetting(new PSetting(userId, "", PrivacyManager.cSettingVersion, "0.0")).value; //OK
        if (new Version(version).compareTo(new Version("2.1.5")) < 0) {
            Log.d("Smarper-Restrictions", result.toString() + " // Wrong version, not asking");
            return false;
        }

        // Get activity manager context
       // final Context context = getContext();
        if (context == null) {
            Log.e("Smarper-Error", result.toString() + " // got null context, ERROR"); //ERROR -- so if we error out here we can't know if we should have on-demanded or not
            throw new Exception("Should on demand check: context is null");
        }

        long token = 0;
        try {
            token = Binder.clearCallingIdentity();

            // Get application info
            final ApplicationInfoEx appInfo = new ApplicationInfoEx(context, restriction.uid);

            // Check for system application
            if (appInfo.isSystem()) {
                if (new Version(version).compareTo(new Version("2.0.38")) < 0) {
                    Log.d("Smarper-Restrictions", result.toString() + " // Version/and system app problem, not asking"); //OK
                    return false;
                }
            }


        } finally {
            Binder.restoreCallingIdentity(token);
        }


        return true;
    }

    //KO: Extracted cache check into a separate method
    private synchronized boolean checkCache(final Hook hook, PRestriction restriction, PRestriction result, PrivacyService.OnDemandResult oResult, Map<CRestriction,CRestriction> mRestrictionCache,
                                            Map<CRestriction,CRestriction> mAskedOnceCache, Map<CSetting,CSetting> mSettingCache, String[] exExtra) {

        long token = Binder.clearCallingIdentity();

        Log.d("Smarper-Restrictions", result.toString() + " // checking cache now");

        // Check if method not asked before
        CRestriction mkey = new CRestriction(restriction, null);
        synchronized (mRestrictionCache) {
            if (mRestrictionCache.containsKey(mkey)) {
                CRestriction mrestriction = mRestrictionCache.get(mkey);
                if (mrestriction.asked) {
                    Log.d("Smarper-Restrictions", "Already asked " + restriction);
                    result.restricted = mrestriction.restricted;
                    result.asked = true;
                    return true;
                }
            }
        }

        // Check if category not asked before (once)
        CRestriction ckey = new CRestriction(restriction, null);
        ckey.setMethodName(null);
        synchronized (mAskedOnceCache) {
            if (mAskedOnceCache.containsKey(ckey)) {
                CRestriction carestriction = mAskedOnceCache.get(ckey);
                if (!carestriction.isExpired()) {
                    Util.log(null, Log.WARN, "Already asked once category " + restriction);
                    result.restricted = carestriction.restricted;
                    result.restrictState = carestriction.restrictState; //KO
                    result.asked = true;
                    oResult.cached = true; //KO: It was in the cache
                    oResult.dialogSucceeded = true;
                    Log.d("Smarper-Restrictions", result.toString() + " // not showing dialog, was cached");
                    Log.d("Smarper-Dialogs", "Already asked once category " + restriction.restrictionName + ", " + restriction.methodName + ", " + restriction.extra + ", " + restriction.value + " result was: " + result.restrictState);
                    return true;

                }
            }
        }

        // Check if method not asked before once
        synchronized (mAskedOnceCache) {
            if (mAskedOnceCache.containsKey(mkey)) {
                CRestriction marestriction = mAskedOnceCache.get(mkey);
                if (!marestriction.isExpired()) {
                    Util.log(null, Log.WARN, "Already asked once method " + restriction);
                    result.restricted = marestriction.restricted;
                    result.restrictState = marestriction.restrictState; //KO
                    result.asked = true;
                    oResult.cached = true; //KO: was cached
                    oResult.dialogSucceeded = true;   //JS

                    Log.d("Smarper-Dialogs", "Already asked once method " + restriction.restrictionName + ", " + restriction.methodName + ", " + restriction.extra + ", " + restriction.value + " result was: " + result.restrictState);
                    return true;
                }
            }
        }

        // Check if whitelist not asked before
        if (restriction.extra != null && hook != null && hook.whitelist() != null) {
            CSetting skey = new CSetting(restriction.uid, hook.whitelist(), restriction.extra);
            synchronized (mSettingCache) {
                if (mSettingCache.containsKey(skey)) {
                    String value = mSettingCache.get(skey).getValue();
                    if (value != null) {
                        Log.d("Smarper-Restrictions", "Already asked whitelist " + skey);
                        result.restricted = Boolean.parseBoolean(value);
                        result.asked = true;
                        return true;

                    }

                }
                for (String xextra : exExtra) {
                    CSetting xkey = new CSetting(restriction.uid, hook.whitelist(), xextra);
                    if (mSettingCache.containsKey(xkey)) {
                        String value = mSettingCache.get(xkey).getValue();
                        if (value != null) {
                            Log.d("Smarper-Restrictions", "Already asked whitelist " + xkey);
                            result.restricted = Boolean.parseBoolean(value);
                            result.asked = true;
                            return true;

                        }
                    }
                }
            }
        }

        Binder.restoreCallingIdentity(token);
        return false;
    }



    //KO: Only enable the buttons after a semantic location is chosen
    public void enableOnDemandButtons(PrivacyService.OnDemandDialogHolder holder){
        holder.dialog.findViewById(R.id.btnAllow).setEnabled(true);
        holder.dialog.findViewById(R.id.btnObfuscate).setEnabled(true);
        holder.dialog.findViewById(R.id.btnDeny).setEnabled(true);
    }

    //KO: Disable the buttons if the semantic location choice is reset
    public void disableOnDemandButtons(PrivacyService.OnDemandDialogHolder holder){
        holder.dialog.findViewById(R.id.btnAllow).setEnabled(false);
        holder.dialog.findViewById(R.id.btnObfuscate).setEnabled(false);
        holder.dialog.findViewById(R.id.btnDeny).setEnabled(false);
    }
    //KO: Notification to contact us if an error occurred with data upload. Modified the method above
    private static void notifyContactUs(Context context){

        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);

        // Build notification
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context);
        notificationBuilder.setSmallIcon(R.mipmap.ic_launcher);
        notificationBuilder.setContentTitle(context.getString(R.string.app_name));
        notificationBuilder.setContentText("An error occurred when uploading your data to our server. Please contact us.");
        notificationBuilder.setWhen(System.currentTimeMillis());
        notificationBuilder.setAutoCancel(false);
        Notification notification = notificationBuilder.build();

        // Display notification
        notificationManager.notify(SmarperUtil.NOTIFY_DATA_UPLOAD_ERROR, notification);


    }
    //KO: Populate the static usage database tables
    private synchronized void populateDbUsageTables(SQLiteDatabase dbUsage){

        try {
            dbUsage.beginTransaction();
            try {

                //Same for headphone types
                //Define the "none" headphones type first, for which the id is 1
                dbUsage.execSQL("INSERT INTO headphones_types (\"headphones_type\") VALUES (\"none\")");

                //Populate method categories
                List<String> methodCategories = PrivacyManager.getRestrictions();
                String valuesStr = ""; //For the values clause in insert statement
                for (String method_category : methodCategories) {
                    valuesStr += "(\"" + method_category + "\")";
                    if (methodCategories.lastIndexOf(method_category) != methodCategories.size() - 1) //if it's the last item, don't add a ,
                        valuesStr += ", ";
                }

                dbUsage.execSQL("INSERT INTO method_categories (\"method_category\") VALUES " + valuesStr);

                //Populate method names
                for (String method_category : methodCategories) {
                    String methodsValuesStr = "";
                    List<Hook> methods = PrivacyManager.getHooks(method_category, new Version("" + PrivacyService.cCurrentVersion));

                    for (Hook method : methods) {
                        methodsValuesStr += "(\"" + method.getName() + "\")";
                        if (methods.lastIndexOf(method) != methods.size() - 1) //if it's the last item, don't add a ,
                            methodsValuesStr += ", ";
                    }

                    dbUsage.execSQL("INSERT INTO method_names (\"method\") VALUES " + methodsValuesStr);

                }

                dbUsage.execSQL("INSERT INTO location_providers (\"provider\") VALUES (\"null\"), (\"" + LocationManager.PASSIVE_PROVIDER + "\"), (\"" + LocationManager.NETWORK_PROVIDER + "\"), (\"" + LocationManager.GPS_PROVIDER + "\")");

                List<Integer> cache_times = SmarperUtil.cacheTimes;
                String timesValuesStr = "";
                for (int i = 0; i < cache_times.size(); i++) {
                    timesValuesStr += "(" + i + ", " + cache_times.get(i) + ")";
                    if (i != cache_times.size() - 1) //if it's the last item, don't add a ,
                        timesValuesStr += ", ";
                }

                dbUsage.execSQL("INSERT INTO cache_times VALUES " + timesValuesStr);

                //If there are no group ids
                dbUsage.execSQL("INSERT INTO group_ids (group_names, group_ids) VALUES (\"null\", \"null\")");

                dbUsage.setTransactionSuccessful();

            } finally {
                dbUsage.endTransaction();
            }
        } catch (Exception e) {
            Log.e("Smarper-Error", "Error initially populating db tables!");
            e.printStackTrace();
        }

    }


    //KO
    public synchronized SQLiteDatabase createUsageDb(File dbUsageFile, ReentrantReadWriteLock mLockUsage, Context context, SQLiteDatabase db, ReentrantReadWriteLock mLock){
        //Create a new database
        SQLiteDatabase dbUsage = null;

        //Check if directories exist
        if (!dbUsageFile.getParentFile().isDirectory())
            dbUsageFile.getParentFile().mkdirs();

        try {
            //Delete old file, if exists
            //File dbUsageFile = getDbUsageFile();
            File usageJournal = new File(dbUsageFile + "-journal"); //This file may not exist
            if (dbUsageFile.exists())
                dbUsageFile.delete();

            if (usageJournal.exists())
                usageJournal.delete();

            dbUsage = SQLiteDatabase.openOrCreateDatabase(dbUsageFile, null); //This can cause an IO exception, if this happens just retry

            // Upgrade database if needed
            if (dbUsage.needUpgrade(SmarperUtil.db_schema_version)) {
                Log.d("Smarper-Debug", "Creating usage database");
                mLockUsage.writeLock().lock();
                try {
                    dbUsage.beginTransaction();
                    try {
                        dbUsage.execSQL("PRAGMA foreign_keys = ON");

                        //Smarper params table
                        //dbUsage.execSQL("CREATE TABLE smarper_params(key TEXT NOT NULL, value TEXT NOT NULL)");

                        //Create the view table and additional tables for storing repetitive data
                        //Group Ids
                        //Gid set ID is an id corresponding to a set of group ids
                        dbUsage.execSQL("CREATE TABLE group_ids (gid_set_id INTEGER PRIMARY KEY AUTOINCREMENT, group_ids TEXT, group_names TEXT)");

                        //Package names
                        dbUsage.execSQL("CREATE TABLE package_names (package_id INTEGER PRIMARY KEY AUTOINCREMENT, package_name TEXT, icon BLOB, app_name TEXT, uninstalled INTEGER, uid INTEGER)");

                        //Method categories
                        dbUsage.execSQL("CREATE TABLE method_categories (method_category_id INTEGER PRIMARY KEY AUTOINCREMENT, method_category TEXT)");

                        //Methods
                        dbUsage.execSQL("CREATE TABLE method_names (method_id INTEGER PRIMARY KEY AUTOINCREMENT, method TEXT)");

                        //Headphone types
                        dbUsage.execSQL("CREATE TABLE headphones_types (headphones_id INTEGER PRIMARY KEY AUTOINCREMENT, headphones_type TEXT)");

                        //Location providers
                        dbUsage.execSQL("CREATE TABLE location_providers (provider_id INTEGER PRIMARY KEY AUTOINCREMENT, provider TEXT)");

                        //How long to cache user decisions for
                        dbUsage.execSQL("CREATE TABLE cache_times (time_id INTEGER PRIMARY KEY, seconds INTEGER)");

                        //Create the new, compact table
                        dbUsage.execSQL("CREATE TABLE usage_compact (_id INTEGER PRIMARY KEY AUTOINCREMENT, uid INTEGER NOT NULL, gid_set_id INTEGER, package_id INTEGER, app_name TEXT, version TEXT, app_category TEXT, method_category_id INTEGER NOT NULL, method_id INTEGER NOT NULL, parameters TEXT, is_dangerous INTEGER NOT NULL, decision INTEGER NOT NULL, cached_duration INTEGER NOT NULL, decision_type INTEGER NOT NULL, decision_elapsed INTEGER NOT NULL, decision_time INTEGER NOT NULL, decision_modified INTEGER, foreground_package_id INTEGER, foreground_app_name TEXT, foreground_activity TEXT, screen_interactive INTEGER, screen_lock INTEGER, ringer_state INTEGER, headphones_plugged INTEGER, headphones_id INTEGER, headphones_mike INTEGER, battery_percent REAL, charging_state INTEGER, charge_plug INTEGER, conn_type INTEGER, dock INTEGER, lat REAL, long REAL, type_of_place TEXT, provider_id INTEGER, FOREIGN KEY (package_id) REFERENCES package_names(package_id), FOREIGN KEY (gid_set_id) REFERENCES group_ids(gid_set_id), FOREIGN KEY (cached_duration) REFERENCES cache_times(time_id), FOREIGN KEY (method_category_id) REFERENCES method_categories(method_category_id), FOREIGN KEY(method_id) REFERENCES method_names(method_id), FOREIGN KEY(foreground_package_id) REFERENCES package_names(package_id), FOREIGN KEY (headphones_id) REFERENCES headphones_types(headphones_id))");
                        dbUsage.execSQL("CREATE INDEX idx_time ON usage_compact(decision_time)");

                        dbUsage.execSQL("CREATE VIEW usage_full AS SELECT _id, U.uid, G.group_names AS gids, P1.package_name, U.app_name, version, app_category, C.method_category, M.method, parameters, is_dangerous, decision, CF.seconds AS cached_duration, decision_type, decision_elapsed, decision_time, decision_modified, P2.package_name AS foreground_package_name, foreground_app_name, foreground_activity, screen_interactive, screen_lock, ringer_state, headphones_plugged, H.headphones_type, headphones_mike, battery_percent, charging_state, charge_plug, conn_type, dock, lat, long, type_of_place, L.provider from usage_compact U JOIN group_ids G ON U.gid_set_id = G.gid_set_id JOIN package_names P1 ON U.package_id = P1.package_id JOIN package_names P2 ON U.foreground_package_id = P2.package_id JOIN method_categories C ON U.method_category_id = C.method_category_id JOIN method_names M ON U.method_id = M.method_id JOIN cache_times CF ON U.cached_duration = CF.time_id JOIN headphones_types H ON U.headphones_id = H.headphones_id JOIN location_providers L ON U.provider_id = L.provider_id");

                        dbUsage.setVersion(SmarperUtil.db_schema_version);
                        dbUsage.setTransactionSuccessful();
                    } finally {
                        dbUsage.endTransaction();
                    }


                    populateDbUsageTables(dbUsage);

                } finally {
                    mLockUsage.writeLock().unlock();
                }


                if (context!= null)
                    setUserAppList(context, dbUsage, mLockUsage);

                resetDataUploadPointer(db, mLock);

                Log.d("Smarper-Debug", "Changing to asynchronous mode");
                try {
                    dbUsage.rawQuery("PRAGMA synchronous=OFF", null);
                } catch (Throwable ex) {
                    ex.printStackTrace();
                }

                Log.d("Smarper-Debug", "Usage database version=" + dbUsage.getVersion());

            }


        } catch (Exception e){
            //Failed
            //Retry
            Log.d("Smarper-Db", "Failed to create new database, need to retry");

        } finally {
            return dbUsage; //may be null
        }


    }


    //KO: If we delete or recreate the usage database, we need to reset the data upload pointer
    private void resetDataUploadPointer(SQLiteDatabase db, ReentrantReadWriteLock mLock){

        //SQLiteDatabase db = getDb();

        try {
            mLock.writeLock().lock();
            try {
                db.beginTransaction();
                try {

                    int rows = db.delete("setting", "name=?", new String[]{"DataUploadPointer"});
                    Log.d("Smarper-Debug", "Deleted " + rows + " from setting table");

                    if (rows == 1)
                        db.setTransactionSuccessful();
                } catch (Exception e) {
                    Log.e("Smarper-Error", "Error when trying to reset data upload pointer: ");
                    e.printStackTrace();
                }


            } finally {
                db.endTransaction();
            }

        } finally {
            mLock.writeLock().unlock();
        }

    }


    //KO
    private File backupCorruptUsageDb(String filename, boolean corrupt) {
        return new File(Environment.getDataDirectory() + File.separator + "system" + File.separator + "smarper"
                + File.separator + filename + ((corrupt) ? "-corrupt-" : "") + (new SimpleDateFormat("ddMM-hhmm")).format(new Date()) + ".gz");

    }

    //KO: Backup database (corrupt or not corrupt)
    public synchronized void backupDatabase(boolean corrupt, File dbUsageFile){
        try {

            Log.d("Smarper-Db", "Attempting to backup database");

           // File dbUsageFile = getDbUsageFile();
            File usageJournal = new File(dbUsageFile + "-journal"); //This file may not exist

            numberOfCorruptBackups = 0; //See how many are already in this directory
            String[] filenames = dbUsageFile.getParentFile().list();
            for (String s : filenames){
                if (s.contains("usage.db-corrupt"))
                    numberOfCorruptBackups++;
            }


            Log.d("Smarper-Debug", "There are currently " + numberOfCorruptBackups + " corrupt backups ");

            if (numberOfCorruptBackups < cMaxCorruptBackups) {
                for (File f : new File[]{dbUsageFile, usageJournal}) {
                    if (f.exists()) {
                        //KO: Read the file and journal into byte buffers, write out with a GzipOutputStream
                        byte[] fileBytes = new byte[8192];
                        BufferedInputStream buf = new BufferedInputStream(new FileInputStream(f));
                        GZIPOutputStream zos = new GZIPOutputStream(new FileOutputStream(backupCorruptUsageDb(f.getName(), corrupt)));

                        Log.d("Smarper-Db", "Backing up usage database " + ((corrupt) ? ", corrupt, " : "") + f.getName()); //KO: TODO: use warn instead

                        if (corrupt) {

                            numberOfCorruptBackups++;
                        }

                        int bytesRead;
                        try {
                            while ((bytesRead = buf.read(fileBytes)) != -1) { //-1 means we've read the entire file
                                zos.write(fileBytes, 0, bytesRead);
                            }
                        } finally {
                            buf.close();
                            zos.close();
                        }

                        f.delete();
                    }

                    else {
                        Log.d("Smarper-Warn", "WARNING: want to backup " + f.getPath() + " but it doesn't exist");
                    }
                }
            }
            else {
                Log.e("Smarper-Error", "Reached max number of corrupt backups, not backing up database");
            }



        } catch (Exception e) {
            Log.e("Smarper-Db", "Failed to backup corrupt usage.db: ");
            e.printStackTrace();
            Log.d("Smarper-Db", "Will recreate usage database");
            if (dbUsageFile.exists()) {
                dbUsageFile.delete();
            }

            File usageJournal = new File(dbUsageFile + "-journal");
            if (usageJournal.exists()){
                usageJournal.delete();
            }

        }
    }

    //KO
    public synchronized void checkDbErrors(SQLiteDatabase mDbUsage){
        //Use this if there are repeated errors, but the db file can respond with the version number

        if (dbErrors.size() > 5 && (new Date().getTime() - lastDbErrorTime) < 60000) { //There are at least 5 errors and the last one was no more than 1 minute ago

            error = true;
            Log.d("Smarper-Db", "DB Debug mode!");

            try {

                if (mDbUsage != null && mDbUsage.isOpen()){
                    mDbUsage.close();
                }

            } catch (Exception e) { //Error thrown in the above block
                Log.e("Smarper-Error", "Exception when attempting to recover from errors: ");
                e.printStackTrace();

            }


        }
    }

    //KO: Undo the changes made by the template. Borrowed a bit from "clear" below this
    public void clearTemplate(SQLiteDatabase smarperDb, ReentrantReadWriteLock mLock, Map<CRestriction,CRestriction> mRestrictionCache, Map <CSetting,CSetting> mSettingCache, Map<CRestriction,CRestriction> mAskedOnceCache) {

       // SQLiteDatabase smarperDb = getDb();

        try {
            mLock.writeLock().lock();
            try {
                smarperDb.beginTransaction();

                smarperDb.delete(PrivacyService.cTableSetting, "uid!=?", new String[]{"0"});
                smarperDb.delete(PrivacyService.cTableRestriction, null, null);

                smarperDb.setTransactionSuccessful();
            } finally {
                smarperDb.endTransaction();
            }

        } finally {
            mLock.writeLock().unlock();
        }

        // Clear caches
        synchronized (mRestrictionCache) {
            mRestrictionCache.clear();
        }
        synchronized (mSettingCache) {
            mSettingCache.clear();
        }
        synchronized (mAskedOnceCache) {
            mAskedOnceCache.clear();
        }
    }


}


