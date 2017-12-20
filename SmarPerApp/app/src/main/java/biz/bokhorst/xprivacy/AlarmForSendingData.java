package biz.bokhorst.xprivacy;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;


// Class created by JS

public class AlarmForSendingData extends BroadcastReceiver
{
    private static String debugTagForAlarm = "smarper-alarm";

    @Override
    public void onReceive(Context context, Intent intent)
    {
        Bundle extras = intent.getExtras();
        int alarmTypeFlag = extras.getInt("NEW FLAG");         //JS: Pull out the Flag from the intent to know which alarm has been triggered

        Log.d(debugTagForAlarm, "Entered to Main Alarm Execution " + alarmTypeFlag);    //JS: if alarmTypeFlag is 0, then it is global alarm execution, else if it is 1, it is the back up alarm execution

        if (alarmTypeFlag == 0) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");
            wl.acquire();

            //JS: Checking WIFI Connectivity
            boolean connected = false;
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState() == NetworkInfo.State.CONNECTED) {
                //JS: We are connected to a network
                connected = true;
            }
            else connected = false;

            NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();          //JS: Checking Internet Access

            if (connected == true && netInfo != null && netInfo.isConnected()) {

                //JS: Create object of SharedPreferences and extract from it the last updated value of UploadDataURL
                //JS: if the initial value of UploadDataURL is null, we automatically set it to https://spism.epfl.ch/smarper/uploadData.php (our server)
                SharedPreferences mPrefs = context.getSharedPreferences("SaveURL", 0);
                //JS: Just before uploading data to server, make sure that PrivacyService.UploadDataURL has the most recent value for the server URL
                Smarper.UploadDataURL = mPrefs.getString("UploadDataURL","https://spism.epfl.ch/smarper/uploadData.php");

                new Smarper.SendDataToServer().execute(context);
            }

            else {
                Log.d(debugTagForAlarm, "Entered to Main Alarm, Not Connected To WIFI, Set Back Up Alarm, " + Smarper.NumberofTimesforBackupAlarm);
                AlarmForSendingData alarm2 = new AlarmForSendingData();                     //JS: Set Back Up Alarm
                alarm2.SetAlarm(context, 1);
            }
            wl.release();
        }

        else if (alarmTypeFlag == 1){
            Smarper.NumberofTimesforBackupAlarm ++;

            //JS: Checking WIFI Connectivity
            boolean connected = false;

            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState() == NetworkInfo.State.CONNECTED) {
                //JS: we are connected to a network
                connected = true;
            }
            else connected = false;

            NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();          //JS: Checking Internet Access

            if (connected == true && netInfo != null && netInfo.isConnected()) {

                //JS: Create object of SharedPreferences and extract from it the last updated value of UploadDataURL
                //JS: if the initial value of UploadDataURL is null, we automatically set it to https://spism.epfl.ch/smarper/uploadData.php (our server)
                SharedPreferences mPrefs = context.getSharedPreferences("SaveURL", 0);
                //JS: Just before uploading data to server, make sure that PrivacyService.UploadDataURL has the most recent value for the server URL
                Smarper.UploadDataURL = mPrefs.getString("UploadDataURL","https://spism.epfl.ch/smarper/uploadData.php");

                new Smarper.SendDataToServer().execute(context);

            }

            else if (Smarper.NumberofTimesforBackupAlarm <= Smarper.MaxNumberofTimesforBackupAlarm) {
                Log.d(debugTagForAlarm, "Entered to Back Up Alarm, Not Connected To WIFI, Reset Back Up Alarm, " + Smarper.NumberofTimesforBackupAlarm);
                AlarmForSendingData alarm2 = new AlarmForSendingData();                     //JS: ReSet BAck Up Alarm
                alarm2.SetAlarm(context, 1);
            }

            else {
                Log.d(debugTagForAlarm, "Entered to Back Up Alarm, Not Connected To WIFI, Cancel Back Up Alarm, " + Smarper.NumberofTimesforBackupAlarm);
                Smarper.NumberofTimesforBackupAlarm = 0;
            }
        }

    }

    public void SetAlarm(Context context, int FlagToDifferentiateAlarms)          //JS: if FlagToDifferentiateAlarms is 0, then it is global alarm setting, else if it is 1, it is the back up alarm setting
    {
        Log.d(debugTagForAlarm, "Entered to Alarm Setting " + FlagToDifferentiateAlarms);

        if (FlagToDifferentiateAlarms == 0){
            SimpleDateFormat sdf = new SimpleDateFormat("k");
            Date d = new Date();
            d.getTime();                          //JS: Get current hour of the day
            String hourofDay = sdf.format(d);
            int hourOfDay = Integer.parseInt(hourofDay);

            sdf = new SimpleDateFormat("m");
            d = new Date();
            d.getTime();                            //JS: Get current minute of the hour of the day
            String minuteofHour = sdf.format(d);
            int minuteOfHour = Integer.parseInt(minuteofHour);

            AlarmManager am =( AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent("biz.bokhorst.xprivacy.START_ALARM");
            i.putExtra("NEW FLAG", 0);                    //JS: Push New Flag to the intent to differentiate alarms (know which alarm has been triggered)
            PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
            if (hourOfDay < Smarper.UploadTimeHour) {
                am.setRepeating(AlarmManager.RTC_WAKEUP, d.getTime() + /*((PrivacyService.UploadTimeHour - hourOfDay) * 60L - minuteOfHour)*/ 1* 60L * 1000L, Smarper.UploadPeriodinMinutes * 60L * 1000L, pi); //JS: Hours * Minutes * Seconds * Millisecs
                Log.d(debugTagForAlarm, "Alarm set to go off in " + ((Smarper.UploadTimeHour - hourOfDay) * 60L - minuteOfHour) + " minutes");
            }
            else {
                am.setRepeating(AlarmManager.RTC_WAKEUP, d.getTime() + ((24 - hourOfDay + Smarper.UploadTimeHour) * 60L - minuteOfHour) * 60L * 1000L, Smarper.UploadPeriodinMinutes * 60L * 1000L, pi); //JS: Hours * Minutes * Seconds * Millisecs
                Log.d(debugTagForAlarm, "Alarm set to go off in " + ((24 - hourOfDay + Smarper.UploadTimeHour) * 60L - minuteOfHour) + " minutes");
            }
        }

        else {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent("biz.bokhorst.xprivacy.START_ALARM");
            i.putExtra("NEW FLAG", 1);                      //JS: Push New Flag to the intent to differentiate alarms (know which alarm has been triggered)
            PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
            am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + Smarper.PeriodicCheckingTimeForBackupAlarm * 60L * 1000L, pi); //JS: Minutes * Seconds * Millisecs
        }
    }

    public void CancelAlarm(Context context)
    {
        Intent intent = new Intent(context, AlarmForSendingData.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(sender);
    }
}