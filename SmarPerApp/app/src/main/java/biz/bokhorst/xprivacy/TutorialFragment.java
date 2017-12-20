package biz.bokhorst.xprivacy;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by olejnik on 07/09/15.
 */
public class TutorialFragment extends Fragment {

    public static String[] pageTitles = new String[]{"Prompting", "Obfuscation", "Cache", "Logs view"};
    public static int[] fileName_ids = new int[]{R.raw.tutorial_0, R.raw.tutorial_1, R.raw.tutorial_2, R.raw.tutorial_3};
    public static final String KEY_POSITION = "KEY_POSITION";

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        // The last two arguments ensure LayoutParams are inflated
        // properly.
        View rootView = inflater.inflate(
                R.layout.tutorial_fragment, container, false);
        Bundle args = getArguments();

        WebView wv = (WebView) rootView.findViewById(R.id.tutorial_fragment_textview);

        int position = args.getInt(KEY_POSITION, 0);

        InputStream is = getResources().openRawResource(fileName_ids[position]);
        InputStreamReader inputreader = new InputStreamReader(is);
        BufferedReader buffreader = new BufferedReader(inputreader);
        String line;
        StringBuilder text = new StringBuilder();

        try {
            while (( line = buffreader.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
        } catch (IOException e) {
            return null;
        }

        wv.loadDataWithBaseURL("file:///android_asset/", text.toString(), "text/html", null, null);

        return rootView;
    }

    static TutorialFragment newInstance(int position) {
        TutorialFragment frag = new TutorialFragment();
        Bundle args=new Bundle();
        args.putInt(KEY_POSITION, position);
        frag.setArguments(args);
        return(frag);
    }

    public static String getTitle(int position){
        return pageTitles[position];
    }
}
