package biz.bokhorst.xprivacy;

import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.content.SyncAdapterType;
import android.content.SyncInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.webkit.MimeTypeMap;

public class XContentResolver extends XHook {
	private Methods mMethod;
	private boolean mClient;
	private String mClassName;
	private ArrayList<String> contactsMethods;
	private ArrayList<String> allowedMimetypes_contacts;


	private XContentResolver(Methods method, String restrictionName, String className) {
		super(restrictionName, method.name().replace("Srv_", ""), method.name());
		mMethod = method;
		if (className == null)
			mClassName = "com.android.server.content.ContentService";
		else
			mClassName = className;
	}

	private XContentResolver(Methods method, String restrictionName, boolean client) {
		super(restrictionName, method.name(), null);
		mMethod = method;
		mClient = client;
		mClassName = null;
	}

	public String getClassName() {
		if (mClassName == null)
			return (mClient ? "android.content.ContentProviderClient" : "android.content.ContentResolver");
		else
			return mClassName;
	}


	private void setupContactsMethods(){
		
		contactsMethods = new ArrayList<String>();
		contactsMethods.add("contacts/contacts");
		contactsMethods.add("contacts/data");
		contactsMethods.add("contacts/people");
		contactsMethods.add("contacts/phone_lookup");
		contactsMethods.add("contacts/profile");
		contactsMethods.add("contacts/raw_contacts");
		contactsMethods.add("ContactsProvider2");
		contactsMethods.add("IccProvider");
		contactsMethods.add("contacts/groups");
		contactsMethods.add("contacts/groups_summary");

		//TODO: potential improvement: dynamically get this setting from xprivacy.db, can allow user to configure datatypes to obfuscate via GUI
		allowedMimetypes_contacts = new ArrayList<String>();
		allowedMimetypes_contacts.add(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE);
		allowedMimetypes_contacts.add(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
		allowedMimetypes_contacts.add(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
		allowedMimetypes_contacts.add(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE);

	}
	
	// @formatter:off

	// public static SyncInfo getCurrentSync()
	// static List<SyncInfo> getCurrentSyncs()
	// static SyncAdapterType[] getSyncAdapterTypes()

	// final AssetFileDescriptor openAssetFileDescriptor(Uri uri, String mode)
	// final AssetFileDescriptor openAssetFileDescriptor(Uri uri, String mode, CancellationSignal cancellationSignal)
	// final ParcelFileDescriptor openFileDescriptor(Uri uri, String mode, CancellationSignal cancellationSignal)
	// final ParcelFileDescriptor openFileDescriptor(Uri uri, String mode)
	// final InputStream openInputStream(Uri uri)
	// final OutputStream openOutputStream(Uri uri)
	// final OutputStream openOutputStream(Uri uri, String mode)
	// final AssetFileDescriptor openTypedAssetFileDescriptor(Uri uri, String mimeType, Bundle opts, CancellationSignal cancellationSignal)
	// final AssetFileDescriptor openTypedAssetFileDescriptor(Uri uri, String mimeType, Bundle opts)

	// AssetFileDescriptor openAssetFile(Uri url, String mode, CancellationSignal signal)
	// AssetFileDescriptor openAssetFile(Uri url, String mode)
	// ParcelFileDescriptor openFile(Uri url, String mode)
	// ParcelFileDescriptor openFile(Uri url, String mode, CancellationSignal signal)

	// public Cursor query(Uri url, String[] projection, String selection, String[] selectionArgs, String sortOrder)
	// public Cursor query(Uri url, String[] projection, String selection, String[] selectionArgs, String sortOrder, CancellationSignal cancellationSignal)

	// https://developers.google.com/gmail/android/
	// http://developer.android.com/reference/android/content/ContentResolver.html
	// http://developer.android.com/reference/android/content/ContentProviderClient.html

	// http://developer.android.com/reference/android/provider/Contacts.People.html
		//Deprecated in API level 5
	// http://developer.android.com/reference/android/provider/ContactsContract.Contacts.html
		//Display_name_primary, has_phone_number
	// http://developer.android.com/reference/android/provider/ContactsContract.Data.html
		//
	// http://developer.android.com/reference/android/provider/ContactsContract.PhoneLookup.html
		//Leave this one alone?
	// http://developer.android.com/reference/android/provider/ContactsContract.Profile.html
		//? 
	// http://developer.android.com/reference/android/provider/ContactsContract.RawContacts.html
		//Keep only account_name and/or account_type?

	// frameworks/base/core/java/android/content/ContentResolver.java

	// public List<SyncInfo> getCurrentSyncs()
	// public void registerContentObserver(android.net.Uri uri, boolean notifyForDescendants, android.database.IContentObserver observer, int userHandle)
	// public void unregisterContentObserver(android.database.IContentObserver observer)
	// http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.2.2_r1/android/content/ContentService.java
	// public List<android.content.SyncInfo> getCurrentSyncsAsUser(int userId)
	// http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/5.0.0_r1/android/content/IContentService.java

	// public Bundle call(String method, String request, Bundle args)
	// http://developer.android.com/reference/android/provider/Settings.html
	// http://developer.android.com/reference/android/provider/Settings.Global.html
	// http://developer.android.com/reference/android/provider/Settings.Secure.html
	// http://developer.android.com/reference/android/provider/Settings.System.html
	// http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.4.2_r1/com/android/providers/settings/SettingsProvider.java

	// @formatter:on

	// @formatter:off
	private enum Methods {
		getCurrentSync, getCurrentSyncs, getSyncAdapterTypes,
		openAssetFile, openFile, openAssetFileDescriptor, openFileDescriptor, openInputStream, openOutputStream, openTypedAssetFileDescriptor,
		query, Srv_call, Srv_query,
		Srv_getCurrentSyncs, Srv_getCurrentSyncsAsUser
	};
	// @formatter:on

	// @formatter:off
	public static List<String> cProviderClassName = Arrays.asList(new String[] {
		"com.android.providers.downloads.DownloadProvider",
		"com.android.providers.calendar.CalendarProvider2",
		"com.android.providers.contacts.CallLogProvider",
		"com.android.providers.contacts.ContactsProvider2",
		"com.google.android.gm.provider.PublicContentProvider",
		"com.google.android.gsf.gservices.GservicesProvider",
		"com.android.providers.telephony.MmsProvider",
		"com.android.providers.telephony.MmsSmsProvider",
		"com.android.providers.telephony.SmsProvider",
		"com.android.providers.telephony.TelephonyProvider",
		"com.android.providers.userdictionary.UserDictionaryProvider",
		"com.android.providers.settings.SettingsProvider",
			"com.android.providers.media.MediaProvider"
	});
	// @formatter:on

	public static List<XHook> getPackageInstances(String packageName, ClassLoader loader) {
		if (packageName.startsWith("com.android.browser.provider"))
			try {
				Class.forName("com.android.browser.provider.BrowserProviderProxy", false, loader);
				return getInstances("com.android.browser.provider.BrowserProviderProxy");
			} catch (ClassNotFoundException ignored) {
				try {
					Class.forName("com.android.browser.provider.BrowserProvider2", false, loader);
					return getInstances("com.android.browser.provider.BrowserProvider2");
				} catch (ClassNotFoundException ignored2) {
					Util.log(null, Log.ERROR, "Browser provider not found, package=" + packageName);
					return new ArrayList<XHook>();
				}
			}

		else if (packageName.startsWith("com.android.email.provider"))
			try {
				Class.forName("com.android.email.provider.EmailProvider", false, loader);
				return getInstances("com.android.email.provider.EmailProvider");
			} catch (ClassNotFoundException ignored) {
				Util.log(null, Log.WARN, "E-mail provider not found, package=" + packageName);
				return new ArrayList<XHook>();
			}

		else if (packageName.startsWith("com.google.android.gm.provider"))
			try {
				Class.forName("com.google.android.gm.provider.PublicContentProvider", false, loader);
				return getInstances("com.google.android.gm.provider.PublicContentProvider");
			} catch (ClassNotFoundException ignored) {
				Util.log(null, Log.WARN, "G-mail provider not found, package=" + packageName);
				return new ArrayList<XHook>();
			}

		else {
			List<XHook> listHook = new ArrayList<XHook>();
			for (String className : cProviderClassName)
				if (className.startsWith(packageName))
					listHook.addAll(getInstances(className));
			return listHook;
		}
	}

	private static List<XHook> getInstances(String className) {
		List<XHook> listHook = new ArrayList<XHook>();

		if ("com.android.providers.settings.SettingsProvider".equals(className))
			listHook.add(new XContentResolver(Methods.Srv_call, null, className));
		else
			listHook.add(new XContentResolver(Methods.Srv_query, null, className));

		return listHook;
	}

	public static List<XHook> getInstances(boolean server) {
		List<XHook> listHook = new ArrayList<XHook>();

		if (server) {
			listHook.add(new XContentResolver(Methods.Srv_query, null, "com.android.internal.telephony.IccProvider"));

			listHook.add(new XContentResolver(Methods.Srv_getCurrentSyncs, PrivacyManager.cAccounts, null));
			listHook.add(new XContentResolver(Methods.Srv_getCurrentSyncsAsUser, PrivacyManager.cAccounts, null));
		} else {
			listHook.add(new XContentResolver(Methods.getCurrentSync, PrivacyManager.cAccounts, false));
			listHook.add(new XContentResolver(Methods.getCurrentSyncs, PrivacyManager.cAccounts, false));
			listHook.add(new XContentResolver(Methods.getSyncAdapterTypes, PrivacyManager.cAccounts, false));

			listHook.add(new XContentResolver(Methods.openAssetFileDescriptor, PrivacyManager.cStorage, false));
			listHook.add(new XContentResolver(Methods.openFileDescriptor, PrivacyManager.cStorage, false));
			listHook.add(new XContentResolver(Methods.openInputStream, PrivacyManager.cStorage, false));
			listHook.add(new XContentResolver(Methods.openOutputStream, PrivacyManager.cStorage, false));
			listHook.add(new XContentResolver(Methods.openTypedAssetFileDescriptor, PrivacyManager.cStorage, false));

			listHook.add(new XContentResolver(Methods.openAssetFile, PrivacyManager.cStorage, true));
			listHook.add(new XContentResolver(Methods.openFile, PrivacyManager.cStorage, true));
			listHook.add(new XContentResolver(Methods.openTypedAssetFileDescriptor, PrivacyManager.cStorage, true));

			listHook.add(new XContentResolver(Methods.query, null, false));
			listHook.add(new XContentResolver(Methods.query, null, true));
		}

		return listHook;
	}

	@Override
	protected void before(XParam param) throws Throwable {
		switch (mMethod) {
		case getCurrentSync:
		case getCurrentSyncs:
		case getSyncAdapterTypes:
		case openAssetFile:
		case openFile:
		case openAssetFileDescriptor:
		case openFileDescriptor:
		case openInputStream:
		case openOutputStream:
		case openTypedAssetFileDescriptor:
			//Do nothing
			break;

		case Srv_call:
			break;

		case query:
		case Srv_query:
			//handleUriBefore(param); //KO: Don't need this anymore
			break;

		case Srv_getCurrentSyncs:
		case Srv_getCurrentSyncsAsUser:
			// Do nothing
			break;
		}
	}

	@Override
	protected void after(XParam param) throws Throwable {
		switch (mMethod) {
		case getCurrentSync:
			if (isRestricted(param))
				param.setResult(null);
			break;

		case getCurrentSyncs:
			if (isRestricted(param))
				param.setResult(new ArrayList<SyncInfo>());
			break;

		case getSyncAdapterTypes:
			if (isRestricted(param))
				param.setResult(new SyncAdapterType[0]);
			break;

		case openAssetFileDescriptor:
		case openFileDescriptor:
		case openInputStream:
		case openOutputStream:
		case openTypedAssetFileDescriptor:
		case openAssetFile:
		case openFile:
			if (param.args.length > 0 && param.args[0] instanceof Uri) {
				String uri = ((Uri) param.args[0]).toString();

				switch(getRestrictStateExtra(param, uri)){
					case 0:
						//Log.d("Smarper-Storage", "XContentResolver, " + mMethod + ": " + uri + " allowed");
						break;
					case 1:

						if (!SmarperUtil.forbiddenPathsInitialized)
							SmarperUtil.initializeForbiddenPaths();

						if (uri.startsWith(SmarperUtil.moviesURI.toString()) || uri.startsWith(SmarperUtil.dcimURI.toString()) || uri.startsWith(SmarperUtil.picturesURI.toString()) || uri.startsWith(SmarperUtil.musicURI.toString())){
							param.setThrowable(new FileNotFoundException("XPrivacy"));
							//Log.d("Smarper-Storage", "XContentResolver, " + mMethod + ": Obfuscating " + uri);
						}
						else{
							//
							//Log.d("Smarper-Storage", "ContentResolver: " + uri + " not in public folders list, not obfuscated!");
						}
						break;
					case 2:
						param.setThrowable(new FileNotFoundException("XPrivacy"));
						break;

				}

			}
			break;

		case Srv_call:
			handleCallAfter(param);
			break;

		case query:
		case Srv_query:
			handleUriAfter(param);
			break;

		case Srv_getCurrentSyncs:
		case Srv_getCurrentSyncsAsUser:
			if (param.getResult() != null)
				if (isRestricted(param)) {
					int uid = Binder.getCallingUid();
					@SuppressWarnings("unchecked")
					List<SyncInfo> listSync = (List<SyncInfo>) param.getResult();
					List<SyncInfo> listFiltered = new ArrayList<SyncInfo>();
					for (SyncInfo sync : listSync)
						if (XAccountManager.isAccountAllowed(sync.account, uid))
							listFiltered.add(sync);
					param.setResult(listFiltered);
				}
			break;
		}
	}

	@SuppressLint("DefaultLocale")
	private void handleUriBefore(XParam param) throws Throwable { //KO: We don't need this anymore
		// Check URI
		if (param.args.length > 1 && param.args[0] instanceof Uri) {
			String uri = ((Uri) param.args[0]).toString().toLowerCase();
			String[] projection = (param.args[1] instanceof String[] ? (String[]) param.args[1] : null);
			String selection = (param.args[2] instanceof String ? (String) param.args[2] : null);
			String[] selection_args = (param.args[3] instanceof String[] ? (String[]) param.args[3] : null);
			
			if (uri.startsWith("content://com.android.contacts/contacts/name_phone_or_email")) {
				// Do nothing
				//Log.d("Smarper-Contacts", "[BEFORE] Got URI: " + uri + ", doing nothing");

			} else if (uri.startsWith("content://com.android.contacts/")
					&& !uri.equals("content://com.android.contacts/")) {
				
				String proj_flat = "";
				if (projection != null){
				for (String s : projection){
					proj_flat += s + ", ";
					}
				}
				
				String selection_args_flat = "";
				if (selection_args != null){
				for (String s : selection_args){
					selection_args_flat += s + ", ";
					}
				}
				
				//Log.d("Smarper-Contacts", "[BEFORE] URI: " + uri + ", projection: " + proj_flat + ", selection: " + selection + ", selection_args: " + selection_args_flat);
				
				String[] components = uri.replace("content://com.android.", "").split("/");
				String methodName = components[0] + "/" + components[1].split("\\?")[0];
				if (methodName.equals("contacts/contacts") || methodName.equals("contacts/data")
						|| methodName.equals("contacts/phone_lookup") || methodName.equals("contacts/raw_contacts")){
					//if (isRestrictedExtra(param, PrivacyManager.cContacts, methodName, uri)) { //KO 
					int restrictState = getRestrictStateExtra(param, PrivacyManager.cContacts, methodName, uri);
					switch (restrictState){
					case 0:
						break;
					case 1:
						break;
					case 2:
						//KO: I don't know why the below is necessary or why the projection needs to be modified. Let's take this guy's word for it for now
						// Get ID from URL if any
						int urlid = -1;
						if ((methodName.equals("contacts/contacts") || methodName.equals("contacts/phone_lookup"))
								&& components.length > 2 && TextUtils.isDigitsOnly(components[2]))
							urlid = Integer.parseInt(components[2]);

						// Modify projection
						boolean added = false;
						if (projection != null && urlid < 0) {
							List<String> listProjection = new ArrayList<String>();
							listProjection.addAll(Arrays.asList(projection));
							String cid = getIdForUri(uri);
							if (cid != null && !listProjection.contains(cid)) {
								added = true;
								//Log.d("Smarper-Contacts", "[BEFORE] Projection modified, " + cid + " was added");
								listProjection.add(cid);
							}
							param.args[1] = listProjection.toArray(new String[0]);
						
						}
						if (added)
							param.setObjectExtra("column_added", added);
						
						//Log.d("Smarper-Contacts", "Param extras now looks like: " + param.getExtras().toString());
						break;
					}
					
					
				}
				
				/*if (projection!= null){
				Log.d("Smarper-Contacts", "[BEFORE] " + methodName + ", not restricted, has " + projection.length + " columns, they are:" );
				
				String cols = "";
				for (int i = 0; i < projection.length; i++){
					cols += " " + projection[i];
				}
				
				Log.d("Smarper-Contacts", "[BEFORE] " + cols);
				}*/
			}
		}
	}

	@SuppressLint("DefaultLocale")
	private void handleUriAfter(XParam param) throws Throwable {
		// Check URI
		if (param.args.length > 1 && param.args[0] instanceof Uri && param.getResult() != null) {
			String uri = ((Uri) param.args[0]).toString().toLowerCase();
			String[] projection = (param.args[1] instanceof String[] ? (String[]) param.args[1] : null);
			String selection = (param.args[2] instanceof String ? (String) param.args[2] : null);
			String[] selection_args = (param.args[3] instanceof String[] ? (String[]) param.args[3] : null);
			Cursor cursor = (Cursor) param.getResult();

			String proj_flat = "";
			if (projection != null){
			for (String s : projection){
				proj_flat += s + ", ";
				}
			}
			
			String selection_args_flat = "";
			if (selection_args != null){
			for (String s : selection_args){
				selection_args_flat += s + ", ";
				}
			}
			
			if (uri.startsWith("content://applications")) {
				// Applications provider: allow selected applications
				if (isRestrictedExtra(param, PrivacyManager.cSystem, "ApplicationsProvider", uri)) {
					MatrixCursor result = new MatrixCursor(cursor.getColumnNames());
					while (cursor.moveToNext()) {
						int colPackage = cursor.getColumnIndex("package");
						String packageName = (colPackage < 0 ? null : cursor.getString(colPackage));
						if (packageName != null && XPackageManager.isPackageAllowed(0, packageName))
							copyColumns(cursor, result);
					}
					result.respond(cursor.getExtras());
					param.setResult(result);
					cursor.close();
				}

			} else if (uri.startsWith("content://com.google.android.gsf.gservices")) {
				// Google services provider: block only android_id
				if (param.args.length > 3 && param.args[3] != null) {
					List<String> listSelection = Arrays.asList((String[]) param.args[3]);
					if (listSelection.contains("android_id"))
						if (isRestrictedExtra(param, PrivacyManager.cIdentification, "GservicesProvider", uri)) {
							int ikey = cursor.getColumnIndex("key");
							int ivalue = cursor.getColumnIndex("value");
							if (ikey == 0 && ivalue == 1 && cursor.getColumnCount() == 2) {
								MatrixCursor result = new MatrixCursor(cursor.getColumnNames());
								while (cursor.moveToNext()) {
									if ("android_id".equals(cursor.getString(ikey)) && cursor.getString(ivalue) != null)
										result.addRow(new Object[] { "android_id",
												PrivacyManager.getDefacedProp(Binder.getCallingUid(), "GSF_ID") });
									else
										copyColumns(cursor, result);
								}
								result.respond(cursor.getExtras());
								param.setResult(result);
								cursor.close();
							} else
								Util.log(this, Log.ERROR,
										"Unexpected result uri=" + uri + " columns=" + cursor.getColumnNames());
						}
				}

			} else if (uri.startsWith("content://com.android.contacts/contacts/name_phone_or_email")) {

				// Do nothing

			} else if (uri.startsWith("content://com.android.contacts/")) {


				//Log.d("Smarper-Contacts", "URI: " + uri + ", projection: " + proj_flat + ", selection: " + selection + ", selection_args: " + selection_args_flat);
				handleContacts(param);
				
				/* // Original XPrivacy code below, modified to work with 3 states, first attempts
				// Contacts provider: allow selected contacts
				String[] components = uri.replace("content://com.android.", "").split("/");
				String methodName = components[0] + "/" + components[1].split("\\?")[0];
				if (methodName.equals("contacts/contacts") || methodName.equals("contacts/data")
						|| methodName.equals("contacts/phone_lookup") || methodName.equals("contacts/raw_contacts")) {
					//if (isRestrictedExtra(param, PrivacyManager.cContacts, methodName, uri)) { //KO
					int restrictState = getRestrictStateExtra(param, PrivacyManager.cContacts, methodName, uri);
					switch (restrictState) {
					case 0:
						Object column_added = param.getObjectExtra("column_added");
						boolean added = (column_added == null ? false : (Boolean) param.getObjectExtra("column_added"));

						List<String> listColumn = new ArrayList<String>();
						listColumn.addAll(Arrays.asList(cursor.getColumnNames()));
						if (added)
							listColumn.remove(listColumn.size() - 1);
						
						if (cursor.getCount() != 0)
						printCursorContents(cursor, listColumn.size());
						
						break;
					case 1:
						//Filter
						// Get ID from URL if any
						/*int urlid = -1;
						if ((methodName.equals("contacts/contacts") || methodName.equals("contacts/phone_lookup"))
								&& components.length > 2 && TextUtils.isDigitsOnly(components[2]))
							urlid = Integer.parseInt(components[2]);
						*/

				// Modify column names back
				//KO: Why is this necessary
						/*column_added = param.getObjectExtra("column_added");
						added = (column_added == null ? false : (Boolean) param.getObjectExtra("column_added"));

						listColumn = new ArrayList<String>();
						listColumn.addAll(Arrays.asList(cursor.getColumnNames()));
						if (added)
							listColumn.remove(listColumn.size() - 1);

						
						//KO: Don't care about this
						// Get blacklist setting
						int uid = Binder.getCallingUid();
						boolean blacklist = PrivacyManager
								.getSettingBool(-uid, PrivacyManager.cSettingBlacklist, false);
						
						
						MatrixCursor result = new MatrixCursor(listColumn.toArray(new String[0]));

						// Filter rows
						/*String cid = getIdForUri(uri);
						Log.d("Smarper-Contacts", "[AFTER] cid for uri " + uri + " is " + cid);
						int iid = (cid == null ? -1 : cursor.getColumnIndex(cid));
						Log.d("Smarper-Contacts", "[AFTER] iid is " + iid);
						if (iid >= 0 || urlid >= 0)
							while (cursor.moveToNext()) {
								// Check if allowed
								long id = (urlid >= 0 ? urlid : cursor.getLong(iid));
								Log.d("Smarper-Contacts", "[AFTER] got id " + id + " from " + iid + ", now at position " + cursor.getPosition());
								boolean allowed = true; ///= PrivacyManager.getSettingBool(-uid, Meta.cTypeContact,Long.toString(id), false);
								 
								if (blacklist)
									allowed = !allowed;*/

				//KO
				//Configurable, but hardcoded for now
						/*if (cursor.getCount() >= 1 && cursor.getColumnCount() >= 1){
						HashSet<String> allowedColumnNames = new HashSet<String>();
						
						//Name
						allowedColumnNames.add(ContactsContract.CommonDataKinds.Identity.DISPLAY_NAME);
						allowedColumnNames.add(ContactsContract.CommonDataKinds.Identity.DISPLAY_NAME_PRIMARY);
						allowedColumnNames.add(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME);
						allowedColumnNames.add(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);
						allowedColumnNames.add(ContactsContract.CommonDataKinds.Identity.DISPLAY_NAME_ALTERNATIVE);
						
						//Phone number
						allowedColumnNames.add(ContactsContract.CommonDataKinds.Phone.NUMBER);
						allowedColumnNames.add("number"); //phone number
						allowedColumnNames.add(ContactsContract.CommonDataKinds.Phone.HAS_PHONE_NUMBER);
						allowedColumnNames.add(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY);
						allowedColumnNames.add(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER);
						
						//Email
						allowedColumnNames.add(ContactsContract.CommonDataKinds.Email.ADDRESS);

						//Internals
						allowedColumnNames.add(ContactsContract.CommonDataKinds.Identity.DISPLAY_NAME_SOURCE);
						allowedColumnNames.add(ContactsContract.CommonDataKinds.Identity.NAME_RAW_CONTACT_ID);
						allowedColumnNames.add(ContactsContract.Data.RAW_CONTACT_ID);
						allowedColumnNames.add(ContactsContract.Data.DATA_VERSION);
						allowedColumnNames.add(ContactsContract.Data.PINNED);
						allowedColumnNames.add(ContactsContract.Data.SORT_KEY_ALTERNATIVE);
						allowedColumnNames.add(ContactsContract.Data.SORT_KEY_PRIMARY);
						allowedColumnNames.add("version");
						allowedColumnNames.add("phonebook_label_alt"); //What is this though
						allowedColumnNames.add("lookup"); //I don't know what this one is either
						allowedColumnNames.add(ContactsContract.Contacts._ID);
						allowedColumnNames.add(ContactsContract.CommonDataKinds.Identity.CONTACT_ID);
						allowedColumnNames.add(ContactsContract.CommonDataKinds.Identity.MIMETYPE);
						allowedColumnNames.add("phonebook_bucket");
						allowedColumnNames.add("phonebook_bucket_alt");
						allowedColumnNames.add(ContactsContract.CommonDataKinds.Phone.LABEL);
						allowedColumnNames.add("data_id");
						
								
						
						ArrayList<Integer> forbiddenColumnIds = new ArrayList<Integer>();
						ArrayList<String> columnNames = new ArrayList(Arrays.asList(cursor.getColumnNames()));
						ArrayList<Integer> dataColumns = new ArrayList<Integer>();
						
						for (int col = 0; col < columnNames.size(); col++){
							if (!allowedColumnNames.contains(columnNames.get(col))){
								forbiddenColumnIds.add(col);
								//Log.d("Smarper-Contacts", "[AFTER] adding " + columnNames.get(col) + " to forbidden column ids");
							}
							else{
								//Log.d("Smarper-Contacts", "[AFTER] column " + columnNames.get(col) + " was allowed");
							}
							
							//Have to deal with data columns separately, match them with this regex
							if (columnNames.get(col).matches("data[1]?[0-9]*")){
								dataColumns.add(col);
								//Log.d("Smarper-Debug", "Adding column " + columnNames.get(col) + " to data columns");
							
							}
						}
								
						printCursorContents(cursor, listColumn.size()); //KO: for debugging
						obfuscatedCopyColumns(cursor, result, forbiddenColumnIds, listColumn.size(), dataColumns, columnNames);
						printCursorContents(result, listColumn.size());
						
						}
						
						else{
							Log.d("Smarper-Contacts", "[AFTER] Omitting cursor with rows " + cursor.getCount() + " and columns " + cursor.getColumnCount());
						}
							///}
						else
							Util.log(this, Log.WARN, "ID missing URI=" + uri + " added=" + added + "/" + cid
									+ " columns=" + TextUtils.join(",", cursor.getColumnNames()) + " projection="
									+ (projection == null ? "null" : TextUtils.join(",", projection)) + " selection="
									+ selection); */

						/*result.respond(cursor.getExtras());
						param.setResult(result);
						cursor.close();
						
						
						break;
						
					case 2:
						//Return empty cursor
						MatrixCursor empty = new MatrixCursor(cursor.getColumnNames());
						empty.respond(cursor.getExtras());
						param.setResult(empty);
						cursor.close();
						break;
					
					
					}
				} else {
					methodName = null;
					if (uri.startsWith("content://com.android.contacts/profile"))
						methodName = "contacts/profile";
					else
						methodName = "ContactsProvider2"; // fall-back

					if (methodName != null){
						//if (isRestrictedExtra(param, PrivacyManager.cContacts, methodName, uri)) { //KO
						switch (getRestrictStateExtra(param, PrivacyManager.cContacts, methodName, uri)){
						case 0:
							break;
						case 1:
						case 2:
						
							// Return empty cursor
							MatrixCursor result = new MatrixCursor(cursor.getColumnNames());
							result.respond(cursor.getExtras());
							param.setResult(result);
							cursor.close();
						///}
							break;
						}
					}
				}
 				*/
				//End contacts
				//Log.d("Smarper-Contacts", "---------------------------------");

			} else if(uri.startsWith("content://media/external")){
					String restrictionName=PrivacyManager.cStorage;
					String methodName="content://media/external";
					MatrixCursor result = new MatrixCursor(cursor.getColumnNames());
					switch(getRestrictStateExtra(param, restrictionName, methodName, uri)){
						case 0:
							//Log.d("Smarper-Storage", "XContentResolver: allowing query " + uri + ", " + proj_flat + ", " + selection + ", " + selection_args_flat);

							//Cursor query_result = (Cursor) param.getResult();

							//printCursorContents(query_result, query_result.getColumnCount(), 10);


							break;
						case 1:

							//Log.d("Smarper-Storage", "XContentResolver: obfuscating query by filtering " + uri + ", " + proj_flat + ", " + selection + ", " + selection_args_flat);
							//printCursorContents(cursor, cursor.getColumnCount());
							obfuscateMediaFiles(cursor, result);
							//Log.d("Smarper-Debug", "XContentResolver: Obfuscated cursor: ");
							//printCursorContents(result, result.getColumnCount());
							cursor.close();
							param.setResult(result);



							break;
						case 2:
							//Log.d("Smarper-Storage", "XContentResolver: denying " + uri + ", " + proj_flat + ", " + selection + ", " + selection_args_flat);
							//Empty cursor
							result.respond(cursor.getExtras());
							param.setResult(result);
							cursor.close();
							break;

					}


			} else {
				//Log.d("Smarper-XContentResolver", "Got other URI: " + uri);
				// Other uri restrictions
				String restrictionName = null;
				String methodName = null;

				//Log.d("Smarper-Debug", "Got other URI: " + uri);
				if (uri.startsWith("content://browser")) {
					restrictionName = PrivacyManager.cBrowser;
					methodName = "BrowserProvider2";
				}


				else if (uri.startsWith("content://com.android.calendar")) {
					restrictionName = PrivacyManager.cCalendar;
					methodName = "CalendarProvider2";
				}

				else if (uri.startsWith("content://call_log")) {
					restrictionName = PrivacyManager.cCalling;
					methodName = "CallLogProvider";
				}

				else if (uri.startsWith("content://contacts/people")) { //KO: this one is deprecated
					restrictionName = PrivacyManager.cContacts;
					methodName = "contacts/people";
				}

				else if (uri.startsWith("content://downloads")) {
					restrictionName = PrivacyManager.cBrowser;
					methodName = "Downloads";
				}

				else if (uri.startsWith("content://com.android.email.provider")) {
					restrictionName = PrivacyManager.cEMail;
					methodName = "EMailProvider";
				}

				else if (uri.startsWith("content://com.google.android.gm")) {
					restrictionName = PrivacyManager.cEMail;
					methodName = "GMailProvider";
				}

				else if (uri.startsWith("content://icc")) {
					restrictionName = PrivacyManager.cContacts;
					methodName = "IccProvider";
				}

				else if (uri.startsWith("content://mms")) {
					restrictionName = PrivacyManager.cMessages;
					methodName = "MmsProvider";
				}

				else if (uri.startsWith("content://mms-sms")) {
					restrictionName = PrivacyManager.cMessages;
					methodName = "MmsSmsProvider";
				}

				else if (uri.startsWith("content://sms")) {
					restrictionName = PrivacyManager.cMessages;
					methodName = "SmsProvider";
				}

				else if (uri.startsWith("content://telephony")) {
					restrictionName = PrivacyManager.cPhone;
					methodName = "TelephonyProvider";
				}

				else if (uri.startsWith("content://user_dictionary")) {
					restrictionName = PrivacyManager.cDictionary;
					methodName = "UserDictionary";
				}

				else if (uri.startsWith("content://com.android.voicemail")) {
					restrictionName = PrivacyManager.cMessages;
					methodName = "VoicemailContentProvider";
				}

				//KO: this appears to be the default case for anything else which doesn't match a pattern listed above
				// Check if know / restricted
				if (restrictionName != null && methodName != null) {
					//if (isRestrictedExtra(param, restrictionName, methodName, uri)) {
					switch (getRestrictStateExtra(param, restrictionName, methodName, uri)){
					case 0:
						break;
					case 1:
					case 2:
						// Return empty cursor
						//Log.d("Smarper-Contacts", "[AFTER] returning empty cursor, nothing else matches");
						MatrixCursor result = new MatrixCursor(cursor.getColumnNames());
						result.respond(cursor.getExtras());
						param.setResult(result);
						cursor.close();
						break;
					}
				}
				
				
			}
		}
		
	}
	
	@SuppressLint("DefaultLocale")
	private void handleContacts(XParam param){
		
		
		if (contactsMethods == null){
			setupContactsMethods();
			
		}
		Uri uri = ((Uri) param.args[0]);
		String uri_string = ((Uri) param.args[0]).toString().toLowerCase(); //Temporary
		String[] projection = (param.args[1] instanceof String[] ? (String[]) param.args[1] : null);
		String selection = (param.args[2] instanceof String ? (String) param.args[2] : null);
		String[] selection_args = (param.args[3] instanceof String[] ? (String[]) param.args[3] : null);
		Cursor cursor = (Cursor) param.getResult();

		
		String[] components = uri_string.replace("content://com.android.", "").split("/"); //TODO: could use URImatcher instead. Go with how the guy does it for now
		String methodName = components[0] + "/" + components[1].split("\\?")[0];
				
		
		//Switch by restrict state
		int restrictState;
		try{
			
		if(contactsMethods.contains(methodName)){ //If the cursor is empty, it doesn't matter 	
		restrictState = getRestrictStateExtra(param, PrivacyManager.cContacts, methodName, uri_string);

		if (cursor.getCount() > 0){
		switch (restrictState){
		case 0:
			//Allow
			//Log.d("Smarper-Contacts", methodName + " was allowed. Contents: ");
			//printCursorContents(cursor, cursor.getColumnCount());
			break;
		case 1:
			//Obfuscate
			//Log.d("Smarper-Contacts", "Obfuscating " + methodName);
			//Log.d("Smarper-Contacts", "Original cursor: ");
			//printCursorContents(cursor, cursor.getColumnCount());
		
			List<String> listColumn = new ArrayList<String>();
			listColumn.addAll(Arrays.asList(cursor.getColumnNames()));
			if (listColumn.contains("data1")){
				
				//All data1 column indices (there can be more than 1)
				ArrayList<Integer> data1_column_indices = new ArrayList<Integer>();
				int mimetype_column = -1; //It may not exist

				for (int i = 0; i < listColumn.size(); i++){
					if(listColumn.get(i).equals("data1"))
						data1_column_indices.add(i);

					if(listColumn.get(i).contains("mimetype"))
						mimetype_column = i;
				}
				
				MatrixCursor result = new MatrixCursor(cursor.getColumnNames());
				
				
				obfuscateColumns(cursor, result, data1_column_indices, mimetype_column);
				//Log.d("Smarper-Contacts", "Obfuscated cursor: ");
				//printCursorContents(result, result.getColumnCount());
				
				param.setResult(result);

			} else {
				//Log.d("Smarper-Contacts", "No data1 column found in cursor! Did nothing!");
			}
			
			
			break;
		case 2: 
			//Deny
			MatrixCursor empty = new MatrixCursor(cursor.getColumnNames());
			empty.respond(cursor.getExtras());
			param.setResult(empty);
			cursor.close();
			break;
		
		}
		
		}else{
			//Log.d("Smarper-Contacts", "Cursor was empty, ignoring");
		}
		}
		else{
			Log.d("Smarper-Contacts", "No hook defined for contacts method " + methodName + ", it was allowed!");
		}
		
		} catch (Throwable e){
		
			Log.e("Smarper-Error", "Error in XContentResolver: " + e.getMessage());
			e.printStackTrace();
		}
		
		
		
				
		
	}

	private void handleCallAfter(XParam param) throws Throwable {
		if (param.args.length > 1 && param.args[0] instanceof String && param.args[1] instanceof String) {
			String method = (String) param.args[0];
			String request = (String) param.args[1];

			if ("GET_secure".equals(method)) {
				if (Settings.Secure.ANDROID_ID.equals(request)) {
					if (!hasEmptyValue(param.getResult()))
						if (isRestricted(param, PrivacyManager.cIdentification, "Srv_Android_ID")) {
							int uid = Binder.getCallingUid();
							String value = (String) PrivacyManager.getDefacedProp(uid, "ANDROID_ID");
							Bundle bundle = new Bundle(1);
							bundle.putString("value", value);
							param.setResult(bundle);
						}

				}

			} else if ("GET_system".equals(method)) {
				// Do nothing

			} else if ("GET_global".equals(method)) {
				if ("default_dns_server".equals(request)) {
					if (!hasEmptyValue(param.getResult()))
						if (isRestricted(param, PrivacyManager.cNetwork, "Srv_Default_DNS")) {
							int uid = Binder.getCallingUid();
							InetAddress value = (InetAddress) PrivacyManager.getDefacedProp(uid, "InetAddress");
							Bundle bundle = new Bundle(1);
							bundle.putString("value", value.getHostAddress());
							param.setResult(bundle);
						}

				} else if ("wifi_country_code".equals(request)) {
					if (!hasEmptyValue(param.getResult()))
						if (isRestricted(param, PrivacyManager.cNetwork, "Srv_WiFi_Country")) {
							int uid = Binder.getCallingUid();
							String value = (String) PrivacyManager.getDefacedProp(uid, "CountryIso");
							Bundle bundle = new Bundle(1);
							bundle.putString("value", value == null ? null : value.toLowerCase(Locale.ROOT));
							param.setResult(bundle);
						}
				}
			}
		}
	}

	// Helper methods

	private boolean hasEmptyValue(Object result) {
		Bundle bundle = (Bundle) result;
		if (bundle == null)
			return true;
		if (!bundle.containsKey("value"))
			return true;
		return (bundle.get("value") == null);
	}

	private String getIdForUri(String uri) {
		if (uri.startsWith("content://com.android.contacts/contacts"))
			return "_id";
		else if (uri.startsWith("content://com.android.contacts/data"))
			return "contact_id";
		else if (uri.startsWith("content://com.android.contacts/phone_lookup"))
			return "_id";
		else if (uri.startsWith("content://com.android.contacts/raw_contacts"))
			return "contact_id";
		else
			Util.log(this, Log.ERROR, "Unexpected uri=" + uri);
		return null;
	}

	private void copyColumns(Cursor cursor, MatrixCursor result) {
		copyColumns(cursor, result, cursor.getColumnCount());
	}

	//KO: Copies all columns in the current row
	private void copyColumns(Cursor cursor, MatrixCursor result, int count) {
		try {
			Object[] columns = new Object[count];
			for (int i = 0; i < count; i++)
				switch (cursor.getType(i)) {
				case Cursor.FIELD_TYPE_NULL:
					columns[i] = null;
					break;
				case Cursor.FIELD_TYPE_INTEGER:
					columns[i] = cursor.getInt(i);
					break;
				case Cursor.FIELD_TYPE_FLOAT:
					columns[i] = cursor.getFloat(i);
					break;
				case Cursor.FIELD_TYPE_STRING:
					columns[i] = cursor.getString(i);
					break;
				case Cursor.FIELD_TYPE_BLOB:
					columns[i] = cursor.getBlob(i);
					break;
				default:
					Util.log(this, Log.WARN, "Unknown cursor data type=" + cursor.getType(i));
				}
			result.addRow(columns);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	private void printCursorContents(Cursor cursor, int totalColumns){
		
		String column_header = "";
		String[] column_names = cursor.getColumnNames();
		for(int i = 0; i < column_names.length; i++){
			column_header += column_names[i] + ", ";
		}
		
		Log.d("Smarper-Debug", column_header);
		
		cursor.moveToFirst();
		
		if (cursor.getCount() != 0){
		String cur_col = "";
		do{
			for (int col = 0; col < totalColumns; col++){
				switch (cursor.getType(col)) {
				case Cursor.FIELD_TYPE_NULL:
					cur_col += "null_type" + ", ";
					break;
				case Cursor.FIELD_TYPE_INTEGER:
					cur_col += cursor.getInt(col) + ", ";
					break;
				case Cursor.FIELD_TYPE_FLOAT:
					cur_col += cursor.getFloat(col) + ", ";
					break;
				case Cursor.FIELD_TYPE_STRING:
					cur_col += cursor.getString(col) + ", ";
					break;
				case Cursor.FIELD_TYPE_BLOB:
					cur_col += cursor.getBlob(col) + ", ";
					break;
				default:
					Util.log(this, Log.WARN, "Unknown cursor data type=" + cursor.getType(col));
				}
			}
			Log.d("Smarper-Debug", cur_col);
			cur_col = "";
			
		}while(cursor.moveToNext());
		}	
		else{
			Log.d("Smarper-Debug", "Cursor is empty!");
		}
	}

	private void printCursorContents(Cursor cursor, int totalColumns, int rowsToPrint){

		String column_header = "";
		String[] column_names = cursor.getColumnNames();
		for(int i = 0; i < column_names.length; i++){
			column_header += column_names[i] + ", ";
		}

		Log.d("Smarper-Storage", column_header);

		cursor.moveToFirst();
		int rowsPrinted = 0;

		if (cursor.getCount() != 0){
			String cur_col = "";
			do{
				for (int col = 0; col < totalColumns; col++){
					switch (cursor.getType(col)) {
						case Cursor.FIELD_TYPE_NULL:
							cur_col += "null_type" + ", ";
							break;
						case Cursor.FIELD_TYPE_INTEGER:
							cur_col += cursor.getInt(col) + ", ";
							break;
						case Cursor.FIELD_TYPE_FLOAT:
							cur_col += cursor.getFloat(col) + ", ";
							break;
						case Cursor.FIELD_TYPE_STRING:
							cur_col += cursor.getString(col) + ", ";
							break;
						case Cursor.FIELD_TYPE_BLOB:
							cur_col += cursor.getBlob(col) + ", ";
							break;
						default:
							Util.log(this, Log.WARN, "Unknown cursor data type=" + cursor.getType(col));
					}
				}
				Log.d("Smarper-Storage", cur_col);
				cur_col = "";

				rowsPrinted++;

			}while(rowsPrinted <= rowsToPrint);
		}
		else{
			Log.d("Smarper-Storage", "Cursor is empty!");
		}
	}
	
	private void obfuscateColumns(Cursor cursor, MatrixCursor result, ArrayList<Integer> dataColumnIds, int mimetype_column){
		
		try{
			
			cursor.moveToFirst();
			boolean copyThisRow = true;
			
			
			do {
			//Get the value of the data1 columns
			forloop: for (Integer column_id : dataColumnIds) {
				copyThisRow = false; //default to FALSE

				//If we have the mimetype, we know what to allow and not allow already.
				if (mimetype_column != -1) {

					if (allowedMimetypes_contacts.contains(cursor.getString(mimetype_column))){
						copyThisRow = true;
					}

				} else {
					//No mimetype, so use regex match for allowed datatypes
					if (cursor.getType(column_id.intValue()) == (Cursor.FIELD_TYPE_STRING)) {

						String value = cursor.getString(column_id);
						//Email
						if (Patterns.EMAIL_ADDRESS.matcher(value).matches()) { //value.matches("(\\w)*.*@(\\w)*.*")) {
							copyThisRow = true;
							break forloop;

						}//Name
						else if (value.matches("[A-Z][a-z]+(-[A-Z][a-z]+)?( [A-Z][a-z]+(-[A-Z][a-z]+)?)+")){ //Name. First, last, or more. Starting with capital letters. May include dashes.
							copyThisRow = true;
							break forloop;
						}


						//Address
						else if (value.matches("[\\w\\s]+\\n([a-zA-Z,\\s]*)+(\\d){4,}([a-zA-Z,\\s]*)+")){ //Address. Anything on the first line. Second line must have at least 4 digits
							copyThisRow = true;
							break forloop;
						}

						//Phone number or none of the above
						else{

							//Loop through characters and check if each one is "Really Dialable" -- if yes, then treat that as a valid phone number
							boolean dialable = false;
							checkChars: for (char c : value.toCharArray()){
								if (!PhoneNumberUtils.isReallyDialable(c)){
									dialable = false;
									break checkChars;
								} else{
									dialable = true;
								}
							}

							if (dialable) {
								copyThisRow = true;
								//Log.d("Smarper-Contacts", "Value is a phone number: " + value);
								break forloop;
							}
							else {
								//Log.d("Smarper-Contacts", "Value is not a dialable phone number: " + value);
								Log.d("Smarper-Contacts", "Value is none of the above and will not be copied over to the result cursor!: " + value);
							}
						}


					}


				}
			}
			
			if(copyThisRow)
				copyColumns(cursor, result, cursor.getColumnCount());
			
			} while(cursor.moveToNext());
			
		} catch (Throwable ex){
			ex.printStackTrace();
		}
		
	}


	private void obfuscateMediaFiles(Cursor cursor, MatrixCursor result){

		try{

			cursor.moveToFirst();
			boolean copyThisRow;

			int _dataColumn = cursor.getColumnIndex("_data");
			if (_dataColumn == -1){
				//KO: Can't do anything
				return;
			}

			do {

				copyThisRow = true;

				String path = cursor.getString(_dataColumn);

				if (!SmarperUtil.forbiddenPathsInitialized)
					SmarperUtil.initializeForbiddenPaths();

				if (path.startsWith(SmarperUtil.dcimPublicDir) || path.startsWith(SmarperUtil.picturesPublicDir) || path.startsWith(SmarperUtil.moviesPublicDir) || path.startsWith(SmarperUtil.musicPublicDir))
					copyThisRow = false;


				if(copyThisRow)
					copyColumns(cursor, result, cursor.getColumnCount());

			} while(cursor.moveToNext());

		} catch (Throwable ex){
			ex.printStackTrace();
		}

	}
	
	//KO: For forbidden columns, return 0, null or empty string depending on data type
	/*private void obfuscatedCopyColumns(Cursor cursor, MatrixCursor result, ArrayList<Integer> forbiddenColumns, int totalColumns, ArrayList<Integer> dataColumns, ArrayList<String> columnNames) {
		try {
			Object[] columns = new Object[totalColumns];
			cursor.moveToFirst();
			do {
			for (int col = 0; col < totalColumns; col++){
				switch (cursor.getType(col)) {
				case Cursor.FIELD_TYPE_NULL:
					columns[col] = null;
					break;
				case Cursor.FIELD_TYPE_INTEGER:
					columns[col] = (forbiddenColumns.contains(col)) ? null : cursor.getInt(col);
					break;
				case Cursor.FIELD_TYPE_FLOAT:
					columns[col] = (forbiddenColumns.contains(col)) ? null : cursor.getFloat(col);
					break;
				case Cursor.FIELD_TYPE_STRING:
					columns[col] = (forbiddenColumns.contains(col)) ? null : cursor.getString(col);
					break;
				case Cursor.FIELD_TYPE_BLOB:
					columns[col] = (forbiddenColumns.contains(col)) ? null : cursor.getBlob(col);
					break;
				default:
					Util.log(this, Log.WARN, "Unknown cursor data type=" + cursor.getType(col));
				}
			}
			
			//Deal with mimetype and data columns for this row
			
			//There exists a column mimetype
			if (columnNames.indexOf("mimetype") != -1){
				
				int mimetypeCol = columnNames.indexOf("mimetype");
				
				
			if (!cursor.getString(mimetypeCol).contains("vnd.android.cursor.item/email_v2") && !cursor.getString(mimetypeCol).contains("vnd.android.cursor.item/phone_v2") && !cursor.getString(mimetypeCol).contains("vnd.android.cursor.item/name")){
			
				Log.d("Smarper-Debug", "Deleting data for " + cursor.getString(mimetypeCol));

				for (int col : dataColumns){
					columns[col] = null;
				}
				
				//columns[mimetypeCol] = null;
				
				}
			}
			
			result.addRow(columns);
			
			} while (cursor.moveToNext());
			
		} catch (Throwable ex) {
			Util.bug(this, ex);
		}
	}
	*/
	
	@SuppressWarnings("unused")
	private void _dumpCursor(String uri, Cursor cursor) {
		_dumpHeader(uri, cursor);
		int i = 0;
		while (cursor.moveToNext() && i++ < 10)
			_dumpColumns(cursor, "");
		cursor.moveToFirst();
	}

	private void _dumpHeader(String uri, Cursor cursor) {
		Util.log(this, Log.WARN, TextUtils.join(", ", cursor.getColumnNames()) + " uri=" + uri);
	}

	private void _dumpColumns(Cursor cursor, String msg) {
		String[] columns = new String[cursor.getColumnCount()];
		for (int i = 0; i < cursor.getColumnCount(); i++)
			switch (cursor.getType(i)) {
			case Cursor.FIELD_TYPE_NULL:
				columns[i] = null;
				break;
			case Cursor.FIELD_TYPE_INTEGER:
				columns[i] = Integer.toString(cursor.getInt(i));
				break;
			case Cursor.FIELD_TYPE_FLOAT:
				columns[i] = Float.toString(cursor.getFloat(i));
				break;
			case Cursor.FIELD_TYPE_STRING:
				columns[i] = cursor.getString(i);
				break;
			case Cursor.FIELD_TYPE_BLOB:
				columns[i] = "[blob]";
				break;
			default:
				Util.log(this, Log.WARN, "Unknown cursor data type=" + cursor.getType(i));
			}
		Util.log(this, Log.WARN, TextUtils.join(", ", columns) + " " + msg);
	}
}
