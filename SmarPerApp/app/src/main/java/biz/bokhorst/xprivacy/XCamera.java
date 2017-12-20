package biz.bokhorst.xprivacy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import android.hardware.Camera;
import android.os.Binder;
import android.util.Log;

public class XCamera extends XHook {
	private Methods mMethod;
	private static boolean isOpen = false; //KO: Track if camera is open

	public static boolean isCameraOpen(){ //KO: Return camera open status when deciding if should prompt
		//Log.d("Smarper-debug", "isCameraOpen(): " + isOpen);
		return isOpen;
	}

	private XCamera(Methods method, String restrictionName) {
		super(restrictionName, method.name(), "Camera." + method.name());
		mMethod = method;
	}

	public String getClassName() {
		return "android.hardware.Camera";
	}

	// @formatter:off

	// public void setPreviewCallback(Camera.PreviewCallback cb)
	// public void setPreviewCallbackWithBuffer(Camera.PreviewCallback cb)
	// public void setPreviewDisplay(SurfaceHolder holder)
	// public void setPreviewTexture(SurfaceTexture surfaceTexture)
	// public final void setOneShotPreviewCallback (Camera.PreviewCallback cb)
	// public native final void startPreview()
	// public void stopPreview()
	// public final void takePicture(ShutterCallback shutter, PictureCallback raw, PictureCallback jpeg)
	// public final void takePicture(ShutterCallback shutter, PictureCallback raw, PictureCallback postview, PictureCallback jpeg)
	// frameworks/base/core/java/android/hardware/Camera.java
	// http://developer.android.com/reference/android/hardware/Camera.html

	// @formatter:on

	private enum Methods {
		open, release, setPreviewCallback, setPreviewCallbackWithBuffer, setPreviewDisplay, setPreviewTexture, setOneShotPreviewCallback, startPreview, stopPreview, takePicture
	};

	public static List<XHook> getInstances() {
		List<XHook> listHook = new ArrayList<XHook>();
		for (Methods cam : Methods.values())
			listHook.add(new XCamera(cam, cam == Methods.stopPreview ? null : PrivacyManager.cMedia));
		return listHook;
	}

	@Override
	protected void before(XParam param) throws Throwable {
		switch (mMethod) {
		case setPreviewCallback:
		case setOneShotPreviewCallback:
		case setPreviewCallbackWithBuffer:
					//KO: Intercept preview frames
			if (param.args[0] == null){
								//KO: Caution: if the argument is null, it means we want to stop receiving preview frames
			}
			else{
			switch(getRestrictState(param)){
			case 1:
				previewFrameListener(param,0,Camera.PreviewCallback.class,1);
				break;
			case 2:
				previewFrameListener(param,0,Camera.PreviewCallback.class,2);
				break;
				}
			}
			break;
		
		case setPreviewDisplay:
		case setPreviewTexture:
		case startPreview:
			///Log.d("Smarper-Debug", "Got method " + mMethod);
			break;
			
		//KO: add switch statement instead of if, add picture callback
		case takePicture:
			switch(getRestrictState(param)){//if (isRestricted(param))
			case 1:
				pictureTakenListener(param, 3, Camera.PictureCallback.class, 1); //int arg is the argument number we're interested in
				break;
			case 2:
				//param.setResult(null);
				pictureTakenListener(param, 3, Camera.PictureCallback.class, 2);
				break;
			}
			

		case stopPreview:
			//KO: Remove this
			///if (isRestricted(param, PrivacyManager.cMedia, "Camera.startPreview"))
			//	param.setResult(null);
			break;
		}
	}

	@Override
	protected void after(XParam param) throws Throwable {

		switch (mMethod) { //KO
			case open: //KO: Monitor when camera opens
				//Log.d("Smarper-debug", "Camera opened");
				isOpen = true;
				PrivacyManager.updateCameraStatus(isOpen);
				break;

			case release: //KO: Monitor when camera closes
				//Log.d("Smarper-debug", "Camera closed");
				isOpen = false;
				PrivacyManager.updateCameraStatus(isOpen);
				break;
		}
	}
	
	//KO: Add picture listener, like for location
	private void pictureTakenListener(XParam param, int arg, Class<?> interfaze, int restrictState) throws Throwable {
		
				// Create proxy
				ClassLoader cl = param.thisObject.getClass().getClassLoader();
				InvocationHandler ih = new OnPictureTakenHandler(Binder.getCallingUid(), param.args[arg], restrictState);
				Object proxy = Proxy.newProxyInstance(cl, new Class<?>[] { interfaze }, ih);

				param.args[arg] = proxy;
			}
	
	//KO: Like for location, add picture handler here
	private class OnPictureTakenHandler implements InvocationHandler {
		private int mUid;
		private Object mTarget;
		private int mRestrictState;

		public OnPictureTakenHandler(int uid, Object target, int restrictState) {
			mUid = uid;
			mTarget = target;
			mRestrictState = restrictState;
		}

		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if ("onPictureTaken".equals(method.getName())){
				if (mRestrictState == 1)
				args[0] = PrivacyManager.getObfuscatedPictureTaken((byte[]) args[0]);
				
				else if (mRestrictState == 2)
				args[0] = PrivacyManager.getDefacedPictureTaken((byte[]) args[0]);
			}
			return method.invoke(mTarget, args);
		}
	}

	
	//KO: For preview
	private void previewFrameListener(XParam param, int arg, Class<?> interfaze, int restrictState) throws Throwable {
		
		// Create proxy
		ClassLoader cl = param.thisObject.getClass().getClassLoader();
		InvocationHandler ih = new PreviewFrameHandler(Binder.getCallingUid(), param.args[arg], restrictState);
		Object proxy = Proxy.newProxyInstance(cl, new Class<?>[] { interfaze }, ih);

		param.args[arg] = proxy;
	}

	
	//KO: For preview
		private class PreviewFrameHandler implements InvocationHandler {
			private int mUid;
			private Object mTarget;
			private int mRestrictState;

			public PreviewFrameHandler(int uid, Object target, int restrictState) {
				mUid = uid;
				mTarget = target;
				mRestrictState = restrictState;
			}

			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				if ("onPreviewFrame".equals(method.getName())){ //(byte[] data, Camera camera)
					if (mRestrictState == 1)
					args[0] = PrivacyManager.getObfuscatedPreviewFrame((byte[]) args[0], (Camera) args[1]);
					
					else if (mRestrictState == 2)
					args[0] = PrivacyManager.getDefacedPreviewFrame((byte[]) args[0], (Camera) args[1]);
				}
				return method.invoke(mTarget, args);
			}
		}

	
}
