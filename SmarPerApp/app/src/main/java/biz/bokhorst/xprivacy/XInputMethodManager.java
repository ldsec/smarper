package biz.bokhorst.xprivacy;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;


/**
 * Created by olejnik on 09/10/15.
 * To detect when the user is typing/the keyboard is shown.
 */
public class XInputMethodManager extends XHook {

    private Methods mMethod;
    private static boolean isTyping = false;

    private XInputMethodManager(Methods method, String restrictionName) {
        super(restrictionName, method.name(), "InputMethodManager." + method.name());
        mMethod = method;
    }

    public String getClassName() {
        return "android.view.inputmethod.InputMethodManager";
    }

    //https://developer.android.com/reference/android/view/inputmethod/InputMethodManager.html#hideSoftInputFromWindow%28android.os.IBinder,%20int%29
    //https://developer.android.com/reference/android/view/inputmethod/InputMethodManager.html#hideSoftInputFromWindow%28android.os.IBinder,%20int,%20android.os.ResultReceiver%29
    //https://developer.android.com/reference/android/view/inputmethod/InputMethodManager.html#showSoftInput%28android.view.View,%20int,%20android.os.ResultReceiver%29
    //https://developer.android.com/reference/android/view/inputmethod/InputMethodManager.html#showSoftInput%28android.view.View,%20int%29
    //https://developer.android.com/reference/android/view/inputmethod/InputMethodManager.html#toggleSoftInput%28int,%20int%29
    //https://developer.android.com/reference/android/view/inputmethod/InputMethodManager.html#toggleSoftInputFromWindow%28android.os.IBinder,%20int,%20int%29
    private enum Methods {
        hideSoftInputFromWindow, showSoftInput, toggleSoftInput, toggleSoftInputFromWindow,
    };

    public static List<XHook> getInstances() {
        List<XHook> listHook = new ArrayList<XHook>();
        for (Methods me : Methods.values())
            listHook.add(new XInputMethodManager(me, "identification")); //method, restrictionName -- what should be the restrictionName for these?
        return listHook;
    }


    @Override
    protected void before(XParam param) throws Throwable {

        //Do nothing
    }

    @Override
    protected void after(XParam param) throws Throwable {

        switch (mMethod) {

            case hideSoftInputFromWindow:
                isTyping = false;
                //Log.d("smarper-debug", "Got hideSoftInput");
                break;

            case showSoftInput:
                isTyping = true;
                //Log.d("smarper-debug", "Got showSoftInput");
                break;

            case toggleSoftInput:
                isTyping = !isTyping;
                //Log.d("smarper-debug", "got toggleSoftInput");
                break;

            case toggleSoftInputFromWindow:
                isTyping = !isTyping;
                //Log.d("smarper-debug", "got toggleSoftInputFromWindow");
                break;
        }


            PrivacyManager.updateTypingStatus(isTyping);
    }
}
