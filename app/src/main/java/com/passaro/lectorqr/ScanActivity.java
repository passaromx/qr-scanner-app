package com.passaro.lectorqr;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PointF;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.dlazaro66.qrcodereaderview.QRCodeReaderView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by Rayon on 28/08/17.
 */

public class ScanActivity extends AppCompatActivity implements QRCodeReaderView.OnQRCodeReadListener {

    private TextView mImei, mStatus, mGate;
    private QRCodeReaderView qrCodeReaderView;
    private ArrayList<String> mScannedImei;
    private JSONArray mScanned;
    private String mScan = "";
    private Double mLat = 0.0, mLon = 0.0;
    private Button mFinish;
    private LocationListener mLocationListener;
    private LocationManager mLocationManager;
    private Vibrator mVibrate;
    private ProgressBar mProgressBar;
    private ProgressDialog mProgressDialog;
    private SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mEditor;

    public static final String SCAN_INFO = "Scan Info";
    public static final String TAG = ScanActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        mImei = (TextView) findViewById(R.id.imeiLabel);
        mStatus = (TextView) findViewById(R.id.statusLabel);
        mGate = (TextView) findViewById(R.id.gateLabel);
        mFinish = (Button) findViewById(R.id.endSessionBtn);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);

        mSharedPreferences = getSharedPreferences(SCAN_INFO, MODE_PRIVATE);
        mEditor = mSharedPreferences.edit();
        //mEditor.putString("scanned", "{}");
        //mEditor.commit();

        Set<String> scannedInfo = mSharedPreferences.getStringSet("scanned", null);
        if (scannedInfo != null ) {
            mScannedImei = new ArrayList<String>(scannedInfo);
        } else {
            mScannedImei = new ArrayList<>();
        }


        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mVibrate = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

        mLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                mLat = location.getLatitude();
                mLon = location.getLongitude();
                if (mLat != 0.0 && mLon != 0.0){
                    try {
                        Log.d(TAG, "LOCATION MANAGER STOPPED");
                        mLocationManager.removeUpdates(mLocationListener);
                        mEditor.putString("lat", String.valueOf(mLat));
                        mEditor.putString("lon", String.valueOf(mLon));
                    } catch (SecurityException e){
                        e.printStackTrace();
                    }

                }
            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {

            }

            @Override
            public void onProviderEnabled(String s) {

            }

            @Override
            public void onProviderDisabled(String s) {

            }
        };
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 10, mLocationListener);
        } catch (SecurityException e){
            e.printStackTrace();
        }

        qrCodeReaderView = (QRCodeReaderView) findViewById(R.id.qrdecoderview);
        qrCodeReaderView.setOnQRCodeReadListener(this);
        qrCodeReaderView.setQRDecodingEnabled(true);
        qrCodeReaderView.setAutofocusInterval(2000L);
        //qrCodeReaderView.forceAutoFocus();
        qrCodeReaderView.setBackCamera();

        mFinish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //fallback data save
                try {
                    JSONObject data = new JSONObject();
                    data.put("puerta", mGate.getText());
                    data.put("scannerId", mSharedPreferences.getString("imei", ""));
                    data.put("date", getDate());
                    data.put("lat", String.valueOf(mLat));
                    data.put("lon", String.valueOf(mLon));
                    data.put("scanned", new JSONArray(mScannedImei.toString()));
                    mEditor.putString("formattedData", data.toString());
                    mEditor.commit();
                    Log.d(TAG, data.toString());

                    if (isNetworkAvailable()) {
                        uploadScan(data.toString());
                    } else {
                        noNetworkAlert();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //finish();
            }
        });

        String gate = mSharedPreferences.getString("gate", "");
        if (gate.equals("")) {
            mProgressDialog = ProgressDialog.show(ScanActivity.this, "Cargando...", "Un momento por favor", true);
            getDataAndShowAlert();
        } else {
            mGate.setText(gate);
        }
        //algo

    }

    @Override
    protected void onResume() {
        super.onResume();
        qrCodeReaderView.startCamera();
        Log.d(TAG, mScannedImei.toString());
    }

    @Override
    protected void onPause() {
        super.onPause();
        qrCodeReaderView.stopCamera();
    }

    @Override
    public void onQRCodeRead(String text, PointF[] points) {
        if (!mScan.equals(text) && !text.equals("No QR Code found")) {
            mScan = text;
            if (text.length() >= 14 && text.length() <= 15) {
                mImei.setText(text);
                if (!mScannedImei.contains(text)){
                    vibrate("success");
                    mStatus.setText(R.string.scan_success);
                    mStatus.setTextColor(Color.parseColor("#2E7D32"));
                    //Log.d(TAG, "NEW IMEI: " + text);
                    mScannedImei.add(mScan);
                    Log.d(TAG, "SCANNED: " + mScannedImei.toString());
                    //save to shared new array
                    /*String[] scans = new String[mScannedImei.size()];
                    scans = mScannedImei.toArray(scans);
                    String formatScans = "";
                    for(int i = 0; i < scans.length; i++){
                        if (i == scans.length-1){
                            formatScans = formatScans + scans[i];
                        } else {
                            formatScans = formatScans + scans[i] + ", ";
                        }
                    }
                    Log.d(TAG, "scans: " + formatScans); */
                    Set<String> set = new HashSet<String>();
                    set.addAll(mScannedImei);
                    mEditor.putStringSet("scanned", set);
                    mEditor.commit();
                } else {
                    mStatus.setText(R.string.scan_duplicate);
                    vibrate("duplicate");
                    mStatus.setVisibility(View.VISIBLE);
                    mProgressBar.setVisibility(View.GONE);
                    mStatus.setTextColor(Color.parseColor("#EF6C00"));
                }
            } else {
                invalidScanAlert();
            }

        }
    }

    public void getDataAndShowAlert(){
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("http://drongeic.mx:8080/operacion/assets/api/puertas.php")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (mProgressDialog.isShowing()){
                    mProgressDialog.dismiss();
                }

                ArrayList<String> gatesArray = new ArrayList<String>();
                gatesArray.addAll(mSharedPreferences.getStringSet("gatesArray", null));
                promptForGate(gatesArray);
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                if (mProgressDialog.isShowing()){
                    mProgressDialog.dismiss();
                }
                final ArrayList<String> gatesArray = new ArrayList<String>();

                if (response.isSuccessful()) {
                    try {
                        JSONArray gates = new JSONArray(response.body().string());
                        for(int i = 0; i < gates.length(); i++){
                            gatesArray.add(gates.get(i).toString());
                        }
                        Set<String> set = new HashSet<String>();
                        set.addAll(gatesArray);
                        mEditor.putStringSet("gatesArray", set);
                        mEditor.commit();
                    } catch (Exception e) {
                        e.printStackTrace();
                        gatesArray.addAll(mSharedPreferences.getStringSet("gatesArray", null));
                    }
                    //Log.d(TAG,response.body().string());

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            promptForGate(gatesArray);
                        }
                    });

                }
            }
        });
    }

    public void promptForGate(ArrayList<String> gates){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Selecciona lugar de escaneo");

        //String[] spinner = {"Selecciona una opción","Puerta 7", "Almacen", "Puerta 7"};

        final ArrayAdapter<String> adp = new ArrayAdapter<>(ScanActivity.this,
                android.R.layout.simple_spinner_dropdown_item, gates);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(80, 60, 80, 0);
        final Spinner sp = new Spinner(ScanActivity.this);
        sp.setLayoutParams(lp);
        sp.setAdapter(adp);

        container.addView(sp);

        builder.setView(container);
        builder.setPositiveButton("Continuar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                String gate = sp.getSelectedItem().toString();
                mGate.setText(gate);
                mEditor.putString("gate", gate);
                mEditor.commit();
            }
        });

        builder.setCancelable(false);
        final AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i != 0){
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

    }

    public void uploadScan(final String data){
        mStatus.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.VISIBLE);

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
                .url("http://drongeic.mx:8080/movilidad/qr.php")
                .build();

        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                //fail logic
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mImei.setText("");
                        mStatus.setText(R.string.scan_fail);
                        vibrate("fail");
                        mStatus.setTextColor(Color.parseColor("#C62828"));
                        mStatus.setVisibility(View.VISIBLE);
                        mProgressBar.setVisibility(View.GONE);
                        //Failed upload Alert
                        uploadAlert("fail");
                    }
                });

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String data = response.body().string();

                if (response.isSuccessful()){
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mStatus.setVisibility(View.VISIBLE);
                            mProgressBar.setVisibility(View.GONE);
                        }
                    });

                    Log.d(TAG, "Call Response: " + data);
                    try {
                        JSONObject res = new JSONObject(data);
                        if (res.get("status").toString().equals("OK")){
                            //succes logic
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    vibrate("success");
                                    mStatus.setText("Operación exitosa");
                                    mStatus.setTextColor(Color.parseColor("#2E7D32"));
                                    //Succes alert
                                    //clear saved values
                                    clearSharedPreferences();
                                    uploadAlert("success");

                                }
                            });
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    vibrate("fail");
                                    mStatus.setText(R.string.scan_fail);
                                    mStatus.setTextColor(Color.parseColor("#C62828"));
                                    //Failed alert
                                    uploadAlert("fail");
                                }
                            });
                        }
                    } catch (Exception e){
                        e.printStackTrace();
                    }

                }
            }
        });
    }

    public void clearSharedPreferences(){
        mEditor.putStringSet("scanned", null);
        mEditor.putString("gate", "");
        mEditor.putString("formattedData", "");
        mEditor.putString("lat", "");
        mEditor.putString("lon", "");
        mEditor.commit();
    }

    public void uploadAlert(String status){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        if (status.equals("success")){
            builder.setTitle("Operación exitosa");
        } else {
            builder.setTitle("Error en solicitud");
            builder.setMessage("Los datos serán guardados e intentaremos subirlos más tarde");
        }
        builder.setPositiveButton(
                "Continuar",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                        finish();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();

    }

    private void noNetworkAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setMessage("No detectamos conexión a internet, intenta más tarde");
        builder.setPositiveButton(
                "Aceptar",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mStatus.setVisibility(View.VISIBLE);
                        mStatus.setText(R.string.no_network);
                        mStatus.setTextColor(Color.parseColor("#C62828"));
                        mProgressBar.setVisibility(View.GONE);
                        dialog.cancel();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    public void vibrate(String evt){
        switch (evt){
            case "success":
                mVibrate.vibrate(150);
                break;
            case "fail":
                mVibrate.vibrate(600);
                break;
            case "duplicate":
                mVibrate.vibrate(150);
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mVibrate.vibrate(150);
                    }
                }, 250);
                break;
        }

    }

    public String getDate(){
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = new Date(System.currentTimeMillis());
        String formattedDate = dateFormat.format(date);
        Log.d(TAG, formattedDate);
        return formattedDate;
    }

    private boolean isNetworkAvailable() {

        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();

        boolean isAvailable = false;
        if (networkInfo != null && networkInfo.isConnected()) {
            //Log.d(TAG, "NETWORK TYPE: " + networkInfo.getType());
            isAvailable = true;
        }
        return isAvailable;
    }

    private void invalidScanAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setMessage("Escaneo inválido, intenta otra vez");
        builder.setPositiveButton(
                "Aceptar",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mImei.setText("IMEI inválido");
                        dialog.cancel();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }
}
