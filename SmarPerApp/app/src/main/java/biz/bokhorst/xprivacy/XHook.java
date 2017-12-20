package biz.bokhorst.xprivacy;

import java.util.Date;

import android.annotation.SuppressLint;
import android.os.Binder;
import android.util.Log;

public abstract class XHook {
	private String mRestrictionName;
	private String mMethodName;
	private String mSpecifier;
	private String mSecret;
	public Object[] xparamArgs; //KO: Store the method arguments here
	
	protected XHook(String restrictionName, String methodName, String specifier) {
		mRestrictionName = restrictionName;
		mMethodName = methodName;
		mSpecifier = specifier;
	}

	abstract public String getClassName();

	public boolean isVisible() {
		return true;
	}

	public String getRestrictionName() {
		return mRestrictionName;
	}

	public String getMethodName() {
		return mMethodName;
	}

	public String getSpecifier() {
		return (mSpecifier == null ? mMethodName : mSpecifier);
	}

	public void setSecret(String secret) {
		mSecret = secret;
	}

	protected String getSecret() {
		return mSecret;
	}

	abstract protected void before(XParam param) throws Throwable;

	abstract protected void after(XParam param) throws Throwable;

	protected boolean isRestricted(XParam param) throws Throwable {
		return isRestricted(param, getSpecifier());
	}
	
	//KO: New version, for obfuscate
	protected int getRestrictState(XParam param) throws Throwable{
		return getRestrictState(param, getSpecifier());
	}

	// KO: take timestamp in all of these methods
	// KO: send this timestamp to our version of these methods, if modifications enabled
	protected boolean isRestrictedExtra(XParam param, String extra) throws Throwable {
		int uid = Binder.getCallingUid();
		long time = new Date().getTime();
		return PrivacyManager.getRestrictionExtraTime(this, uid, mRestrictionName, getSpecifier(), extra, mSecret, time);
	}

	//KO: For "Obfuscate"
		protected int getRestrictStateExtra(XParam param, String extra) throws Throwable {
			int uid = Binder.getCallingUid();
			this.xparamArgs = param.args;
			long time = new Date().getTime();
				return PrivacyManager.getPRestrictionExtraTime(this, uid, mRestrictionName, getSpecifier(), extra, mSecret, time);
		}
	
	protected boolean isRestrictedExtra(XParam param, String methodName, String extra) throws Throwable {
		int uid = Binder.getCallingUid();
		long time = new Date().getTime();
		return PrivacyManager.getRestrictionExtraTime(this, uid, mRestrictionName, methodName, extra, mSecret, time);
	}

	//KO: for ACTION_VIEW
	protected int getRestrictedExtra(XParam param, String methodName, String extra)
			throws Throwable {
		int uid = Binder.getCallingUid();
		long time = new Date().getTime();

		return PrivacyManager.getPRestrictionExtraTime(this, uid, mRestrictionName, methodName, extra, mSecret, time);
	}

	protected boolean isRestrictedExtra(XParam param, String restrictionName, String methodName, String extra)
			throws Throwable {
		int uid = Binder.getCallingUid();
		long time = new Date().getTime();
		return PrivacyManager.getRestrictionExtraTime(this, uid, restrictionName, methodName, extra, mSecret, time);
	}

	//KO: For obfuscate, contacts
	protected int getRestrictStateExtra(XParam param, String restrictionName, String methodName, String extra)
			throws Throwable {
		this.xparamArgs = param.args;
		int uid = Binder.getCallingUid();
		long time = new Date().getTime();

		return PrivacyManager.getPRestrictionExtraTime(this, uid, restrictionName, methodName, extra, mSecret, time);
	}

	protected boolean isRestrictedExtra(int uid, String restrictionName, String methodName, String extra)
			throws Throwable {
		long time = new Date().getTime();
		return PrivacyManager.getRestrictionExtraTime(this, uid, restrictionName, methodName, extra, mSecret, time);
	}

	protected boolean isRestrictedValue(int uid, String value) throws Throwable {
		long time = new Date().getTime();
		return PrivacyManager.getRestrictionExtraTime(this, uid, mRestrictionName, getSpecifier(), null, value, mSecret, time);
	}

	protected boolean isRestrictedValue(XParam param, String value) throws Throwable {
		int uid = Binder.getCallingUid();
		long time = new Date().getTime();
		return PrivacyManager.getRestrictionExtraTime(this, uid, mRestrictionName, getSpecifier(), null, value, mSecret, time);
	}

	protected boolean isRestrictedValue(int uid, String methodName, String value) throws Throwable {
		long time = new Date().getTime();
		return PrivacyManager.getRestrictionExtraTime(this, uid, mRestrictionName, methodName, null, value, mSecret, time);
	}

	protected boolean isRestrictedValue(int uid, String restrictionName, String methodName, String value)
			throws Throwable {
		long time = new Date().getTime();
		return PrivacyManager.getRestrictionExtraTime(this, uid, restrictionName, methodName, null, value, mSecret, time);
	}

	protected boolean isRestrictedExtraValue(int uid, String restrictionName, String methodName, String extra,
			String value) throws Throwable {
		long time = new Date().getTime();
		return PrivacyManager.getRestrictionExtraTime(this, uid, restrictionName, methodName, extra, value, mSecret, time);
	}

	protected boolean isRestricted(XParam param, String methodName) throws Throwable {
		int uid = Binder.getCallingUid();
		long time = new Date().getTime();
		return PrivacyManager.getRestrictionTime(this, uid, mRestrictionName, methodName, mSecret, time);
	}
	
	//KO: For "Obfuscate"
	protected int getRestrictState(XParam param, String methodName) throws Throwable {
		int uid = Binder.getCallingUid();
		this.xparamArgs = param.args;
		long time = new Date().getTime();
			return PrivacyManager.getPRestrictionTime(this, uid, mRestrictionName, methodName, mSecret, time);
	}

	protected boolean isRestricted(XParam param, String restrictionName, String methodName) throws Throwable {
		int uid = Binder.getCallingUid();
		long time = new Date().getTime();
		return PrivacyManager.getRestrictionTime(this, uid, restrictionName, methodName, mSecret, time);
	}
	
	//KO: For "Obfuscate"
	protected int getRestrictState(XParam param, String restrictionName, String methodName) throws Throwable {
		int uid = Binder.getCallingUid();
		long time = new Date().getTime();
		this.xparamArgs = param.args;
		return PrivacyManager.getPRestrictionTime(this, uid, restrictionName, methodName, mSecret, time);
	}

	protected boolean getRestricted(int uid) throws Throwable {
		long time = new Date().getTime();
		return PrivacyManager.getRestrictionTime(this, uid, mRestrictionName, getSpecifier(), mSecret, time);
	}

	protected boolean getRestricted(int uid, String methodName) throws Throwable {
		long time = new Date().getTime();
		return PrivacyManager.getRestrictionTime(this, uid, mRestrictionName, methodName, mSecret, time);
	}

	protected boolean getRestricted(int uid, String restrictionName, String methodName) throws Throwable {
		long time = new Date().getTime();
		return PrivacyManager.getRestrictionTime(this, uid, restrictionName, methodName, mSecret, time);
	}
	
	//KO: for obfuscate
	protected int getRestrictedState(int uid, String restrictionName, String methodName) throws Throwable {
		long time = new Date().getTime();
		this.xparamArgs = null;
		return PrivacyManager.getPRestrictionTime(this, uid, restrictionName, methodName, mSecret, time);
	}

	@Override
	@SuppressLint("FieldGetter")
	public String toString() {
		return getRestrictionName() + "/" + getSpecifier() + " (" + getClassName() + ")";
	}
}