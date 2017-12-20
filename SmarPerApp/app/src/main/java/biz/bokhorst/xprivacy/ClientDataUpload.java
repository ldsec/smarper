package biz.bokhorst.xprivacy;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.util.JsonReader;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


// Class created by JS

public class ClientDataUpload {

    private int maxRecords = 5000;
    private static String debugTag = "smarper-dataupload";
    public ClientDataUpload() {}

    public int ClientDataUpload (ReentrantReadWriteLock mLockUsage, SQLiteDatabase dbUsage, String url, ReentrantReadWriteLock dbLock, SQLiteDatabase smarperDb)throws RemoteException {

        int dataUploadPointer = isDataUploadPointerSet(dbLock, smarperDb);
        if (dataUploadPointer == -1) {
            dataUploadPointer = setDataUploadPointer(mLockUsage, dbUsage, dbLock, smarperDb);
        }

        checkAndSetDataUploadStats(dbLock,smarperDb); //KO: For checking and setting the DataUploadStatus and DataUploadTimestamp fields

        JsonIntDataClass IntermediateResult = FetchDataFromDBandFillItIntoJsonArray (mLockUsage, dbUsage, dataUploadPointer);
        //JS: I created this class JsonIntDataClass to be able to return a JsonArray (Data Sent) and an integer (the data upload pointer)
        //JS: from one call of the FetchDataFromDBandFillItIntoJsonArray method

        int[] returnedPointerAndRowsReceivedByServer = ConnectToServerandSendData(IntermediateResult.PointerForData, url, IntermediateResult.SentData);

        if (returnedPointerAndRowsReceivedByServer[1] > 0) {
            UpdateValueOfPointerInDatabase(returnedPointerAndRowsReceivedByServer[0], dbLock, smarperDb);

        }
        //JS: Only update if pointer value has changed and data was successfully sent, i.e. NumberOfReceivedRows is positive
        //JS: Takes Pointer as first parameter
        //JS: if NumberOfReceivedRows = -5, then could not connect to server, potential exception
        //JS: if NumberOfReceivedRows = -1, then could connect to server but server returned -1 (Http_code = 200), so error on server side
        //JS: if NumberOfReceivedRows = -2, then could connect to server and server returned a response with HTTP_code != 200

        return returnedPointerAndRowsReceivedByServer[1];
        //JS: return the number of rows received by server, if -1 then error occurred on server side, if -2 then client request was invalid (http_code != 200), if -5 then connection error or exception
        //JS: else return the positive integer representing the number of rows
        //JS: sent to and received by the server
    }


    public void checkAndSetDataUploadStats(ReentrantReadWriteLock dbLock, SQLiteDatabase db) throws RemoteException {

        try {
            dbLock.readLock().lock();
            try {
                db.beginTransaction();
                try {

                    //Status field
                    Cursor cursor;
                    cursor = db.query("setting", new String[]{"value"}, "name='DataUploadStatus'", null, null, null, null, null);
                    if (cursor == null || cursor.getCount() == 0) {
                        ContentValues dataUploadStatus = new ContentValues();
                        dataUploadStatus.put("uid", "" + 0);
                        dataUploadStatus.put("name", "DataUploadStatus");
                        dataUploadStatus.put("value", -1);

                        long id = db.insert("setting", null, dataUploadStatus);

                        if (id == -1){
                            Log.e(debugTag, "Error when setting DataUploadStatus field in database!");
                        }
                    }


                    //Timestamp field
                    Cursor cursor2;
                    cursor2 = db.query("setting", new String[]{"value"}, "name='DataUploadTimestamp'", null, null, null, null, null);
                    if (cursor2 == null || cursor2.getCount() == 0) {
                        ContentValues dataUploadTimestamp = new ContentValues();
                        dataUploadTimestamp.put("uid", "" + 0);
                        dataUploadTimestamp.put("name", "DataUploadTimestamp");
                        dataUploadTimestamp.put("value", new Date().getTime());

                        long id = db.insert("setting", null, dataUploadTimestamp);

                        if (id == -1){
                            Log.e(debugTag, "Error when setting DataUploadTimestamp field in database!");
                        }
                    }

                    db.setTransactionSuccessful();

                } catch (Exception e) {
                    Log.e(debugTag, "Exception checking and setting data upload stats:");
                    e.printStackTrace();
                }
            } finally {
                db.endTransaction();
            }
        } finally {
            dbLock.readLock().unlock();
        }

    }

    public int isDataUploadPointerSet(ReentrantReadWriteLock dbLock, SQLiteDatabase db) throws RemoteException {

        int PointerForDataToSend = -1;
        try {
            dbLock.readLock().lock();
            try {
                db.beginTransaction();
                try {
                    Cursor cursor;
                    cursor = db.query("setting", new String[]{"value"}, "name='DataUploadPointer'", null, null, null, null, null);
                    if (cursor.getCount() != 0) {
                        cursor.moveToFirst();                           //JS: if the DataUploadPointer field exists in DB, set the PointerForDataToSend to its value
                        PointerForDataToSend = cursor.getInt(0);
                        Log.d(debugTag, "DataUploadPointer field exists in DB, set the PointerForDataToSend to its value: " + PointerForDataToSend);
                    }

                    db.setTransactionSuccessful();
                } catch (Exception e) {
                    Log.e(debugTag, "Exception fetching data upload pointer:");
                    e.printStackTrace();
                }
            } finally {
                db.endTransaction();
            }
        } finally {
            dbLock.readLock().unlock();
        }

        return PointerForDataToSend;
    }

    public int setDataUploadPointer(ReentrantReadWriteLock mLockUsage, SQLiteDatabase dbUsage, ReentrantReadWriteLock dbLock, SQLiteDatabase db) throws RemoteException {

        int smallestId = 1;
        int PointerForDataToSend = -1;
            try {
                mLockUsage.readLock().lock();
                try {
                    dbUsage.beginTransaction();
                    try {

                        //JS: Query DB for minimum _id and set the pointer equal to it
                        Cursor cursor = dbUsage.query("usage_full", new String[]{"MIN(_id)"}, null, null, null, null, null);   //JS: Change it to _id when the column is readded to the database.
                        cursor.moveToFirst();

                        smallestId = cursor.getInt(0);
                        Log.d(debugTag, "Smallest _id obtained from DB: " + smallestId);

                        if (smallestId < 0) {
                            smallestId = 1;
                            Log.e(debugTag, "Negative number obtained while estimating smallest _id");
                        }

                        dbUsage.setTransactionSuccessful();
                    } catch (Exception e) {

                        Log.e(debugTag, "Error fetching smallest _id: ");
                        e.printStackTrace();
                    }

                } finally {
                    dbUsage.endTransaction();
                }

            } finally {
                mLockUsage.readLock().unlock();
            }


         try {
             dbLock.writeLock().lock();
             try {
                 db.beginTransaction();
                 try {
                     ContentValues DataUploadPointer_row = new ContentValues();
                     DataUploadPointer_row.put("uid", "" + 0);
                     DataUploadPointer_row.put("name", "DataUploadPointer");
                     DataUploadPointer_row.put("value", smallestId);

                     long firstTimePointerUpdate = db.insert("setting", null, DataUploadPointer_row);

                     if (firstTimePointerUpdate == -1) {
                         Log.e(debugTag, "Failure inserting the upload pointer for the first time");
                     }
                     PointerForDataToSend = smallestId;

                     db.setTransactionSuccessful();
                 } catch (Exception e) {
                     Log.e(debugTag, "Error setting data upload pointer value: ");
                     e.printStackTrace();
                 }
             } finally {
                 db.endTransaction();
             }
         } finally {
             dbLock.writeLock().unlock();
         }

        return PointerForDataToSend;
    }

    public JsonIntDataClass FetchDataFromDBandFillItIntoJsonArray (ReentrantReadWriteLock mLockUsage, SQLiteDatabase dbUsage, int PointerForDataToSend) throws RemoteException {
        JSONArray TotalDataToSend = new JSONArray();
        JsonIntDataClass SentObject = new JsonIntDataClass();
        //int PointerForDataToSend = 0;

        try {
            mLockUsage.readLock().lock();
            try {
                dbUsage.beginTransaction();
                try {

                    Cursor cursor = dbUsage.query("usage_full", new String[]{"uid", "gids", "package_name", "app_name", "version", "app_category", "method_category", "method", "parameters",
                                    "is_dangerous", "decision", "cached_duration", "decision_type", "decision_elapsed", "decision_time", "decision_modified", "foreground_package_name", "foreground_app_name",
                                    "foreground_activity", "screen_interactive", "screen_lock", "ringer_state", "headphones_plugged", "headphones_type", "headphones_mike", "battery_percent",
                                    "charging_state", "charge_plug", "conn_type", "dock", "lat", "long", "type_of_place", "provider"}, "_id>=? AND _id<?", new String[]{""+PointerForDataToSend, ""+(PointerForDataToSend+maxRecords)}, null,
                            null, "decision_time DESC");

                    int NumbofRowsToBeSent = cursor.getCount();
                    Log.d(debugTag, "Number of rows to be sent: " + NumbofRowsToBeSent);

                    if (cursor == null) {
                        Util.log(null, Log.WARN, "Database cursor null (usage data)"); //TODO: should we use this log class?
                        Log.e(debugTag, "Error fetching data from DB (cursor == null)");
                    } else
                        try {
                            while (cursor.moveToNext()) {
                                JSONObject jObject = new JSONObject();
                                jObject.put("uid", cursor.getInt(0));
                                jObject.put("gids", cursor.getString(1));
                                jObject.put("package_name", cursor.getString(2));
                                jObject.put("app_name", cursor.getString(3));
                                jObject.put("version", cursor.getString(4));
                                jObject.put("app_category", cursor.getString(5));
                                jObject.put("method_category", cursor.getString(6));
                                jObject.put("method", cursor.getString(7));
                                jObject.put("parameters", cursor.getString(8));
                                jObject.put("is_dangerous", cursor.getInt(9));
                                jObject.put("Decision", +cursor.getInt(10));
                                jObject.put("cached_duration", cursor.getInt(11));
                                jObject.put("decision_type", cursor.getInt(12));
                                jObject.put("decision_elapsed", cursor.getLong(13));
                                jObject.put("decision_time", cursor.getLong(14));
                                jObject.put("decision_modified", cursor.getInt(15));
                                jObject.put("foreground_package_name", cursor.getString(16));
                                jObject.put("foreground_app_name", cursor.getString(17));
                                jObject.put("foreground_activity", cursor.getString(18));
                                jObject.put("screen_interactive", +cursor.getInt(19));
                                jObject.put("screen_lock", +cursor.getInt(20));
                                jObject.put("ringer_state", +cursor.getInt(21));
                                jObject.put("headphones_plugged", +cursor.getInt(22));
                                jObject.put("headphones_type", cursor.getString(23));
                                jObject.put("headphones_mike", cursor.getInt(24));
                                jObject.put("battery_percent", cursor.getInt(25));
                                jObject.put("charging_state", cursor.getInt(26));
                                jObject.put("charge_plug", cursor.getInt(27));
                                jObject.put("conn_type", cursor.getInt(28));
                                jObject.put("dock", cursor.getInt(29));
                                jObject.put("lat", cursor.getFloat(30));
                                jObject.put("long", cursor.getFloat(31));
                                jObject.put("type_of_place", cursor.getInt(32));
                                jObject.put("provider", cursor.getString(33));
                                TotalDataToSend.put(jObject);
                            }

                        } catch (Exception e) {
                            Log.e(debugTag, "Error inserting data into JSON object: ");
                            e.printStackTrace();
                        } finally {
                            cursor.close();
                        }
                } catch (Exception e) {
                    Log.e(debugTag, "Error in fetching db data and filling into JSON object: ");
                    e.printStackTrace();
                }

                    dbUsage.setTransactionSuccessful();
                } finally {
                    dbUsage.endTransaction();
                }

            } catch (Throwable ex) {
                Util.bug(null, ex);
                throw new RemoteException(ex.toString());

            } finally {
                mLockUsage.readLock().unlock();
            }

            SentObject.SentData = TotalDataToSend;
            SentObject.PointerForData = PointerForDataToSend;
            return SentObject;

    }

    public int[] ConnectToServerandSendData(int PointerForDataToSend, String url, JSONArray TotalDataToSend)throws RemoteException {
        int NumberOfRowsReceivedByServer = -5;            //JS: initialize it to any negative value other than -1
        StringBuilder sb = new StringBuilder();
        HttpURLConnection urlConnection = null;
        try {
            URL url2 = new URL(url);
            urlConnection = (HttpURLConnection) url2.openConnection();
            urlConnection.setDoOutput(true);
            urlConnection.setRequestMethod("POST");
            urlConnection.setUseCaches(false);
            urlConnection.setConnectTimeout(10000);
            urlConnection.setReadTimeout(10000);
            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setRequestProperty("Accept-Encoding", "gzip");
            urlConnection.setRequestProperty("Content-Encoding", "gzip");
            urlConnection.setRequestProperty("SmarPer-DeviceID", PrivacyService.Smarper.getHash());
            urlConnection.setRequestProperty("SmarPer-Secret", PrivacyService.Smarper.getSecret());
            urlConnection.connect();

            OutputStream outStream = new GZIPOutputStream(urlConnection.getOutputStream());
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(outStream));
            out.write(TotalDataToSend.toString());
            out.close();

            int HttpResult = urlConnection.getResponseCode();
            if(HttpResult == HttpURLConnection.HTTP_OK){
                Log.d(debugTag, "Connected to server: " + HttpURLConnection.HTTP_OK);
                InputStream inStream = new GZIPInputStream(urlConnection.getInputStream());
                BufferedReader br = new BufferedReader(new InputStreamReader(inStream,"utf-8"));

                //KO: Read server response into a string
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line + "\n");
                }
                br.close();

                //KO: Initialize
                String errorMessage = "";
                boolean success = false;

                try {

                    //KO: Parse to JSON
                    JSONObject response = new JSONObject(sb.toString());
                    success = response.getBoolean("success");
                    if (success) {
                        NumberOfRowsReceivedByServer = response.getInt("rows");
                    }
                    else {
                        errorMessage = response.getString("errorMsg");
                    }

                } catch (JSONException e){
                    Log.e(debugTag, "Error parsing JSON: " + e.getMessage());
                    Log.e(debugTag, sb.toString() + " failed to parse to JSON");
                    e.printStackTrace();

                }

                //KO: Check new success field instead of number of rows returned
                if (!success){ //KO: Something went wrong
                    Log.e(debugTag, "An error occurred: " + errorMessage);
                }
                else if (NumberOfRowsReceivedByServer > 0){                                                                          //JS: Data was sent and acknowledged, We can then update pointer
                    PointerForDataToSend = PointerForDataToSend + NumberOfRowsReceivedByServer;
                    Log.d(debugTag, "Server Response Succeeded: " + NumberOfRowsReceivedByServer);
                }
            }
            else{
                NumberOfRowsReceivedByServer = -2;             //JS: Server Response Code was not 200, it is considered an error
                Log.e(debugTag, urlConnection.getResponseMessage() + " " + urlConnection.getResponseCode());
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            Log.e(debugTag, "Connection Exception Error: Did not send any data");
            NumberOfRowsReceivedByServer = -5;                                      //JS: Connection Exception
            e.printStackTrace();
        } finally{
            if(urlConnection!=null)
                urlConnection.disconnect();
        }
        int[] returnIntegers = {PointerForDataToSend, NumberOfRowsReceivedByServer};
        return returnIntegers;
    }

    public void UpdateValueOfPointerInDatabase (int PointerForDataToSend, ReentrantReadWriteLock mLock, SQLiteDatabase db) throws RemoteException {
        try {
            mLock.writeLock().lock();
            try {
                db.beginTransaction();
                try {
                    ContentValues DataUploadPointer_row = new ContentValues();
                    DataUploadPointer_row.put("value", PointerForDataToSend);
                    DataUploadPointer_row.put("uid", "" + 0);
                    String []L = {"DataUploadPointer"};
                    int NumberOfRowsAffected = db.update("setting", DataUploadPointer_row, "name=?", L);
                    if(NumberOfRowsAffected == 0) {
                        Log.e(debugTag, "Updating upload pointer failed" + NumberOfRowsAffected);
                    }
                    Log.d(debugTag, "Updated upload pointer value");
                    Log.d(debugTag, "New value of pointer = " + PointerForDataToSend);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            } finally {
                mLock.writeLock().unlock();
            }
        } catch (Throwable ex) {
            Util.bug(null, ex);
            throw new RemoteException(ex.toString());
        }
    }

}