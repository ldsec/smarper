package biz.bokhorst.xprivacy;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;


// Class created by JS
public class appPackNameAndIcon implements Parcelable {
    public String app_PackageName;
    public String app_Name;
    public int app_Icon_size;
    public byte[] app_Icon;
    public int app_uid;

    public appPackNameAndIcon() {
        app_PackageName = "";
        app_Name = "";
        app_Icon_size = 0;
        app_Icon = null;
        app_uid = 0;
    }

    public appPackNameAndIcon(appPackNameAndIcon other) {
        app_PackageName = other.app_PackageName;
        app_Name = other.app_Name;
        app_Icon_size = other.app_Icon_size;
        app_Icon = other.app_Icon;
        app_uid = other.app_uid;
    }

    public appPackNameAndIcon(String app_PackageName_2,  String app_Name_2, byte[] app_Icon_2, int app_Icon_size_2, int app_uid_2) {
        app_PackageName = app_PackageName_2;
        app_Name = app_Name_2;
        app_Icon_size = app_Icon_size_2;
        app_Icon = app_Icon_2;
        app_uid = app_uid_2;
    }

    public static final Parcelable.Creator<appPackNameAndIcon> CREATOR = new Parcelable.Creator<appPackNameAndIcon>() {
        public appPackNameAndIcon createFromParcel(Parcel in) {
            return new appPackNameAndIcon(in);
        }

        public appPackNameAndIcon[] newArray(int size) {
            return new appPackNameAndIcon[size];
        }
    };

    private appPackNameAndIcon(Parcel in) {
        readFromParcel(in);
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(app_PackageName);
        out.writeString(app_Name);
        out.writeInt(app_Icon_size);
        out.writeByteArray(app_Icon);
        out.writeInt(app_uid);
    }

    public void readFromParcel(Parcel in) {
        app_PackageName = in.readString();
        app_Name = in.readString();
        app_Icon_size = in.readInt();
        app_Icon = new byte[app_Icon_size];
        in.readByteArray(app_Icon);
        app_uid = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @SuppressLint("DefaultLocale")
    public String toString() {
        return String.format("%d/%s(%s;%s) %s=%package_name%s", app_PackageName);
    }

}