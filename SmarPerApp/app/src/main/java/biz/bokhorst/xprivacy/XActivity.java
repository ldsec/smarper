package biz.bokhorst.xprivacy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

public class XActivity extends XHook {
	private Methods mMethod;
	private String mActionName;
	private boolean forbiddenPathsInitialized = false;

	private Uri dcimURI = null;
	private Uri moviesURI = null;
	private Uri musicURI = null;
	private Uri picturesURI = null;

	private void initializeForbiddenPaths(){

		dcimURI = Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM));
		moviesURI = Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES));
		musicURI = Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC));
		picturesURI = Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES));

		forbiddenPathsInitialized = true;

	}

	private XActivity(Methods method, String restrictionName, String actionName) {
		super(restrictionName, method.name(), actionName);
		mMethod = method;
		mActionName = actionName;
	}

	public String getClassName() {
		return "android.app.Activity";
	}

	// @formatter:off

	// public Object getSystemService(String name)
	// public void startActivities(Intent[] intents)
	// public void startActivities(Intent[] intents, Bundle options)
	// public void startActivity(Intent intent)
	// public void startActivity(Intent intent, Bundle options)
	// public void startActivityForResult(Intent intent, int requestCode)
	// public void startActivityForResult(Intent intent, int requestCode, Bundle options)
	// public void startActivityFromChild(Activity child, Intent intent, int requestCode)
	// public void startActivityFromChild(Activity child, Intent intent, int requestCode, Bundle options)
	// public void startActivityFromFragment(Fragment fragment, Intent intent, int requestCode)
	// public void startActivityFromFragment(Fragment fragment, Intent intent, int requestCode, Bundle options)
	// public boolean startActivityIfNeeded(Intent intent, int requestCode)
	// public boolean startActivityIfNeeded(Intent intent, int requestCode, Bundle options)
	// public boolean startNextMatchingActivity(Intent intent)
	// public boolean startNextMatchingActivity(Intent intent, Bundle options)
	// frameworks/base/core/java/android/app/Activity.java

	// @formatter:on

	// @formatter:off
	private enum Methods {
		getSystemService,
		startActivities, startActivity, startActivityForResult, startActivityFromChild, startActivityFromFragment, startActivityIfNeeded, startNextMatchingActivity
	};
	// @formatter:on

	@SuppressLint("InlinedApi")
	public static List<XHook> getInstances() {
		List<XHook> listHook = new ArrayList<XHook>();
		listHook.add(new XActivity(Methods.getSystemService, null, null));

		List<Methods> startMethods = new ArrayList<Methods>(Arrays.asList(Methods.values()));
		startMethods.remove(Methods.getSystemService);

		// Intent send: browser
		for (Methods activity : startMethods)
			listHook.add(new XActivity(activity, PrivacyManager.cView, Intent.ACTION_VIEW));

		// Intent send: call/dial
		for (Methods activity : startMethods) {
			listHook.add(new XActivity(activity, PrivacyManager.cCalling, Intent.ACTION_CALL));
			listHook.add(new XActivity(activity, PrivacyManager.cCalling, Intent.ACTION_DIAL));
		}

		// Intent send: media
		for (Methods activity : startMethods) {
			listHook.add(new XActivity(activity, PrivacyManager.cMedia, MediaStore.ACTION_IMAGE_CAPTURE));
			listHook.add(new XActivity(activity, PrivacyManager.cMedia, MediaStore.ACTION_IMAGE_CAPTURE_SECURE));
			listHook.add(new XActivity(activity, PrivacyManager.cMedia, MediaStore.ACTION_VIDEO_CAPTURE));
		}

		return listHook;
	}

	@Override
	@SuppressLint("DefaultLocale")
	protected void before(XParam param) throws Throwable {
		// Get intent(s)
		Intent[] intents = null;
		switch (mMethod) {
		case getSystemService:
			// Do nothing
			break;

		case startActivity:
		case startActivityForResult:
		case startActivityIfNeeded:
		case startNextMatchingActivity:
			if (param.args.length > 0 && param.args[0] instanceof Intent)
				intents = new Intent[] { (Intent) param.args[0] };
			break;

		case startActivityFromChild:
		case startActivityFromFragment:
			if (param.args.length > 1 && param.args[1] instanceof Intent)
				intents = new Intent[] { (Intent) param.args[1] };
			break;

		case startActivities:
			if (param.args.length > 0 && param.args[0] instanceof Intent[])
				intents = (Intent[]) param.args[0];
			break;
		}

		// Process intent(s)
		if (intents != null)
			for (Intent intent : intents) {
				if (mActionName.equals(intent.getAction())) {
					boolean restricted = false;
					if (mActionName.equals(Intent.ACTION_VIEW)) { //KO: forbid files on SD card here too!
						Uri uri = intent.getData();
						if (uri != null) {
							//if (isRestrictedExtra(param, mActionName, uri.toString()))
							//restricted = true;
							//Log.d("Smarper-Debug", "XActivity: got ACTION_VIEW intent: " +  uri.toString());
							switch (getRestrictedExtra(param, mActionName, uri.toString())) {

								case 0:
									restricted = false;
									break;
								case 1:

									if (!forbiddenPathsInitialized)
										initializeForbiddenPaths();

									if (uri.toString().startsWith(moviesURI.toString()) || uri.toString().startsWith(dcimURI.toString()) || uri.toString().startsWith(picturesURI.toString()) || uri.toString().startsWith(musicURI.toString())) {
										restricted = true;

									}
									break;
								case 2:
									restricted = true;
									break;
							}


						}
					} else if (mActionName.equals(MediaStore.ACTION_IMAGE_CAPTURE)) { //KO (this entire section)
						int restrictState = getRestrictState(param, mActionName);
						Bundle extras = intent.getExtras();
						String action = intent.getAction();
						Uri data = intent.getData();

						String extras_str = "";
						for (String key : extras.keySet()) {
							Object item = extras.get(key);

							if (item != null) {
								extras_str += key + ": " + item.toString() + ", ";
							}

						}

						//Log.d("Smarper-Debug", action + ", " + data + ", extras: " + extras_str);


					} else
						restricted = isRestricted(param, mActionName);

					if (restricted) {
						if (mMethod == Methods.startActivityIfNeeded)
							param.setResult(true);
						else
							param.setResult(null);
						return;
					}
				}
			}
	}

	@Override
	protected void after(XParam param) throws Throwable {
		if (mMethod == Methods.getSystemService)
			if (param.args.length > 0 && param.args[0] instanceof String && param.getResult() != null) {
				String name = (String) param.args[0];
				Object instance = param.getResult();
				XPrivacy.handleGetSystemService(name, instance.getClass().getName(), getSecret());
			}
	}
	

}
