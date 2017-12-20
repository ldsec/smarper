package biz.bokhorst.xprivacy;
import android.util.Log;
import android.view.KeyEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;


/**
 * Created by olejnik on 09/10/15.
 * To detect when the user is typing/the keyboard is shown.
 */
public class XView extends XHook {

    private Methods mMethod;
    private static boolean isTyping = false;

    private XView(Methods method, String restrictionName) {
        super(restrictionName, method.name(), "View." + method.name());
        mMethod = method;
    }

    public String getClassName() {
        return "android.view.View";
    }

    //https://developer.android.com/reference/android/view/View.html#onKeyPreIme%28int,%20android.view.KeyEvent%29
    private enum Methods {
        onKeyPreIme
    };

    //How to do?
    public static List<XHook> getInstances() {
        List<XHook> listHook = new ArrayList<XHook>();
        for (Methods me : Methods.values())
            listHook.add(new XView(me, "identification")); //method, restrictionName -- what should be the restrictionName for these?
        return listHook;
    }


    @Override
    protected void before(XParam param) throws Throwable {

        //Do nothing
    }

    @Override
    protected void after(XParam param) throws Throwable {

        switch (mMethod) {

            case onKeyPreIme:

                int keyCode = (int) param.args[0];
                KeyEvent keyEvent = (KeyEvent) param.args[1];

                //http://stackoverflow.com/questions/6570974/edittext-with-soft-keyboard-and-back-button
                if (keyCode == KeyEvent.KEYCODE_BACK && keyEvent.getAction() == KeyEvent.ACTION_UP) {
                    //Log.d("Smarper-debug", "XView: typing status canceled");
                    isTyping = false;
                }
                break;


        }

        //We can't know in this class whether the user is typing... Have the PrivacyManager decide when to update the service.
           PrivacyManager.toggleTypingStatusIfTyping();
    }
}
