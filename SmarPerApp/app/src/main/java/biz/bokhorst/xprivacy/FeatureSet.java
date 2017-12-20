package biz.bokhorst.xprivacy;

import android.location.Location;
import android.location.LocationManager;

//KO: Created this entire class
// Contextual features gathered at decision time
public class FeatureSet {

	public boolean initialized = false; //Whether initial state has been populated

	public float batteryPercent = 0; 
	public boolean cached = false;
	
	int isCharging; //BATTERY_STATUS_ *
	
	int chargePlug; //BATTERY_PLUGGED_*
	
	int connectionType; //ConnectivityManager.TYPE_ * , -1 means no connection
	
	int dockingState; //Intent.EXTRA_DOCK_STATE_ *   //Default undocked
	
	public boolean asked_user = false;
	
	public Location currentLocation;
	
	public String topApp_package; //Foreground app
	
	public String topApp_name; //Foreground app name
	
	public String topActivity; //Top activity in the foreground app
	
	public boolean screenOn;
	
	public boolean screenLocked;
	
	public int detectedActivity; //
	
	public int detectedActivityConfidence;
	
	int ringer; // RINGER_MODE_*  (AudioManager)
	
	boolean headsetPlugged;
	
	String headsetType;
	
	boolean headsetHasMike;
	
	
	public FeatureSet(){
		
	}

	public int providerStringToInt(){

		int providerInt = 1; //Invalid

		if (currentLocation!=null) {
			switch (currentLocation.getProvider()) {
				case LocationManager.GPS_PROVIDER:
					providerInt = 4;
					break;
				case LocationManager.NETWORK_PROVIDER:
					providerInt = 3;
					break;
				case LocationManager.PASSIVE_PROVIDER:
					providerInt = 2;
					break;
				case "": //Possible that no provider has been set
					providerInt = 1;
					break;
			}
		}

		return providerInt;

	}

	public FeatureSet(FeatureSet other){
		
		this.batteryPercent = other.batteryPercent;
		this.isCharging = other.isCharging;
		this.chargePlug = other.chargePlug;
		this.connectionType = other.connectionType;
		this.dockingState = other.dockingState;
		this.currentLocation = other.currentLocation;
		this.topApp_package = other.topApp_package;
		this.topApp_name = other.topApp_name;
		this.topActivity = other.topActivity;
		
		
		//KO: Cleaner to copy everything over
		this.initialized = other.initialized;
		this.cached = other.cached;
		this.asked_user = other.asked_user;
		this.screenOn = other.screenOn;
		this.screenLocked = other.screenLocked;
		this.detectedActivity = other.detectedActivity;
		this.detectedActivityConfidence = other.detectedActivityConfidence;
		this.ringer = other.ringer;
		this.headsetPlugged = other.headsetPlugged;
		this.headsetType = other.headsetType;
		this.headsetHasMike = other.headsetHasMike;
		
	}
}
