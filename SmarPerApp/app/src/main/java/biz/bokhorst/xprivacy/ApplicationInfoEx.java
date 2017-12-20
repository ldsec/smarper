package biz.bokhorst.xprivacy;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

@SuppressLint("DefaultLocale")
public class ApplicationInfoEx implements Comparable<ApplicationInfoEx> {
	private int mUid;
	private TreeMap<String, ApplicationInfo> mMapAppInfo = null; //KO: Didn't add this. This maps Application label to AppInfo object
	private static Map<String, PackageInfo> mMapPkgInfo = new HashMap<String, PackageInfo>(); //KO: Didn't add this. This maps package name to PackageInfo

	// Cache
	private Boolean mInternet = null;
	private Boolean mFrozen = null;
	private long mInstallTime = -1;
	private long mUpdateTime = -1;

	public static final int STATE_ATTENTION = 0;
	public static final int STATE_CHANGED = 1;
	public static final int STATE_SHARED = 2;

	public ApplicationInfoEx(Context context, int uid) {
		mUid = uid;
		mMapAppInfo = new TreeMap<String, ApplicationInfo>();
		PackageManager pm = context.getPackageManager();
		String[] packages = pm.getPackagesForUid(uid);
		if (packages != null)
			for (String packageName : packages)
				try {

					ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
					mMapAppInfo.put(pm.getApplicationLabel(appInfo).toString(), appInfo);
				} catch (NameNotFoundException ignored) {
				}
	}


	public static ArrayList<Integer> getDefaultCameraAppUids(Context context){
		PackageManager pm = context.getPackageManager();
		List<ApplicationInfo> installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

		ArrayList<Integer> systemCameraApps = new ArrayList<Integer>();

		for (ApplicationInfo appInfo: installedApps){
			if (isSystem(appInfo) && appInfo.uid > 1000){ //KO: On Samsung phone there is an app with UID 1000 which contains the word "camera test" and this causes problems
				if (appInfo.packageName.toLowerCase().contains("camera")){
					systemCameraApps.add(appInfo.uid);
					//Log.d("Smarper-debug", "Added " + appInfo.packageName + ", UID: " + appInfo.uid + " to default camera apps");
				}
			}
		}

		return systemCameraApps;
	}
	
	//KO: Added this method for getting gids
	public int[] getGids(String packageName){
		
		PackageInfo pinfo = mMapPkgInfo.get(packageName);
		if (pinfo!= null) {
			return pinfo.gids;
		}
		else{
			Log.e("Smarper-Error", "No PackageInfo object for " + packageName);
			return null;

		}



	}
	
	public static List<ApplicationInfoEx> getXApplicationList(Context context, ProgressDialog dialog) {
		// Get references
		PackageManager pm = context.getPackageManager();

		// Get app list
		SparseArray<ApplicationInfoEx> mapApp = new SparseArray<ApplicationInfoEx>();
		List<ApplicationInfoEx> listApp = new ArrayList<ApplicationInfoEx>();
		List<ApplicationInfo> listAppInfo = pm.getInstalledApplications(PackageManager.GET_META_DATA);
		if (dialog != null)
			dialog.setMax(listAppInfo.size());
		for (int app = 0; app < listAppInfo.size(); app++) {
			if (dialog != null)
				dialog.setProgress(app + 1);

			ApplicationInfo appInfo = listAppInfo.get(app);
			Util.log(null, Log.INFO, "package=" + appInfo.packageName + " uid=" + appInfo.uid);

			ApplicationInfoEx appInfoEx = new ApplicationInfoEx(context, appInfo.uid);
			if (mapApp.get(appInfoEx.getUid()) == null) {
				mapApp.put(appInfoEx.getUid(), appInfoEx);
				listApp.add(appInfoEx);
			}
		}

		// Sort result
		Collections.sort(listApp);
		return listApp;
	}




	public static List<ApplicationInfoEx> getXApplicationListImprovedPerformance(Context context, ProgressDialog dialog) {
		// Get references
		PackageManager pm = context.getPackageManager();

		// Get app list
		SparseArray<ApplicationInfoEx> mapApp = new SparseArray<ApplicationInfoEx>();
		List<ApplicationInfoEx> listApp = new ArrayList<ApplicationInfoEx>();
		List<ApplicationInfo> listAppInfo = pm.getInstalledApplications(PackageManager.GET_META_DATA);
		if (dialog != null)
			dialog.setMax(listAppInfo.size());
		for (int app = 0; app < listAppInfo.size(); app++) {
			if (dialog != null)
				dialog.setProgress(app + 1);

			ApplicationInfo appInfo = listAppInfo.get(app);
			Util.log(null, Log.INFO, "package=" + appInfo.packageName + " uid=" + appInfo.uid);

			ApplicationInfoEx appInfoEx = new ApplicationInfoEx(context, appInfo.uid);
			if (mapApp.get(appInfoEx.getUid()) == null) {
				mapApp.put(appInfoEx.getUid(), appInfoEx);
				listApp.add(appInfoEx);
			}
		}

		return listApp;
	}


	//KO: Another version of this method without progress bar and unnecessary operations
	public static List<ApplicationInfoEx> getXApplicationListQuiet(Context context) {
		// Get references
		PackageManager pm = context.getPackageManager();

		// Get app list
		SparseArray<ApplicationInfoEx> mapApp = new SparseArray<ApplicationInfoEx>();
		List<ApplicationInfoEx> listApp = new ArrayList<ApplicationInfoEx>();
		List<ApplicationInfo> listAppInfo = pm.getInstalledApplications(PackageManager.GET_META_DATA);

		//Clear package info map
		mMapPkgInfo.clear();

		for (int app = 0; app < listAppInfo.size(); app++) {
			ApplicationInfo appInfo = listAppInfo.get(app);
			try {
				getPackageInfo(context, appInfo.packageName); //KO: Call this here instead of when getting the package version name
			} catch (Exception e) {
				Log.e("Smarper-Error", "Error getting package info for " + appInfo.packageName);
				e.printStackTrace();
			}


			ApplicationInfoEx appInfoEx = new ApplicationInfoEx(context, appInfo.uid);
			if (mapApp.get(appInfoEx.getUid()) == null) {
				mapApp.put(appInfoEx.getUid(), appInfoEx);
				listApp.add(appInfoEx);
			}
		}


		return listApp;
	}

	public ArrayList<String> getApplicationName() {
		return new ArrayList<String>(mMapAppInfo.navigableKeySet());
	}

	public String getApplicationName(String packageName) {
		for (Entry<String, ApplicationInfo> entry : mMapAppInfo.entrySet())
			if (entry.getValue().packageName.equals(packageName))
				return entry.getKey();
		return "";
	}

	public List<String> getPackageName() {
		List<String> listPackageName = new ArrayList<String>();
		for (ApplicationInfo appInfo : mMapAppInfo.values())
			listPackageName.add(appInfo.packageName);
		return listPackageName;
	}

	private static void getPackageInfo(Context context, String packageName) throws NameNotFoundException {
		PackageManager pm = context.getPackageManager();
		if (mMapPkgInfo.get(packageName) != null) {
			mMapPkgInfo.remove(packageName); //remove the old entry
		}
			mMapPkgInfo.put(packageName, pm.getPackageInfo(packageName, pm.GET_GIDS)); //KO: Get gids for package

		//Log.d("Smarper-Debug", "Putting entry " + packageName + ", " + pm.getPackageInfo(packageName,0).toString() + " into package info map");
	}

	public List<String> getPackageVersionName(Context context) {
		List<String> listVersionName = new ArrayList<String>();
		for (String packageName : this.getPackageName())
			try {
				getPackageInfo(context, packageName);
				String version = mMapPkgInfo.get(packageName).versionName;
				if (version == null)
					listVersionName.add("???");
				else
					listVersionName.add(version);
			} catch (NameNotFoundException ex) {
				listVersionName.add(ex.getMessage());
			}
		return listVersionName;
	}

	//KO: A version of this method which doesn't require Context as a parameter
	public String getPackageVersionName(String packageName){

			String version = mMapPkgInfo.get(packageName).versionName;
			if (version == null)
				return "???";
			else
				return version;

	}


	public String getPackageVersionName(Context context, String packageName) {
		try {
			getPackageInfo(context, packageName);
			String version = mMapPkgInfo.get(packageName).versionName;
			if (version == null)
				return "???";
			else
				return version;
		} catch (NameNotFoundException ex) {
			return ex.getMessage();
		}
	}

	public List<Integer> getPackageVersionCode(Context context) {
		List<Integer> listVersionCode = new ArrayList<Integer>();
		for (String packageName : this.getPackageName())
			try {
				getPackageInfo(context, packageName);
				listVersionCode.add(mMapPkgInfo.get(packageName).versionCode);
			} catch (NameNotFoundException ex) {
				listVersionCode.add(0);
			}
		return listVersionCode;
	}

	public Drawable getIcon(Context context) {
		// Pick first icon
		if (mMapAppInfo.size() > 0)
			return mMapAppInfo.firstEntry().getValue().loadIcon(context.getPackageManager());
		else
			return new ColorDrawable(Color.TRANSPARENT);
	}

	public Bitmap getIconBitmap(Context context) {
		if (mMapAppInfo.size() > 0) {
			try {
				final ApplicationInfo appInfo = mMapAppInfo.firstEntry().getValue();
				if (appInfo.icon == 0)
					appInfo.icon = android.R.drawable.sym_def_app_icon;
				final Resources resources = context.getPackageManager().getResourcesForApplication(appInfo);

				final BitmapFactory.Options options = new BitmapFactory.Options();
				options.inJustDecodeBounds = true;
				BitmapFactory.decodeResource(resources, appInfo.icon, options);

				final int pixels = Math.round(Util.dipToPixels(context, 48));
				options.inSampleSize = Util.calculateInSampleSize(options, pixels, pixels);
				options.inJustDecodeBounds = false;
				return BitmapFactory.decodeResource(resources, appInfo.icon, options);
			} catch (NameNotFoundException ex) {
				Util.bug(null, ex);
				return null;
			}
		} else
			return null;
	}

	public boolean hasInternet(Context context) {
		if (mInternet == null) {
			mInternet = false;
			PackageManager pm = context.getPackageManager();
			for (ApplicationInfo appInfo : mMapAppInfo.values())
				if (pm.checkPermission("android.permission.INTERNET", appInfo.packageName) == PackageManager.PERMISSION_GRANTED) {
					mInternet = true;
					break;
				}
		}
		return mInternet;
	}

	public boolean isFrozen(Context context) {
		if (mFrozen == null) {
			PackageManager pm = context.getPackageManager();
			boolean enabled = false;
			for (ApplicationInfo appInfo : mMapAppInfo.values())
				try {
					int setting = pm.getApplicationEnabledSetting(appInfo.packageName);
					enabled = (enabled || setting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
					enabled = (enabled || setting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
					if (enabled)
						break;
				} catch (IllegalArgumentException ignored) {
				}
			mFrozen = !enabled;
		}
		return mFrozen;
	}

	public int getUid() {
		return mUid;
	}

	@SuppressLint("FieldGetter")
	public int getState(Context context) {
		return Integer.parseInt(PrivacyManager.getSetting(-getUid(), PrivacyManager.cSettingState,
				Integer.toString(STATE_CHANGED)));
	}

	public long getInstallTime(Context context) {
		if (mInstallTime == -1) {
			long now = System.currentTimeMillis();
			mInstallTime = now;
			for (String packageName : this.getPackageName())
				try {
					getPackageInfo(context, packageName);
					long time = mMapPkgInfo.get(packageName).firstInstallTime;
					if (time < mInstallTime)
						mInstallTime = time;
				} catch (NameNotFoundException ex) {
				}
			if (mInstallTime == now)
				// no install time, so assume it is old
				mInstallTime = 0;
		}
		return mInstallTime;
	}

	public long getUpdateTime(Context context) {
		if (mUpdateTime == -1) {
			mUpdateTime = 0;
			for (String packageName : this.getPackageName())
				try {
					getPackageInfo(context, packageName);
					long time = mMapPkgInfo.get(packageName).lastUpdateTime;
					if (time > mUpdateTime)
						mUpdateTime = time;
				} catch (NameNotFoundException ex) {
				}
		}
		return mUpdateTime;
	}

	@SuppressLint("FieldGetter")
	public long getModificationTime(Context context) {
		return Long.parseLong(PrivacyManager.getSetting(-getUid(), PrivacyManager.cSettingModifyTime, "0"));
	}

	public boolean isSystem() {
		boolean mSystem = false;
		for (ApplicationInfo appInfo : mMapAppInfo.values()) {
			mSystem = ((appInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0);
			mSystem = mSystem || appInfo.packageName.equals(this.getClass().getPackage().getName());
			mSystem = mSystem || appInfo.packageName.equals(this.getClass().getPackage().getName() + ".pro");
			mSystem = mSystem || appInfo.packageName.equals("de.robv.android.xposed.installer");
		}
		return mSystem;
	}

	private static boolean isSystem(ApplicationInfo appInfo){
		boolean system = false;
		system = ((appInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0);

		return system;
	}

	public boolean isShared() {
		for (ApplicationInfo appInfo : mMapAppInfo.values())
			if (PrivacyManager.isShared(appInfo.uid))
				return true;
		return false;
	}

	public boolean isIsolated() {
		for (ApplicationInfo appInfo : mMapAppInfo.values())
			if (PrivacyManager.isIsolated(appInfo.uid))
				return true;
		return false;
	}

	@Override
	@SuppressLint("FieldGetter")
	public String toString() {
		return String.format("%d %s", getUid(), TextUtils.join(", ", getApplicationName()));
	}

	@Override
	public int compareTo(ApplicationInfoEx other) {
		// Locale respecting sorter
		Collator collator = Collator.getInstance(Locale.getDefault());
		return collator.compare(TextUtils.join(", ", getApplicationName()),
				TextUtils.join(", ", other.getApplicationName()));
	}
}
