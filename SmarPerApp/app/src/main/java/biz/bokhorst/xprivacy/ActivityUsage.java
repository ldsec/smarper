package biz.bokhorst.xprivacy;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Process;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

public class ActivityUsage extends ActivityBase {

	private static String debugTagForTimerTask = "smarper-timer-task";
	public int CountAllow= 0;
	public int CountObf= 0;                //JS
	public int CountDeny= 0;
	public List<String> methodCategories = PrivacyManager.getRestrictions();
	public boolean[] methodCategoriesCheckbox = new boolean[methodCategories.size()];
	public String decisionCounts;                  //JS
	TextView decisionCountContainer;                  //JS
	public String decisionValue;                   //JS
	private List<String> checkedCategories = new ArrayList<String>();        //JS
	private List<String> checkedApps = new ArrayList<String>();
	public int [] checkedDecisions = null;
	public int[] checkedDecisionType = null;
	public int checkedTime = -1;
	private MyTimerTask myTask; 					//JS
	private boolean SwitchisChecked = false;        //JS
	public boolean currentlyFiltering = false;
	public boolean CheckBoxAllow = false;
	public boolean CheckBoxObfuscate = false;
	public boolean CheckBoxDeny = false;
	public boolean CheckBoxStatic = false;
	public boolean CheckBoxUser = true;            //JS: displayed by default
	public boolean CheckBoxUserCached = false;
	public boolean CheckBoxUserModified = true;		//JS: displayed by default
	public boolean CheckBoxTimeout = false;
	public boolean RadioButtonPastHour = false;
	public boolean RadioButtonDefaultTime = true;    //JS: By default, show the 1000 most recent decisions
	public boolean RadioButtonToday = false;
	public boolean RadioButtonPastDay = false;
	private Map<String, Boolean> appsCheckbox = new HashMap<String, Boolean>();         //JS: A map that maps application names to booleans indication whether their checkboxes are checked
	private boolean mAll = true;
	private int mUid;
	private String mRestrictionName;
	private UsageAdapter mUsageAdapter;
	private UsageAdapter UsageAdapterFilter;
	public static final String cUid = "Uid";
	public static final String cRestriction = "Restriction";

	List<PackageInfo> monitoredApps; //KO: The apps the user selected to monitor
	PackageManager pm; //KO: PackageManager for app names and icons

	private static ExecutorService mExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),
			new PriorityThreadFactory());

	private static class PriorityThreadFactory implements ThreadFactory {
		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r);
			t.setPriority(Thread.NORM_PRIORITY);
			return t;
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Check privacy service client
		if (!PrivacyService.checkClient())
			return;

		// Set layout
		setContentView(R.layout.usage_list);
		setSupportActionBar((Toolbar) findViewById(R.id.widgetToolbar)); //KO: new XPrivacy version

		// Get uid
		Bundle extras = getIntent().getExtras();
		mUid = (extras == null ? 0 : extras.getInt(cUid, 0));
		mRestrictionName = (extras == null ? null : extras.getString(cRestriction));

		int i = 0;
		while (i < methodCategories.size()){
			methodCategoriesCheckbox[i] = false;
			i++;
		}


		checkedCategories.clear();
		checkedApps.clear();

		// Show title
		updateTitle();

		//KO: Get list of apps and package manager
		monitoredApps = LcaSmarperPolicy.getToMonitor();
		pm = getPackageManager();

		// Start task to get usage data
		UsageTask usageTask = new UsageTask();
		usageTask.executeOnExecutor(mExecutor, (Object) null);

		// Up navigation
		getSupportActionBar().setDisplayHomeAsUpEnabled(true); //KO: new XPrivacy version

		Switch mySwitch = (Switch) findViewById(R.id.switch1);
		if (mySwitch.isChecked()){
			myTask = new MyTimerTask();
			Timer myTimer = new Timer();
			myTimer.schedule(myTask, 20 * 1000L, 20 * 1000L);
			Log.d(debugTagForTimerTask, "Switch is ON");
			SwitchisChecked = true;
		}
		else {
			Log.d(debugTagForTimerTask, "Switch is OFF, do not start timer task");
			SwitchisChecked = false;
		}
	}

	class MyTimerTask extends TimerTask {
		@Override
		public void run() {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
						optionRefresh();
						Log.d(debugTagForTimerTask, "Refreshing Logs View");
				}
			});
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (mUsageAdapter != null)
			mUsageAdapter.notifyDataSetChanged();
	}


	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (SwitchisChecked){
				myTask.cancel();
				Log.d(debugTagForTimerTask, "Destroying Activity and Canceling Timer Task");
			}
			else {
				Log.d(debugTagForTimerTask, "Destroying Activity without canceling timer task");
			}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		if (inflater != null && PrivacyService.checkClient()) {
			inflater.inflate(R.menu.usage, menu);
			return true;
		} else
			return false;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {                    //JS
		switch (item.getItemId()) {
			case R.id.menu_filter:
				optionFilter();
				return true;
			case R.id.menu_help:
				optionHelp();
				return true;
			case R.id.menu_refresh:          //JS
				optionRefresh();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	//JS: Filter the permissions
	private void optionFilter() {
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		final View view = inflater.inflate(R.layout.filter, null);

		if (CheckBoxAllow) {
			CheckBox Check = ((CheckBox) view.findViewById(R.id.decision_allow));
			Check.setChecked(true);
		}
		if (CheckBoxObfuscate) {
			CheckBox Check = ((CheckBox) view.findViewById(R.id.decision_obfuscate));
			Check.setChecked(true);
		}
		if (CheckBoxDeny) {
			CheckBox Check = ((CheckBox) view.findViewById(R.id.decision_deny));
			Check.setChecked(true);
		}
		if (CheckBoxStatic) {
			CheckBox Check = ((CheckBox) view.findViewById(R.id.decision_type_static));
			Check.setChecked(true);
		}
		if (CheckBoxUser) {
			CheckBox Check = ((CheckBox) view.findViewById(R.id.decision_type_user));
			Check.setChecked(true);
		}
		if (CheckBoxUserCached) {
			CheckBox Check = ((CheckBox) view.findViewById(R.id.decision_type_user_cached));
			Check.setChecked(true);
		}
		if (CheckBoxUserModified) {
			CheckBox Check = ((CheckBox) view.findViewById(R.id.decision_type_user_modified));
			Check.setChecked(true);
		}
		if (CheckBoxTimeout) {
			CheckBox Check = ((CheckBox) view.findViewById(R.id.decision_type_timeout));
			Check.setChecked(true);
		}

		if (RadioButtonDefaultTime){
			RadioButton button = (RadioButton) view.findViewById(R.id.default_time);
			button.setChecked(true);
		}
		else if (RadioButtonPastHour) {
			RadioButton button = (RadioButton) view.findViewById(R.id.past_hour);
			button.setChecked(true);
		}
		else if (RadioButtonToday) {
			RadioButton button = (RadioButton) view.findViewById(R.id.today);
			button.setChecked(true);
		}
		else if (RadioButtonPastDay) {
			RadioButton button = (RadioButton) view.findViewById(R.id.past_day);
			button.setChecked(true);
		}

		//JS: Filter by app category
		LinearLayout CategoryCont = (LinearLayout) view.findViewById(R.id.app_category_cont);
		int j=0;
		while (j < methodCategories.size()){
			View categoryItemView = inflater.inflate(R.layout.filter_category_item, null);
			CheckBox categoryCheckbox = (CheckBox) categoryItemView.findViewById(R.id.category_checkbox);
			categoryCheckbox.setText(methodCategories.get(j));
			CategoryCont.addView(categoryItemView);
			if (methodCategoriesCheckbox[j]) categoryCheckbox.setChecked(true);
			else categoryCheckbox.setChecked(false);
			j++;
		}

		//JS: Filter by app name
		LinearLayout appCont = (LinearLayout) view.findViewById(R.id.app_cont);
		for (PackageInfo i : monitoredApps) {
			View appItemView = inflater.inflate(R.layout.filter_app_item, null);
			//App icon
			ImageView AppIcon = (ImageView) appItemView.findViewById(R.id.app_icon);
			Drawable drawable = i.applicationInfo.loadIcon(pm);
			AppIcon.setImageDrawable(drawable);

			//App name
			CheckBox appCheckbox = (CheckBox) appItemView.findViewById(R.id.app_checkbox);
			appCheckbox.setText(i.applicationInfo.loadLabel(pm));
			appCont.addView(appItemView);
			if (appsCheckbox.get(i.applicationInfo.loadLabel(pm))) appCheckbox.setChecked(true);
			else appCheckbox.setChecked(false);
		}

		//JS: Build dialog
		final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(ActivityUsage.this);
		alertDialogBuilder.setTitle(R.string.menu_filter);
		alertDialogBuilder.setView(view);
		alertDialogBuilder.setPositiveButton(ActivityUsage.this.getString(android.R.string.ok),
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {

						int[] decisions = {-1, -1, -1};
						int[] decisionType = {-1, -1, -1, -1, -1};
						int time = -1;

						if (((CheckBox) view.findViewById(R.id.decision_allow)).isChecked()) {
							decisions[0] = 0;
							CheckBoxAllow = true;
						} else CheckBoxAllow = false;

						if (((CheckBox) view.findViewById(R.id.decision_obfuscate)).isChecked()) {
							decisions[1] = 1;
							CheckBoxObfuscate = true;
						} else CheckBoxObfuscate = false;

						if (((CheckBox) view.findViewById(R.id.decision_deny)).isChecked()) {
							decisions[2] = 2;
							CheckBoxDeny = true;
						} else CheckBoxDeny = false;


						if (((CheckBox) view.findViewById(R.id.decision_type_static)).isChecked()) {
							decisionType[0] = 0;
							CheckBoxStatic = true;
						} else CheckBoxStatic = false;

						if (((CheckBox) view.findViewById(R.id.decision_type_user)).isChecked()) {
							decisionType[1] = 1;
							CheckBoxUser = true;
						} else CheckBoxUser = false;

						if (((CheckBox) view.findViewById(R.id.decision_type_timeout)).isChecked()) {
							decisionType[2] = 2;
							CheckBoxTimeout = true;
						} else CheckBoxTimeout = false;

						if (((CheckBox) view.findViewById(R.id.decision_type_user_modified)).isChecked()) {
							decisionType[3] = 3;
							CheckBoxUserModified = true;
						} else CheckBoxUserModified = false;

						if (((CheckBox) view.findViewById(R.id.decision_type_user_cached)).isChecked()) {
							decisionType[4] = 4;
							CheckBoxUserCached = true;
						} else CheckBoxUserCached = false;


						if (((RadioButton) view.findViewById(R.id.past_hour)).isChecked()) {
							time = 0;
							RadioButtonPastHour = true;
						} else RadioButtonPastHour = false;

						if (((RadioButton) view.findViewById(R.id.today)).isChecked()) {
							time = 1;
							RadioButtonToday = true;
						} else RadioButtonToday = false;

						if (((RadioButton) view.findViewById(R.id.past_day)).isChecked()) {
							time = 2;
							RadioButtonPastDay = true;
						} else RadioButtonPastDay = false;

						if (((RadioButton) view.findViewById(R.id.default_time)).isChecked()) {
							time = -1;
							RadioButtonDefaultTime = true;
						} else RadioButtonDefaultTime = false;


						checkedDecisions = decisions;
						checkedDecisionType = decisionType;
						checkedTime = time;

						currentlyFiltering = true;
						GetFilteredDatafromDB(checkedDecisions, checkedDecisionType, checkedTime, checkedCategories, checkedApps);
					}
				});
		alertDialogBuilder.setNegativeButton(ActivityUsage.this.getString(android.R.string.cancel),
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
					}
				});

		// Show dialog
		AlertDialog alertDialog = alertDialogBuilder.create();
		alertDialog.show();
	}

	private void optionHelp() {
		final LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		final View view = inflater.inflate(R.layout.help, null);

		// Build dialog
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(ActivityUsage.this);
		alertDialogBuilder.setTitle(R.string.legend);
		alertDialogBuilder.setView(view);
		alertDialogBuilder.setPositiveButton(ActivityUsage.this.getString(android.R.string.ok),
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
					}
				});

		// Show dialog
		AlertDialog alertDialog = alertDialogBuilder.create();
		alertDialog.show();
	}

	//JS: Refresh the Usage list (fetch more recent logs from db)
	private void optionRefresh() {
		if (currentlyFiltering) GetFilteredDatafromDB(checkedDecisions, checkedDecisionType, checkedTime, checkedCategories, checkedApps);
		else{
			UsageTask usageTask = new UsageTask();
			usageTask.executeOnExecutor(mExecutor, (Object) null);
		}
	}

	//JS
	public void onCategoryCheckboxClicked(View view) {
		boolean checked = ((CheckBox) view).isChecked();
		String value  = ((CheckBox) view).getText().toString();
		String temp = value;
		value = "'"+value+"'";

		int i = 0; boolean stoploop = false;
		while (i < methodCategories.size() && !stoploop){
			if (methodCategories.get(i).equals(temp)) {
				stoploop = true;
			}
			else i++;
		}

		if (checked) {
			checkedCategories.add(value);
			methodCategoriesCheckbox[i] = true;
		} else {
			checkedCategories.remove(value);
			methodCategoriesCheckbox[i] = false;
		}
	}

	//JS
	public void onAppCheckboxClicked(View view) {
		boolean checked = ((CheckBox) view).isChecked();
		String value  = ((CheckBox) view).getText().toString();
		String temp = value;
		value = "'"+value+"'";

		if (checked) {
			checkedApps.add(value);
			appsCheckbox.put(temp,true);

		} else {
			checkedApps.remove(value);
			appsCheckbox.put(temp,false);
		}
	}

	//JS: Get the filtered data from Database
	public void GetFilteredDatafromDB(int[] checkedDecisions, int[] checkedDecisionType, int checkedTime, List<String> checkedCategories, List<String> checkedApps){      //JS
		List<PRestriction> listFilterData = new ArrayList<PRestriction>();
		for (PRestriction usageData : PrivacyManager.GetFilteredData(checkedDecisions, checkedDecisionType, checkedTime, checkedCategories, checkedApps)) {
			listFilterData.add(usageData);
		}

		CountDeny = 0;
		CountAllow = 0;                   //JS: Reset the counts for every decision before displaying the filtered list
		CountObf = 0;

		for (int i = 0; i < listFilterData.size(); i++) {
			if (listFilterData.get(i).restrictState == 0)      CountAllow++;
			else if (listFilterData.get(i).restrictState == 1) CountObf++;       //JS: Get the counts for every decision
			else if (listFilterData.get(i).restrictState == 2) CountDeny++;
		}

		UsageAdapterFilter = new UsageAdapter(ActivityUsage.this, R.layout.usage_list_item, listFilterData);
		final ListView simple_list = (ListView) findViewById(R.id.simple_list);
		simple_list.setAdapter(UsageAdapterFilter);
		UsageAdapterFilter.getFilter().filter(Boolean.toString(mAll));

		decisionCounts = String.valueOf(CountAllow) + "/" + String.valueOf(CountObf) +  "/" + String.valueOf(CountDeny);
		decisionCountContainer = (TextView) findViewById(R.id.counts);           //JS: Display the counts for every decision, after getting the filtered list
		decisionCountContainer.setText(decisionCounts);

	}

	//JS
	private class UsageTask extends AsyncTask<Object, Object, List<PRestriction>> {
		@Override
		protected List<PRestriction> doInBackground(Object... arg0) {
			for (PackageInfo i : monitoredApps) {
				appsCheckbox.put(i.applicationInfo.loadLabel(pm).toString(),false);
			}
			
			List<PRestriction> listUsageData = new ArrayList<PRestriction>();
			for (PRestriction usageData : PrivacyManager.getFullUsageList(mUid, mRestrictionName)) //KO: Changed to use our method here instead of getUsageList
				listUsageData.add(usageData);
			return listUsageData;
		}

		@Override
		protected void onPostExecute(List<PRestriction> listUsageData) {
			if (!ActivityUsage.this.isFinishing()) {

				mUsageAdapter = new UsageAdapter(ActivityUsage.this, R.layout.usage_list_item, listUsageData);
				final ListView simple_list = (ListView) findViewById(R.id.simple_list);
				simple_list.setAdapter(mUsageAdapter);
				mUsageAdapter.getFilter().filter(Boolean.toString(mAll));

				// list item click
				simple_list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
					@Override
					public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
						//JS: ID
						TextView idTextView = (TextView) view.findViewById(R.id._id);
						int idValue = Integer.parseInt(idTextView.getText().toString());

						//JS: Decision
						decisionValue = null;
						ImageView imageView = (ImageView) view.findViewById(R.id.imgDecision);
						if (imageView.getDrawable().getConstantState().equals(ContextCompat.getDrawable(ActivityUsage.this, R.drawable.allow).getConstantState())) {
							decisionValue = "allow";
						} else if (imageView.getDrawable().getConstantState().equals(ContextCompat.getDrawable(ActivityUsage.this, R.drawable.obfuscate).getConstantState())) {
							decisionValue = "obfuscate";
						} else if (imageView.getDrawable().getConstantState().equals(ContextCompat.getDrawable(ActivityUsage.this, R.drawable.deny).getConstantState())) {
							decisionValue = "deny";
						}

						ImageView decisionTypeImage = (ImageView) view.findViewById(R.id.imgDecisionType);  //JS: Cannot change decision if it is not user based (user, user_cache or user_modified)
						if (decisionTypeImage.getDrawable().getConstantState().equals(ContextCompat.getDrawable(ActivityUsage.this, R.drawable.user).getConstantState()) ||
								decisionTypeImage.getDrawable().getConstantState().equals(ContextCompat.getDrawable(ActivityUsage.this, R.drawable.user_cache).getConstantState()) ||
								decisionTypeImage.getDrawable().getConstantState().equals(ContextCompat.getDrawable(ActivityUsage.this, R.drawable.user_modified).getConstantState())) {
							DecisionChange(position, decisionValue, idValue);
						}
						else {
							new AlertDialog.Builder(ActivityUsage.this)
									.setTitle("Change Decision")
									.setMessage("This is not a user decision, it cannot be changed.")
									.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
										public void onClick(DialogInterface dialog, int which) {
										}
									})
									.setIcon(android.R.drawable.ic_dialog_alert)
									.show();
						}
						return true;
					}

					public void DecisionChange(final int position, final String decisionValue, final int idValue) {
						final LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
						final View view = inflater.inflate(R.layout.decision_list, null);

						//JS: Disable current radio button value
						int resID = getResources().getIdentifier(decisionValue, "id", getPackageName());
						final RadioButton currentDecision = (RadioButton) view.findViewById(resID);
						currentDecision.setEnabled(false);

						final RadioGroup decisionRadioGroup = (RadioGroup) view.findViewById(R.id.decision_radio_group);

						AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(ActivityUsage.this);
						alertDialogBuilder.setTitle(R.string.change_decision);
						alertDialogBuilder.setView(view);
						alertDialogBuilder.setPositiveButton(ActivityUsage.this.getString(android.R.string.ok),
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {

										if (decisionRadioGroup.getCheckedRadioButtonId() != -1) {
											int id = decisionRadioGroup.getCheckedRadioButtonId();
											View radioButton = decisionRadioGroup.findViewById(id);
											int radioId = decisionRadioGroup.indexOfChild(radioButton);

											PrivacyManager.UpdateDBwithNewDecision(radioId, idValue);     //JS: Update the database with the new decision value

											CountAllow = 0;
											CountObf = 0;               //JS: Reset the counts for every decision before refreshing the list
											CountDeny = 0;

											optionRefresh();			//JS: Refresh the list (or filtered list) after the decision change
											//JS: No need to change the decision type and its icon because we are refreshing the usage list (or refreshed list) right after the decision change
										}
									}
								});


						alertDialogBuilder.setNegativeButton(ActivityUsage.this.getString(android.R.string.cancel),
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
									}
								});

						AlertDialog alertDialog = alertDialogBuilder.create();
						alertDialog.show();
					}
				});


			//JS: This method shows us a popup activity with info on a certain entry, when it is long pressed
				simple_list.setOnItemClickListener(new AdapterView.OnItemClickListener() {                  //JS
					@Override
					public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
						TextView idTextView = (TextView) view.findViewById(R.id._id);
						int idValue = Integer.parseInt(idTextView.getText().toString());
						DisplayList(idValue);         //JS: DisplayList fetches all the permission info from database and displays them
					}

					public void DisplayList(int idValue) {
						final LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
						final View view = inflater.inflate(R.layout.permission_info, null);

						List<String> InfoList = PrivacyManager.PermissionInfoOnClick(idValue);
						ListView lv = (ListView) view.findViewById(R.id.Permission_Info);
						ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(ActivityUsage.this, R.layout.permission_info_entry, InfoList);
						lv.setAdapter(arrayAdapter);

						// Build dialog
						AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(ActivityUsage.this);
						alertDialogBuilder.setTitle(R.string.permission_info);
						alertDialogBuilder.setView(view);
						alertDialogBuilder.setPositiveButton(ActivityUsage.this.getString(android.R.string.ok),
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
										//JS: Do Nothing
									}
								});

						// Show dialog
						AlertDialog alertDialog = alertDialogBuilder.create();
						alertDialog.show();
					}
				});

				super.onPostExecute(listUsageData);

				CountAllow = 0;
				CountObf = 0;               //JS: Reset the counts for every decision before displaying the list
				CountDeny = 0;


				for (int i = 0; i < listUsageData.size(); i++) {
					if (listUsageData.get(i).restrictState == 0) CountAllow++;
					else if (listUsageData.get(i).restrictState == 1) CountObf++;       //JS: Get the counts for every decision
					else if (listUsageData.get(i).restrictState == 2) CountDeny++;
				}


				decisionCounts = String.valueOf(CountAllow) + "/" + String.valueOf(CountObf) +  "/" + String.valueOf(CountDeny);
				decisionCountContainer = (TextView) findViewById(R.id.counts);           //JS: Display the counts for every decision, after fetching the list from DB
				decisionCountContainer.setText(decisionCounts);


				Switch mySwitch = (Switch) findViewById(R.id.switch1);                       //JS: Check (Detect) for switch changes
				mySwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
						public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
							if (isChecked) {
								myTask = new MyTimerTask();
								Timer myTimer = new Timer();
								myTimer.schedule(myTask, 20 * 1000L, 20 * 1000L);
								SwitchisChecked = true;
								Log.d(debugTagForTimerTask, "Switch is ON, in Async");
							} else {
								myTask.cancel();
								SwitchisChecked = false;
								Log.d(debugTagForTimerTask, "Switch is OFF, in Async, Canceling timer task");
							}
						}
					});
			}
		}
	}

	// Adapters
	private class UsageAdapter extends ArrayAdapter<PRestriction> {
		private boolean mHasProLicense = false;
		private List<PRestriction> mListUsageData;
		private LayoutInflater mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		public UsageAdapter(Context context, int textViewResourceId, List<PRestriction> objects) {
			super(context, textViewResourceId, objects);
			mHasProLicense = (Util.hasProLicense(ActivityUsage.this) != null);
			mListUsageData = new ArrayList<PRestriction>();
			mListUsageData.addAll(objects);
		}


		@Override
		public Filter getFilter() {
			return new UsageFilter();
		}

		private class UsageFilter extends Filter {
			public UsageFilter() {
			}

			@Override
			protected FilterResults performFiltering(CharSequence constraint) {
				FilterResults results = new FilterResults();

				// Get argument
				boolean all = Boolean.parseBoolean((String) constraint);

				// Match applications
				List<PRestriction> lstResult = new ArrayList<PRestriction>();
				for (PRestriction usageData : UsageAdapter.this.mListUsageData) {
					if (all ? true : usageData.restricted)
						lstResult.add(usageData);
				}

				synchronized (this) {
					results.values = lstResult;
					results.count = lstResult.size();
				}
				return results;
			}

			@Override
			@SuppressWarnings("unchecked")
			protected void publishResults(CharSequence constraint, FilterResults results) {
				clear();
				if (results.values == null){
					notifyDataSetInvalidated();
				}
				else {
					addAll((ArrayList<PRestriction>) results.values);
					notifyDataSetChanged();
				}
			}
		}

		private class ViewHolder {
			private View row;
			private int position;
			public TextView time;
			public ImageView AppIcon;
			public ImageView decisionImage;
			public ImageView decisionTypeImage;                //JS
			public TextView methodCategory;
			public TextView method;
			public TextView id;

			public ViewHolder(View theRow, int thePosition) {
				row = theRow;
				position = thePosition;
				time = (TextView) row.findViewById(R.id.time);				//JS
				AppIcon = (ImageView) row.findViewById(R.id.AppIcon);
				decisionImage = (ImageView) row.findViewById(R.id.imgDecision);
				decisionTypeImage = (ImageView) row.findViewById(R.id.imgDecisionType);
				methodCategory = (TextView) row.findViewById(R.id.methodCategory);
				method = (TextView) row.findViewById(R.id.method);
				id = (TextView) row.findViewById(R.id._id);
			}
		}

		private class HolderTask extends AsyncTask<Object, Object, Object> {
			private int position;
			private ViewHolder holder;
			private PRestriction usageData;
			private Drawable icon = null;
			private boolean system;
			private Hook hook;

			public HolderTask(int thePosition, ViewHolder theHolder, PRestriction theUsageData) {
				position = thePosition;
				holder = theHolder;
				usageData = theUsageData;
			}

			@Override
			protected Object doInBackground(Object... params) {
				if (usageData != null) {
					ApplicationInfoEx appInfo = new ApplicationInfoEx(ActivityUsage.this, usageData.uid);
					String package_Name = usageData.package_name;
					Drawable drawable = appInfo.getIcon(ActivityUsage.this); //appPackNameIcons.get(package_Name);
					icon = drawable;
					system = appInfo.isSystem();
					hook = PrivacyManager.getHook(usageData.restrictionName, usageData.methodName);
					return holder;
				}
				return null;
			}

			@Override
			protected void onPostExecute(Object result) {
				if (holder.position == position && result != null) {
					if (system || (hook != null && hook.isDangerous()))
						holder.row.setBackgroundColor(getResources().getColor(getThemed(R.attr.color_dangerous)));
					else
						holder.row.setBackgroundColor(Color.TRANSPARENT);
					holder.AppIcon.setImageDrawable(icon);             //JS
					holder.AppIcon.setVisibility(View.VISIBLE);             //JS

					View.OnClickListener clickListener = new View.OnClickListener() {
						@Override
						public void onClick(View view) {
							PRestriction usageData = mUsageAdapter.getItem(position);
							Intent intent = new Intent(ActivityUsage.this, ActivityApp.class);
							intent.putExtra(ActivityApp.cUid, usageData.uid);
							intent.putExtra(ActivityApp.cRestrictionName, usageData.restrictionName);
							intent.putExtra(ActivityApp.cMethodName, usageData.methodName);
							startActivity(intent);
						}
					};

					View.OnLongClickListener longClickListener = new View.OnLongClickListener() {
						@Override
						public boolean onLongClick(View view) {
							int userId = Util.getUserId(Process.myUid());
							final PRestriction usageData = mUsageAdapter.getItem(position);
							final Hook hook = PrivacyManager.getHook(usageData.restrictionName, usageData.methodName);

							boolean isApp = PrivacyManager.isApplication(usageData.uid);
							boolean odSystem = PrivacyManager.getSettingBool(userId,
									PrivacyManager.cSettingOnDemandSystem, false);
							final boolean wnomod = PrivacyManager.getSettingBool(usageData.uid,
									PrivacyManager.cSettingWhitelistNoModify, false);

							if ((isApp || odSystem) && hook != null && hook.whitelist() != null
									&& usageData.extra != null) {
								if (Util.hasProLicense(ActivityUsage.this) == null)
									Util.viewUri(ActivityUsage.this, ActivityMain.cProUri);
								else {
									AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(ActivityUsage.this);
									alertDialogBuilder.setTitle(R.string.menu_whitelists);
									alertDialogBuilder.setMessage(usageData.restrictionName + "/"
											+ usageData.methodName + "(" + usageData.extra + ")");
									alertDialogBuilder.setIcon(getThemed(R.attr.icon_launcher));
									alertDialogBuilder.setPositiveButton(getString(R.string.title_deny),
											new DialogInterface.OnClickListener() {
												@Override
												public void onClick(DialogInterface dialog, int which) {
													// Deny
													PrivacyManager.setSetting(usageData.uid, hook.whitelist(),
															usageData.extra, Boolean.toString(false));
													if (!wnomod)
														PrivacyManager.updateState(usageData.uid);
												}
											});
									alertDialogBuilder.setNeutralButton(getString(R.string.title_allow),
											new DialogInterface.OnClickListener() {
												@Override
												public void onClick(DialogInterface dialog, int which) {
													// Allow
													PrivacyManager.setSetting(usageData.uid, hook.whitelist(),
															usageData.extra, Boolean.toString(true));
													if (!wnomod)
														PrivacyManager.updateState(usageData.uid);
												}
											});
									alertDialogBuilder.setNegativeButton(getString(android.R.string.cancel),
											new DialogInterface.OnClickListener() {
												@Override
												public void onClick(DialogInterface dialog, int which) {
												}
											});
									AlertDialog alertDialog = alertDialogBuilder.create();
									alertDialog.show();
								}
								return true;
							} else
								return false;
						}
					};
				}
			}
		}


		@Override
		@SuppressLint("InflateParams")
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			if (convertView == null) {
				convertView = mInflater.inflate(R.layout.usage_list_item, null);
				holder = new ViewHolder(convertView, position);
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
				holder.position = position;
			}

			// Get data
			PRestriction usageData = getItem(position);

			// Build entry
			holder.row.setBackgroundColor(Color.TRANSPARENT);

			Date date = new Date(usageData.time);
			SimpleDateFormat format = new SimpleDateFormat("MM-dd H:mm:ss", Locale.ROOT);
			holder.time.setText(format.format(date));            //JS: time instead of TVtime

			holder.id.setText("" + usageData.id);  //JS
			//holder.imgIcon.setVisibility(View.VISIBLE); //KO: Icon was invisible
			//KO: Use a switch statement to deal with 3 cases, allow, deny, obfuscate
			switch (usageData.restrictState){
				//JS: Display icon for allow, deny, obfuscate
				case 0:
					holder.decisionImage.setImageDrawable(ContextCompat.getDrawable(ActivityUsage.this, getResources().getIdentifier("drawable/" + "allow", "drawable", getPackageName())));
					break;
				case 1:
					holder.decisionImage.setImageDrawable(ContextCompat.getDrawable(ActivityUsage.this, getResources().getIdentifier("drawable/" + "obfuscate", "drawable", getPackageName())));
					break;
				case 2:
					holder.decisionImage.setImageDrawable(ContextCompat.getDrawable(ActivityUsage.this, getResources().getIdentifier("drawable/" + "deny", "drawable", getPackageName())));
					break;
			}

			if(usageData.decision_type == 0) holder.decisionTypeImage.setImageDrawable(ContextCompat.getDrawable(ActivityUsage.this, getResources().getIdentifier("drawable/" + "stat", "drawable", getPackageName())));
			else if(usageData.decision_type == 1) holder.decisionTypeImage.setImageDrawable(ContextCompat.getDrawable(ActivityUsage.this, getResources().getIdentifier("drawable/" + "user", "drawable", getPackageName())));
			else if(usageData.decision_type == 2) holder.decisionTypeImage.setImageDrawable(ContextCompat.getDrawable(ActivityUsage.this, getResources().getIdentifier("drawable/" + "timeout", "drawable", getPackageName())));
			else if(usageData.decision_type == 3) holder.decisionTypeImage.setImageDrawable(ContextCompat.getDrawable(ActivityUsage.this, getResources().getIdentifier("drawable/" + "user_modified", "drawable", getPackageName())));
			else if(usageData.decision_type == 4) holder.decisionTypeImage.setImageDrawable(ContextCompat.getDrawable(ActivityUsage.this, getResources().getIdentifier("drawable/" + "user_cache", "drawable", getPackageName())));

			holder.methodCategory.setText(String.format("%s", usageData.restrictionName));//("%s/%s", usageData.restrictionName, usageData.methodName)); //KO: Hide method name for now
			holder.method.setText(String.format("%s", usageData.methodName));            //JS

			// update
			new HolderTask(position, holder, usageData).executeOnExecutor(mExecutor, (Object) null);
			return convertView;
		}
	}

	// Helpers

	private void updateTitle() {
		if (mUid == 0) {
			// Get statistics
			long count = 0;
			long restricted = 0;
			double persec = 0;
			try {
				@SuppressWarnings("rawtypes")
				Map statistics = PrivacyService.getClient().getStatistics();
				count = (Long) statistics.get("restriction_count");
				restricted = (Long) statistics.get("restriction_restricted");
				long uptime = (Long) statistics.get("uptime_milliseconds");
				persec = (double) count / (uptime / 1000);
			} catch (Throwable ex) {
				Util.bug(null, ex);
			}
		}
	}
}