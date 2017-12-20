package biz.bokhorst.xprivacy;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import android.location.Location;
import android.location.LocationManager;
import android.os.Binder;

import com.google.android.gms.maps.*;

public class XGoogleMapV2 extends XHook {
	private Methods mMethod;

	private XGoogleMapV2(Methods method, String restrictionName) {
		super(restrictionName, method.name(), String.format("MapV2.%s", method.name()));
		mMethod = method;
	}

	public String getClassName() {
		if (mMethod == Methods.getPosition)
			return "com.google.android.gms.maps.model.Marker";
		else
			return "com.google.android.gms.maps.GoogleMap";
	}

	// @formatter:off

	// final Location getMyLocation()
	// final void setLocationSource(LocationSource source)
	// final void setOnMapClickListener(GoogleMap.OnMapClickListener listener)
	// final void setOnMapLongClickListener(GoogleMap.OnMapLongClickListener listener)
	// final void setOnMyLocationChangeListener(GoogleMap.OnMyLocationChangeListener listener)
	// http://developer.android.com/reference/com/google/android/gms/maps/GoogleMap.html

	// public LatLng getPosition ()
	// http://developer.android.com/reference/com/google/android/gms/maps/model/Marker.html
	// http://developer.android.com/reference/com/google/android/gms/maps/model/LatLng.html

	// @formatter:on

	private enum Methods {
		getMyLocation, getPosition, setLocationSource, setOnMapClickListener, setOnMapLongClickListener, setOnMyLocationChangeListener
	};

	public static List<XHook> getInstances() {
		List<XHook> listHook = new ArrayList<XHook>();
		for (Methods method : Methods.values())
			listHook.add(new XGoogleMapV2(method, PrivacyManager.cLocation));
		return listHook;
	}

	@Override
	protected void before(XParam param) throws Throwable {
		switch (mMethod) {
		case getMyLocation:
			// Do nothing
			break;

		case getPosition:
			// Do nothing
			break;

		case setLocationSource:
		case setOnMapClickListener:
		case setOnMapLongClickListener:
			break;
			
		case setOnMyLocationChangeListener: //KO 
			/*int restrictState = getRestrictState(param);
			switch (restrictState){
			case 1:
				locationChangedListener(param, 0, GoogleMap.OnMyLocationChangeListener.class, 1);
				break;
			case 2:
				locationChangedListener(param, 0, GoogleMap.OnMyLocationChangeListener.class, 2);
				break;
			}*/
			//if (getRestrictState(param) == 2)
			//	param.setResult(null);  //This was causing the zoom-out in Runkeeper
			break;
		}
	}

	@Override
	protected void after(XParam param) throws Throwable {
		switch (mMethod) {
		//KO: Add obfuscate case here
		case getMyLocation:
			if (param.getResult() != null){
				Location originalLocation = (Location) param.getResult();
				
				switch(getRestrictState(param)){
				
				case 1:
					param.setResult(PrivacyManager.getObfuscatedLocation(originalLocation));
					
					break;
					
				case 2:
					param.setResult(PrivacyManager.getDefacedLocation(Binder.getCallingUid(), originalLocation));
					
					break;
				}
			}
			break;

			//KO: Add obfuscate case here
		case getPosition:
			if (param.getResult() != null){
				switch (getRestrictState(param)){//if (isRestricted(param)) {
				case 1:
					Location fakeLocation = new Location(LocationManager.PASSIVE_PROVIDER);
					//LatLng actual = (LatLng) param.getResult();
					Field fLat = param.getResult().getClass().getField("latitude");
					Field fLon = param.getResult().getClass().getField("longitude");
					
					fakeLocation.setLatitude(fLat.getDouble(param.getResult()));
					fakeLocation.setLongitude(fLon.getDouble(param.getResult()));
					Location obfuscated = PrivacyManager.getObfuscatedLocation(fakeLocation);

					fLat.setAccessible(true);
					fLon.setAccessible(true);
					fLat.set(param.getResult(), obfuscated.getLatitude());
					fLon.set(param.getResult(), obfuscated.getLongitude());
					break;
					
				case 2:
					Location fakeLocation2 = PrivacyManager.getDefacedLocation(Binder.getCallingUid(), null);
					Field fLat2 = param.getResult().getClass().getField("latitude");
					Field fLon2 = param.getResult().getClass().getField("longitude");
					fLat2.setAccessible(true);
					fLon2.setAccessible(true);
					fLat2.set(param.getResult(), fakeLocation2.getLatitude());
					fLon2.set(param.getResult(), fakeLocation2.getLongitude());
					break;
				}
			}
			break;

		case setLocationSource:
		case setOnMapClickListener:
		case setOnMapLongClickListener:
		case setOnMyLocationChangeListener:
			// Do nothing
			break;
		}
	}
	
	//KO: for monitoring location changes
	private void locationChangedListener(XParam param, int arg, Class<?> interfaze, int restrictState) throws Throwable {
		
		// Create proxy
		ClassLoader cl = ClassLoader.getSystemClassLoader();//param.thisObject.getClass().getClassLoader();
		InvocationHandler ih = new OnLocationChangedHandler(Binder.getCallingUid(), param.args[arg], restrictState);
		Object proxy = Proxy.newProxyInstance(cl, new Class<?>[] { interfaze }, ih);

		param.args[arg] = proxy;
	}
	
	//KO: invocation handler
	private class OnLocationChangedHandler implements InvocationHandler {
		private int mUid;
		private Object mTarget;
		private int mRestrictState;

		public OnLocationChangedHandler(int uid, Object target, int restrictState) {
			mUid = uid;
			mTarget = target;
			mRestrictState = restrictState;
		}

		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if ("onMyLocationChange".equals(method.getName())){
				if (mRestrictState == 1)
				args[0] = PrivacyManager.getObfuscatedLocation((Location) args[0]);
				
				else if (mRestrictState == 2)
				args[0] = PrivacyManager.getDefacedLocation(mUid, (Location) args[0]);
			}
			return method.invoke(mTarget, args);
		}
	}
}
