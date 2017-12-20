package biz.bokhorst.xprivacy;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;

public class PRestriction implements Parcelable {
	public int uid;
	public int id;
	public String restrictionName; //= method category
	public String methodName;
	public boolean restricted; //Use for static policy
	public int restrictState; //KO: 0 = allow, 1 = obfuscate, 2 = deny. i.e. decision
	public boolean asked; //Did we show the prompt?
	public int decision_type; //KO: 0 = static policy, 1 = asked user on demand, 2 = timeout, 3 = user modified, 4 = user cached, 5 = on demand to static
	public String extra; //method parameters
	public String value; //not used
	public long time; //decision_time
	public boolean debug; //not used
	public int cached_duration  = -1; //how long did the user decide to cache this decision for?
	public String package_name;    //JS: added this so that we can map the package_name to app icon in ActivityUsage
	//public boolean auto; //KO: was decision automatic? i.e. via ML
	// The extra is never needed in the result

	public PRestriction() {
	}

	public PRestriction(PRestriction other) {
		uid = other.uid;
		id = other.id;
		restrictionName = other.restrictionName;
		methodName = other.methodName;
		restricted = other.restricted;
		restrictState = other.restrictState; //KO
		asked = other.asked;
		decision_type = other.decision_type;
		extra = other.extra; //KO: why was it = null?
		value = other.value;
		time = other.time;
		debug = other.debug;
		cached_duration = other.cached_duration;
		package_name = other.package_name;
		//auto = other.auto;

	}

	public PRestriction(int _uid, String category, String method) {
		uid = _uid;
		restrictionName = category;
		methodName = method;
		restricted = false;
		restrictState = 0; //KO
		asked = false;
		decision_type = 0; //KO default static policy
		extra = null;
		value = null;
		time = 0;
		debug = false;
		cached_duration = 0;
		package_name = "";
		//auto = false;

	}

	public PRestriction(int _uid, String category, String method, boolean _restricted) {
		uid = _uid;
		restrictionName = category;
		methodName = method;
		restricted = _restricted;
		
		if (restricted) //KO
			restrictState = 2;
		else
			restrictState = 0;

		decision_type = 0; //KO

		asked = false;
		extra = null;
		value = null;
		time = 0;
		debug = false;
		cached_duration = 0;
		package_name = "";
		//auto = false;

	}

	public PRestriction(int _uid, String category, String method, boolean _restricted, boolean _asked) {
		uid = _uid;
		restrictionName = category;
		methodName = method;
		restricted = _restricted;
		
		if (restricted) //KO
			restrictState = 2;
		else
			restrictState = 0;
		
		asked = _asked;
		decision_type = 0; //KO
		extra = null;
		value = null;
		time = 0;
		debug = false;
		cached_duration = 0;
		package_name = "";
		//auto = false;

	}

	public static final Parcelable.Creator<PRestriction> CREATOR = new Parcelable.Creator<PRestriction>() {
		public PRestriction createFromParcel(Parcel in) {
			return new PRestriction(in);
		}

		public PRestriction[] newArray(int size) {
			return new PRestriction[size];
		}
	};

	private PRestriction(Parcel in) {
		readFromParcel(in);
	}

	@Override
	public void writeToParcel(Parcel out, int flags) {
		out.writeInt(uid);
		out.writeInt(id);

		out.writeInt(restrictionName == null ? 1 : 0);
		if (restrictionName != null)
			out.writeString(restrictionName);

		out.writeInt(methodName == null ? 1 : 0);
		if (methodName != null)
			out.writeString(methodName);

		out.writeInt(restricted ? 1 : 0);
		
		out.writeInt(restrictState); //KO
		
		out.writeInt(asked ? 1 : 0);

		out.writeInt(decision_type); //KO

		out.writeInt(extra == null ? 1 : 0);
		if (extra != null)
			out.writeString(extra);

		out.writeInt(value == null ? 1 : 0);
		if (value != null)
			out.writeString(value);

		out.writeLong(time);
		out.writeInt(debug ? 1 : 0);
		out.writeInt(cached_duration);
		out.writeString(package_name);       //JS
		//out.writeInt(auto ? 1 : 0);

	}

	public void readFromParcel(Parcel in) {
		uid = in.readInt();
		id = in.readInt();
		restrictionName = (in.readInt() > 0 ? null : in.readString());
		methodName = (in.readInt() > 0 ? null : in.readString());
		restricted = (in.readInt() > 0 ? true : false);
		restrictState = in.readInt(); //KO
		asked = (in.readInt() > 0 ? true : false);
		decision_type = (in.readInt()); //KO
		extra = (in.readInt() > 0 ? null : in.readString());
		value = (in.readInt() > 0 ? null : in.readString());
		time = in.readLong();
		debug = (in.readInt() > 0 ? true : false);
		cached_duration = (in.readInt());
		package_name = in.readString();
		//auto = (in.readInt() > 0 ? true : false);
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	@SuppressLint("DefaultLocale")
	public String toString() {
		return String.format("%d/%s(%s;%s) %s=%srestricted%s", uid, methodName, extra, value, restrictionName,
				(restricted ? "" : "!"), (asked ? "" : "?"));
	}
}
