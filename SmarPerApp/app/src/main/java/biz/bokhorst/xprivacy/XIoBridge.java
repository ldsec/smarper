package biz.bokhorst.xprivacy;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.os.Binder;
import android.os.Environment;
import android.os.Process;
import android.os.storage.StorageManager;
import android.text.TextUtils;
import android.util.Log;

public class XIoBridge extends XHook {
	private Methods mMethod;
	private String mFileName;

	private static String mExternalStorage = null;
	private static String mEmulatedSource = null;
	private static String mEmulatedTarget = null;
	private static String mMediaStorage = null;
	private static String mSecondaryStorage = null;

	//TODO: move these to PrivacyManager or something, since they're shared between XIOBridge and XContentResolver
	/*private static String picturesPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath(); //.getAbsolutePath(); //KO
	private static String moviesPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getPath(); //.getAbsolutePath(); //KO
	private static String musicPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath(); //KO
	private static String dcimPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();  //KO
	*/
	private ArrayList<String> storagePrefixes = new ArrayList<String>();

	/*private void updatePublicDirs(){
		try{
			picturesPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath(); //.getAbsolutePath(); //KO
			moviesPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getPath(); //.getAbsolutePath(); //KO
			musicPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath(); //KO
			dcimPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();  //KO
		} catch (Exception e){
			Log.d("Smarper-Error", "Exception trying tp update storage directories: ");
			e.printStackTrace();
		}
	}*/


	private XIoBridge(Methods method, String restrictionName) {
		super(restrictionName, method.name(), null);
		mMethod = method;
		mFileName = null;
	}

	private XIoBridge(Methods method, String restrictionName, String fileName) {
		super(restrictionName, method.name(), fileName);
		mMethod = method;
		mFileName = fileName;
	}

	public String getClassName() {
		return "libcore.io.IoBridge";
	}

	// @formatter:off

	// public static void connect(FileDescriptor fd, InetAddress inetAddress, int port) throws SocketException
	// public static void connect(FileDescriptor fd, InetAddress inetAddress, int port, int timeoutMs) throws SocketException, SocketTimeoutException
	// public static FileDescriptor open(String path, int flags) throws FileNotFoundException
	// public static FileDescriptor socket(boolean stream) throws SocketException
	// https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/os/Environment.java
	// https://android.googlesource.com/platform/libcore/+/android-5.0.1_r1/luni/src/main/java/libcore/io/IoBridge.java

	// @formatter:on

	private enum Methods {
		open, connect
	};

	public static List<XHook> getInstances() {
		List<XHook> listHook = new ArrayList<XHook>();
		listHook.add(new XIoBridge(Methods.connect, PrivacyManager.cInternet));
		listHook.add(new XIoBridge(Methods.open, PrivacyManager.cStorage));
		listHook.add(new XIoBridge(Methods.open, PrivacyManager.cIdentification, "/proc"));
		listHook.add(new XIoBridge(Methods.open, PrivacyManager.cIdentification, "/system/build.prop"));
		listHook.add(new XIoBridge(Methods.open, PrivacyManager.cIdentification, "/sys/block/.../cid"));
		listHook.add(new XIoBridge(Methods.open, PrivacyManager.cIdentification, "/sys/class/.../cid"));
		return listHook;
	}

	@Override
	@SuppressLint("SdCardPath")
	protected void before(XParam param) throws Throwable {
		if (mMethod == Methods.connect) {
			if (param.args.length > 2 && param.args[1] instanceof InetAddress && param.args[2] instanceof Integer) {
				InetAddress address = (InetAddress) param.args[1];
				int port = (Integer) param.args[2];

				String hostName;
				int uid = Binder.getCallingUid();
				boolean resolve = PrivacyManager.getSettingBool(uid, PrivacyManager.cSettingResolve, false);
				boolean noresolve = PrivacyManager.getSettingBool(-uid, PrivacyManager.cSettingNoResolve, false);
				if (resolve && !noresolve)
					try {
						hostName = address.getHostName();
					} catch (Throwable ignored) {
						hostName = address.toString();
					}
				else
					hostName = address.toString();

				if (isRestrictedExtra(param, hostName + ":" + port))
					param.setThrowable(new SocketException("XPrivacy"));
			}

		} else if (mMethod == Methods.open) {
			if (param.args.length > 0) {
				String fileName = (String) param.args[0];
				if (mFileName == null && fileName != null) {
					// Get storage folders
					if (mExternalStorage == null) {
						mExternalStorage = System.getenv("EXTERNAL_STORAGE");
						mEmulatedSource = System.getenv("EMULATED_STORAGE_SOURCE");
						mEmulatedTarget = System.getenv("EMULATED_STORAGE_TARGET");
						mMediaStorage = System.getenv("MEDIA_STORAGE");
						mSecondaryStorage = System.getenv("SECONDARY_STORAGE");
						if (TextUtils.isEmpty(mMediaStorage))
							mMediaStorage = "/data/media";
						//Log.d("Smarper-Debug", "external storage: " + mExternalStorage + ", emulated source: " + mEmulatedSource + ", emulated target: " + mEmulatedTarget + ", media storage: " + mMediaStorage);
					}

					// Check storage folders

					//KO: update to support 3 states
					if (fileName.startsWith("/sdcard") || (mMediaStorage != null && fileName.startsWith(mMediaStorage))
							|| (mExternalStorage != null && fileName.startsWith(mExternalStorage))
							|| (mEmulatedSource != null && fileName.startsWith(mEmulatedSource))
							|| (mEmulatedTarget != null && fileName.startsWith(mEmulatedTarget))
							|| (mSecondaryStorage != null && fileName.startsWith(mSecondaryStorage))){ //KO: Secondary storage: 3.6.19

						//KO: TODO: potential improvement: make robust for multiple users (i.e. get user id dynamically instead of assuming 0)
						//Log.d("Smarper-Debug", "Checking if " + fileName + " is restricted");
						switch(getRestrictStateExtra(param, fileName)){//if (isRestrictedExtra(param, fileName))
						case 0:
							//Log.d("Smarper-Storage", "XIOBridge: Allowing " + fileName);
							break;
						case 1:
							//If the path is to public directories, throw file not found exception
							if (fileName.startsWith("/sdcard")){
								// /sdcard/Pictures/ etc
								if(fileName.startsWith("/sdcard/Pictures") || fileName.startsWith("/sdcard/Movies") || fileName.startsWith("/sdcard/DCIM") || fileName.startsWith("/sdcard/Music"))
									param.setThrowable(new FileNotFoundException("XPrivacy"));
								/*else
									Log.d("Smarper-Storage", "XIOBridge: " + fileName + " not in public directories list! Allowed!");*/
							}

							else if (mEmulatedTarget != null && fileName.startsWith(mEmulatedTarget)){
								// /storage/emulated/0/Pictures
								if(fileName.startsWith(mEmulatedTarget + "/0/Pictures") || fileName.startsWith(mEmulatedTarget + "/0/Movies") || fileName.startsWith(mEmulatedTarget + "/0/DCIM") || fileName.startsWith(mEmulatedTarget + "/0/Music"))
									param.setThrowable(new FileNotFoundException("XPrivacy"));
								/*else
									Log.d("Smarper-Storage", "XIOBridge: " + fileName + " not in public directories list! Allowed!");*/
							}

							else if (mMediaStorage != null && fileName.startsWith(mMediaStorage)){
								// /data/media/0/Pictures
								if(fileName.startsWith(mMediaStorage + "/0/Pictures") || fileName.startsWith(mMediaStorage + "/0/Movies") || fileName.startsWith(mMediaStorage + "/0/DCIM") || fileName.startsWith(mMediaStorage + "/0/Music"))
									param.setThrowable(new FileNotFoundException("XPrivacy"));
								/*else
									Log.d("Smarper-Storage", "XIOBridge: " + fileName + " not in public directories list! Allowed!");*/

							}




							else {
								//Log.d("Smarper-Storage", "XIOBridge: Got other filename - " + fileName + ", not obfuscated!");
								//Log.d("Smarper-Storage", "Public dirs are: " + picturesPublicDir + ", " + musicPublicDir + ", " + moviesPublicDir + ", " + dcimPublicDir);
							}


							break;
						case 2:
							//Log.d("Smarper-Storage", "XIOBridge: Denying " + fileName);
							param.setThrowable(new FileNotFoundException("XPrivacy"));
							break;
						
						}
						
					
					}

				} else if (fileName.startsWith(mFileName) || mFileName.contains("...")) {
					// Zygote, Android
					if (Util.getAppId(Process.myUid()) == Process.SYSTEM_UID)
						return;

					// Proc white list
					if (mFileName.equals("/proc"))
						if ("/proc/self/cmdline".equals(fileName))
							return;

					// Check if restricted
					if (mFileName.contains("...")) {
						String[] component = mFileName.split("\\.\\.\\.");
						if (fileName.startsWith(component[0]) && fileName.endsWith(component[1]))
							if (isRestricted(param, mFileName))
								param.setThrowable(new FileNotFoundException("XPrivacy"));

					} else if (mFileName.equals("/proc")) {
						if (isRestrictedExtra(param, mFileName, fileName))
							param.setThrowable(new FileNotFoundException("XPrivacy"));

					} else {
						if (isRestricted(param, mFileName))
							param.setThrowable(new FileNotFoundException("XPrivacy"));
					}
				}
			}

		} else
			Util.log(this, Log.WARN, "Unknown method=" + param.method.getName());
	}

	@Override
	protected void after(XParam param) throws Throwable {
		// Do nothing
	}
}
