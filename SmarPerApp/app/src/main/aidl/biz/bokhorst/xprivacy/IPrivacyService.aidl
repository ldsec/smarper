package biz.bokhorst.xprivacy;

import biz.bokhorst.xprivacy.PRestriction;
import biz.bokhorst.xprivacy.PSetting;
import biz.bokhorst.xprivacy.appPackNameAndIcon;

interface IPrivacyService {
	int getVersion();
	List /* String */ check();
	boolean databaseCorrupt();
	void reportError(String message);
	Map getStatistics();
	void setRestriction(in PRestriction restriction);
	void setRestrictionList(in List<PRestriction> listRestriction);
	PRestriction getRestriction(in PRestriction restriction, boolean usage, String secret);
	PRestriction getRestrictionTime(in PRestriction restriction, boolean usage, String secret, long time); //KO: Our version with time parameter
	List<PRestriction> getRestrictionList(in PRestriction selector);
	boolean isRestrictionSet(in PRestriction restriction);
	void deleteRestrictions(int uid, String restrictionName);
	long getUsage(in List<PRestriction> restriction);
	List<PRestriction> getUsageList(int uid, String restrictionName);
	List<PRestriction> getFullUsageList(int uid, String restrictionName); //KO: Read from our table
	List<appPackNameAndIcon> getAppIcons(); //JS: Fetch App Icons from database
	String getHashFromDB(); 						//JS: Get Hash-user id from smarper_params
	long[] getDataUploadStats(); //KO: Get the value of the data upload pointer from settings table
	void clearTemplate(); //KO: Undo the operation applied by the template
	long getMostRecentDecisionId(); //KO: Get highest _id from usage table
    void initialize(); //KO: initialize background thread, runs post-boot
    void updateCameraStatus(boolean newStatus); //KO: Update the camera status (open or not) in PrivacyService
    void updateTypingStatus(boolean newStatus); //KO: Update the typing status (typing or not) in PrivacyService
	List<PRestriction> GetFilteredData(in int[] decision, in int[] decisiontype, int time, in List<String> checkedCategories, in List<String> AppList);    //JS
	void deleteUsage(int uid);
	void UpdateDBwithNewDecision(int radioId, int idValue);
	List<String> PermissionInfoOnClick(int idValue);                    //JS
	int ConnectToServerandSendData(String url);                           //JS
	int ClearCacheOfSelectedApp(int uid);                      //JS: Clear all cached decisions of the selected app
	void setSetting(in PSetting setting);
	void setSettingList(in List<PSetting> listSetting);
	PSetting getSetting(in PSetting setting);
	List<PSetting> getSettingList(in PSetting selector);
	void deleteSettings(int uid);
	void clear();
	void flush();
	void dump(int uid);
	int[] getInstalledAppUids(); //KO
}