package biz.bokhorst.xprivacy;

import android.content.Context;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by olejnik on 19/02/16.
 */
public class ActivityDataUpload extends AppCompatActivity {

    TextView dataUploadMessage;
    Button ChangeServerURL;
    String NewURL = "";
    final Context context = this;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.data_upload);

        //Initialize the TextView
        dataUploadMessage = (TextView) findViewById(R.id.data_upload_message);


        //JS: Whenever the user wants to change the URL of the server to which the data is periodically or manually uploaded
        ChangeServerURL = (Button) findViewById(R.id.changeServerURL);

        ChangeServerURL.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                //JS: Get changeurlserverprompt view
                LayoutInflater li = LayoutInflater.from(context);
                View promptsView = li.inflate(R.layout.changeurlserverprompt, null);

                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);

                //JS: Set changeurlserverprompt to alertdialog builder
                alertDialogBuilder.setView(promptsView);

                final EditText userInput = (EditText) promptsView.findViewById(R.id.NewServerURL);
                userInput.setText("");
                //JS: Create object of SharedPreferences and extract from it the last updated value of UploadDataURL
                //JS: if the initial value of UploadDataURL is null, we automatically set it to https://spism.epfl.ch/smarper/uploadData.php (our server)
                final SharedPreferences mPrefs = getSharedPreferences("SaveURL", 0);
                String LastURL = mPrefs.getString("UploadDataURL","https://spism.epfl.ch/smarper/uploadData.php");
                //JS: Set the EditText userInput to the last value written for the URL (it is by default our server)
                userInput.append(LastURL);


                // set dialog message
                alertDialogBuilder
                        .setCancelable(false)
                        .setPositiveButton("OK",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,int id) {
                                        //JS: Get user input and set NewURL equal to its value
                                        NewURL = userInput.getText().toString();       //JS: This is the new URL, as set by the iser
                                        Smarper.UploadDataURL = NewURL;    //JS: Update the value of UploadDataURL to the new URL value set by the user

                                        //JS: Editor for SharedPreferences
                                        SharedPreferences.Editor editor = mPrefs.edit();
                                        //JS: Save the value of NewURL, for future references
                                        editor.putString("UploadDataURL", NewURL);
                                        //JS: Commit the changes
                                        editor.commit();
                                    }
                                })
                        .setNegativeButton("Cancel",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,int id) {
                                        dialog.cancel();
                                    }
                                });

                // create alert dialog
                AlertDialog alertDialog = alertDialogBuilder.create();

                // show it
                alertDialog.show();
            }
        });

        //Fetch freshest values from db in order to display them
        DataUploadDisplayTask d = new DataUploadDisplayTask();
        d.execute((Void) null);
    }


    @Override
    protected void onStop(){
        super.onStop();
    }


    //KO: Do a manual data upload
    public void manualDataUpload(View v){

        //KO: John's code for checking Internet connectivity
        //JS: Checking WIFI Connectivity
        boolean connected = false;
        ConnectivityManager connectivityManager = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState() == NetworkInfo.State.CONNECTED) {
            //JS: We are connected to a network
            connected = true;
        }
        else connected = false;

        NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();          //JS: Checking Internet Access

        if (connected == true && netInfo != null && netInfo.isConnected()) {
            //JS: Just before uploading data to server, make sure that UploadDataURL has the most recent value for the server URL
            final SharedPreferences mPrefs = getSharedPreferences("SaveURL", 0);
            Smarper.UploadDataURL = mPrefs.getString("UploadDataURL","https://spism.epfl.ch/smarper/uploadData.php");

            //Do the data upload if connected to Internet
            new Smarper.SendDataToServer().execute(ActivityDataUpload.this);

            //Then, update the display
            DataUploadDisplayTask d = new DataUploadDisplayTask();
            d.execute((Void) null);
        } else {
            //Show a toast if no Internet or WiFi
            Toast t = Toast.makeText(ActivityDataUpload.this, "You must be connected to WiFi to do a data upload.".toString(), Toast.LENGTH_LONG);
            t.show();
        }

    }

    //KO: Task for getting data upload pointer value from db and number of records, in order to show the user
    private class DataUploadDisplayTask extends AsyncTask<Void, Void, Long[]> {

        @Override
        protected Long[] doInBackground(Void... params){

            Long[] pointerValues = new Long[4];
            long[] pointerStatusAndDate = null;
            long id;
            try {
                pointerStatusAndDate = PrivacyService.getClient().getDataUploadStats();
                id = PrivacyService.getClient().getMostRecentDecisionId();

                pointerValues[0] = pointerStatusAndDate[0]; //Data upload pointer
                pointerValues[1] = pointerStatusAndDate[1]; //Data upload status code
                pointerValues[2] = pointerStatusAndDate[2]; //Data upload timestamp
                pointerValues[3] = id; //Number of records

            } catch (RemoteException e){
                Log.e("Smarper-Error", "RemoteException when trying to read data upload pointer value");
            }

            return pointerValues;
        }


        @Override
        protected void onPostExecute(Long[] values){

            //KO: Show 0 if there is no pointer value, otherwise show pointer value - 1.
            TextView dataUploaded = (TextView) findViewById(R.id.data_uploaded_value);
            dataUploaded.setText(((values[0]==0) ? 0 : values[0]-1) + " records uploaded of " + values[3] + " total");

            Date date = new Date(values[2]);
            DateFormat df = SimpleDateFormat.getDateTimeInstance();
            String stringdate = df.format(date);

            switch(values[1].intValue()){
                case -1:
                    dataUploadMessage.setText(stringdate + ": Did not try uploading yet");
                    break;
                case 0:
                    dataUploadMessage.setText(stringdate + ": OK");
                    dataUploadMessage.setTextColor(Color.GREEN);
                    break;
                case 1:
                    dataUploadMessage.setText(stringdate + ": Upload failed");
                    break;
                case 2:
                    dataUploadMessage.setText(stringdate + ": Failed repeatedly, please contact us.");
                    dataUploadMessage.setTextColor(Color.RED);
                    break;
            }

        }
    }
}
