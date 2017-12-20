package biz.bokhorst.xprivacy;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;


/**
 * Created by olejnik on 07/09/15.
 */
public class ActivityTutorial extends FragmentActivity {

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tutorial);
        ViewPager pager = (ViewPager) findViewById(R.id.tutorialpager);
        pager.setAdapter(new SampleAdapter(getSupportFragmentManager()));

    }

}


 class SampleAdapter extends FragmentPagerAdapter {

    public SampleAdapter(FragmentManager mgr) {
        super(mgr);
    }

    @Override
    public int getCount() {
        return(4);
    }

    @Override
    public Fragment getItem(int position) {
        return(TutorialFragment.newInstance(position));
    }

     @Override
     public String getPageTitle(int position) {
         return(TutorialFragment.getTitle(position));
     }
}

