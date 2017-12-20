package biz.bokhorst.xprivacy;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.Log;
import android.app.ProgressDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Created by idacosta
 *
 * Creates and applies the SmarPer monitoring policy for data collection. Only a subset of apps will
 * be monitored
 *
 */

public class LcaSmarperPolicy {

    private final  String mTemplateName = "SmarperPolicy";
    private final  String mSmarperPolicyCreated = "SmarperPolicyCreated";
    private final  String mSmarperPolicyApplied = "SmarPerPolicyApplied";
    private static List<PackageInfo> matchedApps; //KO: Apps the user has of the ones from our list
    private Context mContext;
    private static List<PackageInfo> toMonitor = new ArrayList<PackageInfo>(); //KO: The apps the user selected to monitor. Gets cleared on reboot though. After that rely on getSettingBool(uid, "OnDemand", false);

    private final  List monitoredApps = Arrays.asList(
            "com.twitter.android",
            "com.whatsapp",
            "com.skype.raider",
            "kik.android",
            "com.viber.voip",
            "com.facebook.katana",
            "com.snapchat.android",
            "com.instagram.android",
            "com.yelp.android",
            "com.tripadvisor.tripadvisor",
            "com.waze",
            "com.contextlogic.wish",
            "com.walmart.android",
            "com.amazon.mShop.android.shopping",
            "com.weather.Weather",
            "com.accuweather.android",
            "com.shazam.android",
            "com.clearchannel.iheartradio.controller",
            "com.soundcloud.android",
            "com.ubercab",
            "me.lyft.android",
            "com.king.candycrushsodasaga",
            "com.supercell.clashofclans",
            "com.kiloo.subwaysurf",
            "com.ea.game.starwarscapital_row",
            "com.fitbit.FitbitMobile",
            "com.runtastic.android",
            "com.dropbox.android",
            "com.evernote"
    );

    //KO: This hashmap maps package names to app categories, and is used for recording the app category in the usage database
    private static final HashMap<String, String> appCategories;
    static
    {
        appCategories = new HashMap<String, String>();
        appCategories.put("com.twitter.android", "Social");
        appCategories.put("com.whatsapp", "Communication");
        appCategories.put("com.skype.raider", "Communication");
        appCategories.put("kik.android", "Communication");
        appCategories.put("com.viber.voip", "Communication");
        appCategories.put("com.facebook.katana", "Social");
        appCategories.put("com.snapchat.android", "Social");
        appCategories.put("com.instagram.android", "Social");
        appCategories.put("com.yelp.android", "Travel & Local");
        appCategories.put("com.tripadvisor.tripadvisor", "Travel & Local");
        appCategories.put("com.waze","Travel & Local");
        appCategories.put("com.contextlogic.wish", "Shopping");
        appCategories.put("com.walmart.android", "Shopping");
        appCategories.put("com.amazon.mShop.android.shopping", "Shopping");
        appCategories.put("com.weather.Weather", "Weather");
        appCategories.put("com.accuweather.android", "Weather");
        appCategories.put("com.shazam.android", "Music & Audio");
        appCategories.put("com.clearchannel.iheartradio.controller", "Music & Audio");
        appCategories.put("com.soundcloud.android", "Music & Audio");
        appCategories.put("com.ubercab", "Transportation");
        appCategories.put("me.lyft.android", "Transportation");
        appCategories.put("com.king.candycrushsodasaga", "Game");
        appCategories.put("com.supercell.clashofclans", "Game");
        appCategories.put("com.kiloo.subwaysurf", "Game");
        appCategories.put("com.ea.game.starwarscapital_row", "Game");
        appCategories.put("com.fitbit.FitbitMobile", "Health & Fitness");
        appCategories.put("com.runtastic.android", "Health & Fitness");
        appCategories.put("com.dropbox.android", "Productivity");
        appCategories.put("com.evernote", "Productivity");
    }

    public LcaSmarperPolicy(Context context) {
        mContext = context;
    }


    private void createTemplate(){

        PrivacyManager.setSetting(0, mTemplateName, "media", "false+asked"); //KO: Only consider 3 categories
        PrivacyManager.setSetting(0, mTemplateName, "storage", "false+ask");
        PrivacyManager.setSetting(0, mTemplateName, "location", "false+ask");
        PrivacyManager.setSetting(0, mTemplateName, "contacts", "false+ask");
        PrivacyManager.setSetting(0, mTemplateName, "accounts", "false+asked");
        PrivacyManager.setSetting(0, mTemplateName, "browser", "false+asked");
        PrivacyManager.setSetting(0, mTemplateName, "calendar", "false+asked");
        PrivacyManager.setSetting(0, mTemplateName, "calling", "false+asked");
        PrivacyManager.setSetting(0, mTemplateName, "clipboard", "false+asked");
        PrivacyManager.setSetting(0, mTemplateName, "dictionary", "false+asked");
        PrivacyManager.setSetting(0, mTemplateName, "email", "false+asked");
        PrivacyManager.setSetting(0, mTemplateName, "identification", "false+asked");
        PrivacyManager.setSetting(0, mTemplateName, "internet", "false+asked");
        PrivacyManager.setSetting(0, mTemplateName, "ipc", "false+asked");
        PrivacyManager.setSetting(0, mTemplateName, "messages", "false+asked");
        PrivacyManager.setSetting(0, mTemplateName, "network", "false+asked");
        PrivacyManager.setSetting(0, mTemplateName, "nfc", "false+asked");
        PrivacyManager.setSetting(0, mTemplateName, "notifications", "false+asked");
        PrivacyManager.setSetting(0, mTemplateName, "overlay", "false+asked");
        PrivacyManager.setSetting(0, mTemplateName, "phone", "false+asked");
        PrivacyManager.setSetting(0, mTemplateName, "sensors", "false+asked");
        PrivacyManager.setSetting(0, mTemplateName, "shell", "false+asked");
        PrivacyManager.setSetting(0, mTemplateName, "system", "false+asked");
        PrivacyManager.setSetting(0, mTemplateName, "view", "false+asked");

        //ID: No camera monitoring
//		PrivacyManager.setSetting(0,templateName, "media.Audio.startRecording", "false+asked");
//		PrivacyManager.setSetting(0,templateName, "media.MediaRecorder.setOutputFile", "false+asked");
//		PrivacyManager.setSetting(0,templateName, "media.MediaRecorder.start", "false+asked");
//		PrivacyManager.setSetting(0,templateName,"media.android.media.action.IMAGE_CAPTURE", "false+asked");
//		PrivacyManager.setSetting(0,templateName,"media.android.media.action.IMAGE_CAPTURE_SECURE", "false+asked");
//		PrivacyManager.setSetting(0,templateName,"media.android.media.action.VIDEO_CAPTURE", "false+asked");

        // Put flag in DB indicating the policy has been created
        PrivacyManager.setSetting(0, mSmarperPolicyCreated, "true");

    }

    //ID: template applied only to monitored apps
    public void applyTemplate(ProgressDialog p){

        if(!PrivacyManager.getSettingBool(0, mSmarperPolicyCreated, false)) {
            createTemplate();
        }

        // Apply template only once
       // if(!PrivacyManager.getSettingBool(0, mSmarperPolicyApplied, false)) { //KO: We now allow modifying the configuration so we don't need this if statement

            int i = 0;
            Log.d("Smarper-debug", "There are " + toMonitor.size() + " apps which will be monitored");
            for (PackageInfo pInfo : toMonitor) {
                    PrivacyManager.applyTemplate(pInfo.applicationInfo.uid, mTemplateName, null, true, true, false);
                    i++;
                    p.setProgress(i);
                    Log.d("Smarper-Debug", i + ". Applying template " + mTemplateName + " to " + pInfo.packageName + " - " + pInfo.applicationInfo.uid);

                    //KO: Must prompt for this one as well
                    PrivacyManager.setRestriction(pInfo.applicationInfo.uid, "location", "WiFi.getScanResults", false, false);
                
                }

            // Set flag in the smarper->setting table to indicate that policy has been applied
            PrivacyManager.setSetting(0, mSmarperPolicyApplied, "true");
        //}
    }

    //KO: Count number of apps the user wants to monitor, so we can display a nice progress bar
    public int countMonitoredApps(){
        return toMonitor.size();
    }

    //KO: Get the info of apps that match ones on our list
    public void computeMatchingApps(){

        PackageManager pm = mContext.getPackageManager();
        List<PackageInfo> packageInfoList = pm.getInstalledPackages(pm.GET_GIDS);
        List<PackageInfo> appMatches = new ArrayList<PackageInfo>();
        for (PackageInfo pInfo : packageInfoList) {
            if (monitoredApps.contains(pInfo.packageName)) {
                appMatches.add(pInfo);
            }
        }

        matchedApps = appMatches;
    }

    //KO: Convenience method
    public boolean isPolicyApplied(){
        return PrivacyManager.getSettingBool(0, mSmarperPolicyApplied, false);
    }

    //KO: Convenience method
    public static List<PackageInfo> getMatchedApps(){
        return matchedApps;
    }

    public static List<PackageInfo> getToMonitor(){
        return toMonitor;
    }

    //KO: Show the dialog asking which apps the user wants to monitor
    public void showAppList(final ExecutorService mExecutor){

        //KO: See which apps the user has from our list
        computeMatchingApps();

        if (matchedApps.size() <= 5){
            toMonitor.addAll(matchedApps);
            PolicyTask t = new PolicyTask();
            t.executeOnExecutor(mExecutor, null);

        } else {

            //KO: Get package manager for app names and icons
            PackageManager pm = mContext.getPackageManager();

            //KO: Copied and modified slightly from the filtering code in ActivityUsage
            //KO: Build the view here
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View view = inflater.inflate(R.layout.policy_app_list, null);

            final LinearLayout appCont = (LinearLayout) view.findViewById(R.id.app_cont);
            for (int i = 0; i < matchedApps.size(); i++) {
                //Log.d("Smarper-debug", "Adding app " + matchedApps.get(i).packageName + " to layout");
                PackageInfo app = matchedApps.get(i);
                View appItemView = inflater.inflate(R.layout.policy_app_item, null);

                //App icon
                ImageView AppIcon = (ImageView) appItemView.findViewById(R.id.app_icon);
                Drawable drawable = app.applicationInfo.loadIcon(pm);
                AppIcon.setImageDrawable(drawable);

                //App name
                CheckBox appCheckbox = (CheckBox) appItemView.findViewById(R.id.app_checkbox);
                appCheckbox.setText(app.applicationInfo.loadLabel(pm));
                appCont.addView(appItemView);

            }

            if (matchedApps.size() == 0) { //No matching apps. Show a special message
                TextView noApps = (TextView) view.findViewById(R.id.textViewNoApps);
                noApps.setText("You don't have any of the apps from our list installed. You are not eligible to participate. Please uninstall SmarPer.");
                noApps.setVisibility(View.VISIBLE);
            }

            Button okButton = (Button) view.findViewById(R.id.OKbutton);

            // Build dialog
            final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);
            alertDialogBuilder.setTitle(R.string.app_name);
            alertDialogBuilder.setView(view);
            alertDialogBuilder.setCancelable(false);
            final AlertDialog alertDialog = alertDialogBuilder.create();

            //We need a custom button with custom click listener so that the dialog does not get dismissed automatically upon button press,
            // since we need to do some verification as well.
            okButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    for (int i = 0; i < appCont.getChildCount(); i++) {
                        CheckBox app = (CheckBox) appCont.getChildAt(i).findViewById(R.id.app_checkbox);
                        //KO: Go through all of the checkboxes and see if they are checked
                        if (app.isChecked()) {
                            toMonitor.add(matchedApps.get(i));
                            //Log.d("Smarper-debug", "app number " + i + " was checked");
                        }

                    }

                    if (toMonitor.size() != 0) { //At least one was selected
                        //Log.d("Smarper-debug", "Starting policy task");
                        alertDialog.dismiss();
                        PolicyTask t = new PolicyTask();
                        t.executeOnExecutor(mExecutor, null);

                    } else { //KO: Inform the user they must select at least one app from the list
                        Toast t = Toast.makeText(mContext, "You must select at least one app to monitor.", Toast.LENGTH_LONG);
                        t.show();
                    }

                }
            });

            alertDialog.show();
        }

    }

    //KO: Task for applying the LCASmarperPolicy once
    public class PolicyTask extends AsyncTask<Void, Integer, Void> {
        private ProgressDialog mProgressDialog;

        @SuppressWarnings("deprecation")
        @Override
        protected void onPreExecute() {

            TypedArray ta = mContext.getTheme().obtainStyledAttributes(new int[] { R.attr.progress_horizontal });
            int progress_horizontal = ta.getResourceId(0, 0);
            ta.recycle();

            // Show progress dialog
            mProgressDialog = new ProgressDialog(mContext);
            mProgressDialog.setMessage(mContext.getString(R.string.msg_loading));
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            //mProgressDialog.setProgressDrawable(mContext.getResources().getDrawable(progress_horizontal));
            mProgressDialog.setProgressNumberFormat(null);
            mProgressDialog.setCancelable(false);
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.show();
        }

        @Override
        protected Void doInBackground(Void... params){

            //KO: Get count for progress bar
            int maxSize = countMonitoredApps();
            mProgressDialog.setMax(maxSize);

            //KO: Apply the policy to matched apps and update progress bar
            applyTemplate(mProgressDialog);

            return null;
        }

        @Override
        protected void onPostExecute(Void result){

            if(mProgressDialog.isShowing())
                mProgressDialog.dismiss();

            Toast status = Toast.makeText(mContext, "Configuration complete!", Toast.LENGTH_LONG);
            status.show();

        }

    }

    //KO: Reconstruct "toMonitor" object which will get cleared after a reboot
    public void repopulateAppList(){

        if (matchedApps!= null)
            matchedApps.clear();

        if (toMonitor != null)
            toMonitor.clear();

        computeMatchingApps();

        for (PackageInfo info : matchedApps){
            if (PrivacyManager.getSettingBool(info.applicationInfo.uid, PrivacyManager.cSettingOnDemand, false)){
                toMonitor.add(info);
            }
        }
    }

    //KO: Method to fetch the category for a given package name
    public static String getCategory(String packageName){
        return appCategories.get(packageName);
    }

    //KO: Convenience method for modifying the template
    public void setToMonitor(List<PackageInfo> newList){
        toMonitor = newList;
    }


}






