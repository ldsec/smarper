package biz.bokhorst.xprivacy;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by olejnik on 21/02/16.
 */
public class ActivityConfiguration extends AppCompatActivity {

    PackageManager pm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.policy_app_list);

        pm = getPackageManager();
        final LcaSmarperPolicy p = new LcaSmarperPolicy(ActivityConfiguration.this);

        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        p.computeMatchingApps();
        final List<PackageInfo> matchedApps = LcaSmarperPolicy.getMatchedApps(); //KO: What to show in the list
        List<PackageInfo> monitoredApps = LcaSmarperPolicy.getToMonitor(); //KO: Which boxes should be checked
        List<String> matchedAppsNames = new ArrayList<String>();
        List<String> monitoredAppsNames = new ArrayList<String>();

        //KO: Lists of names to compare them since PackageInfo doesn't implement equals and we can't use List.contains!
        for (PackageInfo info : monitoredApps){
            monitoredAppsNames.add(info.packageName);
        }

        Log.d("Smarper-debug", "User has " + matchedApps.size() + " matching apps, of which " + monitoredApps.size() + " are monitored");

        //KO: Same layout as when displaying the dialog fragment for user's first time choice, could probably be refactored to have the code only in one place
        final LinearLayout appCont = (LinearLayout) findViewById(R.id.app_cont);
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
            String appName = app.applicationInfo.loadLabel(pm).toString();
            appCheckbox.setText(appName);
            if (monitoredAppsNames.contains(app.packageName))
                appCheckbox.setChecked(true);
            appCont.addView(appItemView);

            Button okButton = (Button) findViewById(R.id.OKbutton);

            final List<PackageInfo> newConfig = new ArrayList<PackageInfo>();

            okButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    for (int i = 0; i < appCont.getChildCount(); i++) {
                        CheckBox app = (CheckBox) appCont.getChildAt(i).findViewById(R.id.app_checkbox);
                        //KO: Go through all of the checkboxes and see if they are checked
                        if (app.isChecked()) {
                            newConfig.add(matchedApps.get(i));
                            //Log.d("Smarper-debug", "app number " + i + " was checked");
                        }

                    }

                    if (newConfig.size() != 0) { //At least one was selected

                        //Clear the current config
                        try {
                            PrivacyService.getClient().clearTemplate();
                        } catch (RemoteException e){
                            Log.e("Smarper-Error", "Error when clearing the template!");
                            e.printStackTrace();
                        }

                        //Apply new config
                        p.setToMonitor(newConfig);
                        LcaSmarperPolicy.PolicyTask t = p.new PolicyTask();
                        t.execute((Void)null);


                    } else { //KO: Inform the user they must select at least one app from the list
                        Toast t = Toast.makeText(ActivityConfiguration.this, "You must select at least one app to monitor.", Toast.LENGTH_LONG);
                        t.show();
                    }

                }
            });
        }

    }




}
