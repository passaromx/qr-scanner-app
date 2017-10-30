package com.passaro.lectorqr;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PointF;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.dlazaro66.qrcodereaderview.QRCodeReaderView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class MainActivity extends AppCompatActivity {

    private SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mEditor;
    private ProgressDialog mProgressDialog;

    public static final String SCAN_INFO = "Scan Info";
    public static final String TAG = MainActivity.class.getSimpleName();
    private FloatingActionButton mScan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mScan = (FloatingActionButton) findViewById(R.id.fab);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, 123);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_PHONE_STATE}, 1234);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 12);
        }

        mSharedPreferences = getSharedPreferences(SCAN_INFO, MODE_PRIVATE);
        mEditor = mSharedPreferences.edit();
        //clearSharedPreferences();
        String imei = mSharedPreferences.getString("imei", "");
        if (imei.equals("")){
            mEditor.putString("imei", getDeviceId());
            mEditor.commit();
        }

        mScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Set<String> scanned = mSharedPreferences.getStringSet("scanned", null);
                Log.d(TAG, "Scanned: " + scanned);

                if (scanned == null){
                    Intent intent = new Intent(MainActivity.this, ScanActivity.class);
                    startActivity(intent);
                } else {
                    pendingSessionAlert();
                }
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    public void clearSharedPreferences(){
        mEditor.putStringSet("scanned", null);
        //mEditor.putString("gate", "");
        mEditor.putString("formattedData", "");
        mEditor.putString("lat", "");
        mEditor.putString("lon", "");
        mEditor.commit();
    }

    public void pendingSessionAlert(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setTitle("Detectamos una sesión que cerró incorectamente");
        builder.setMessage("¿Qué acción deseas tomar?");
        builder.setPositiveButton(
                "Continuar sesión",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        Intent intent = new Intent(MainActivity.this, ScanActivity.class);
                        startActivity(intent);
                    }
                });
        builder.setNegativeButton(
                "Sincronizar datos",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        mProgressDialog = ProgressDialog.show(MainActivity.this, "Un momento por favor", "Estamos procesando tu solicitud...", true);

                        String data = mSharedPreferences.getString("formattedData", "");
                        if (data.equals("")){
                            data = createUploadJson();
                        }

                        uploadScan(data);
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    public String getDeviceId(){
        TelephonyManager telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        String imei = telephonyManager.getDeviceId();
        //Log.d(TAG, imei);
        return imei;
    }

    public String createUploadJson() {
        JSONObject data = new JSONObject();

        try {
            //data.put("puerta", mSharedPreferences.getString("gate", ""));
            data.put("scannerId", mSharedPreferences.getString("imei", ""));
            data.put("date", getDate());
            data.put("lat", mSharedPreferences.getString("lat", ""));
            data.put("lon", mSharedPreferences.getString("lon", ""));
            data.put("scanned", new JSONArray((new ArrayList<String>(mSharedPreferences.getStringSet("scanned", null))).toString()));
            Log.d(TAG, data.toString());

        } catch (Exception e) {
            e.printStackTrace();

        }

        return data.toString();
    }

    public void uploadScan(String data){

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();

        //Log.d(TAG, "Lat: " + mLat + " Lon: " + mLon);

        FormBody body = new FormBody.Builder()
                .add("data", data)
                .build();

        Request request = new Request.Builder()
                .post(body)
                .url("http://drongeic.mx:8080/movilidad/qr2.php")
                .build();

        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                //fail logic
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mProgressDialog.isShowing()){
                            mProgressDialog.dismiss();
                        }
                        // sigue intentando
                        Toast.makeText(MainActivity.this, "Algo falló, intenta más tarde", Toast.LENGTH_SHORT).show();
                    }
                });

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String data = response.body().string();
                //Log.d(TAG, "RESPONSE: " + response);
                //Log.d(TAG, "RESPONSE BODY: " + response.body());

                if (mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }
                if (response.isSuccessful()){

                    Log.d(TAG, "Call Response: " + data);
                    try {
                        JSONObject res = new JSONObject(data);
                        if (res.get("status").toString().equals("OK")){
                            //succes logic
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    //too chido
                                    clearSharedPreferences();
                                    Toast.makeText(MainActivity.this, "Solicitud exitosa", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    //sigue intentando
                                    Toast.makeText(MainActivity.this, "Algo falló, intenta más tarde", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    } catch (Exception e){
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //sigue intentando
                                Toast.makeText(MainActivity.this, "Algo falló, intenta más tarde", Toast.LENGTH_SHORT).show();
                            }
                        });
                        e.printStackTrace();
                    }

                }
            }
        });
    }

    public String getDate(){
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = new Date(System.currentTimeMillis());
        String formattedDate = dateFormat.format(date);
        Log.d(TAG, formattedDate);
        return formattedDate;
    }

}
