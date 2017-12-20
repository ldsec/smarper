package biz.bokhorst.xprivacy;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;


// Class created by JS
public class ActivityClearCache extends AppCompatActivity {
    public List<String> appNamesForListView = new ArrayList<String>();
    private static String debugTagForClearCache = "smarper-ClearCache";
    private List<PackageInfo> monitoredApps = null;
    private PackageManager pm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check privacy service client
        if (!PrivacyService.checkClient())
            return;

        optionClearCache();
    }

    private void optionClearCache() {
        setContentView(R.layout.activity_clear_cache);

        monitoredApps = LcaSmarperPolicy.getToMonitor(); //KO: Get monitored apps chosen by user
        pm = this.getPackageManager(); //KO: Get PM for app names and icons

        //Create an arraylist of the app names for the listview
        for (PackageInfo i : monitoredApps){
            appNamesForListView.add(i.applicationInfo.loadLabel(pm).toString());
        }

        ListView listView = (ListView) findViewById(R.id.lvApps);
        CustomListViewAdapter adapter = new CustomListViewAdapter(this, R.layout.clearcache_app_item, appNamesForListView);
        listView.setAdapter(adapter);

        //JS: This method clears the cache of the chosen (clicked) application
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {                  //JS

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                TextView appName = (TextView) view.findViewById(R.id.AppName);
                String app_Name = appName.getText().toString();

                int numberOfRemovedEntries = ClearCacheExecution(monitoredApps.get(position).applicationInfo.uid);
                Log.d(debugTagForClearCache, "We cleared the cache of Application " + app_Name + " by " + numberOfRemovedEntries + " entries");

                new AlertDialog.Builder(ActivityClearCache.this)
                        .setTitle("Clearing the Cache")
                        .setMessage("You Cleared the Cache of " + app_Name + " by " + numberOfRemovedEntries + " entries.")
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setIcon(monitoredApps.get(position).applicationInfo.loadIcon(pm))
                        .show();
            }
        });

    }

    private int ClearCacheExecution(int uid) {
        return PrivacyManager.ClearCacheOfSelectedApp(uid);
    }


    public class CustomListViewAdapter extends ArrayAdapter<String> {
        Context context;

        public CustomListViewAdapter(Context context, int resourceId, List<String> items) {
            super(context, resourceId, items);
            this.context = context;
        }

        private class ViewHolder {
            ImageView app_icon;
            TextView AppName;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder = null;

            LayoutInflater mInflater = (LayoutInflater) context
                    .getSystemService(ActivityClearCache.LAYOUT_INFLATER_SERVICE);
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.clearcache_app_item, null);
                holder = new ViewHolder();
                holder.AppName = (TextView) convertView.findViewById(R.id.AppName);
                holder.app_icon = (ImageView) convertView.findViewById(R.id.app_icon);
                convertView.setTag(holder);
            } else
                holder = (ViewHolder) convertView.getTag();

            holder.AppName.setText(appNamesForListView.get(position));
            Drawable icon = monitoredApps.get(position).applicationInfo.loadIcon(pm);
            holder.app_icon.setImageDrawable(icon);
            return convertView;
        }
    }
}