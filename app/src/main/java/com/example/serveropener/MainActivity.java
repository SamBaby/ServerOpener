package com.example.serveropener;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.android.serveropener.R;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import datamodel.BasicFee;
import datamodel.BasicSetting;
import event.Var;
import util.ApacheServerRequest;
import util.HTTPGetRequest;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        try {
            executeCommand("mysqld", new String[]{});
            executeCommand("rm", new String[]{"/data/data/com.termux/files/usr/var/run/apache2/httpd.pid"});
            executeCommand("apachectl", new String[]{"start"});
            executeCommand("python", new String[]{"/storage/sdcard/camera_server.py"});
            uploadLots();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void executeCommand(String command, String[] ags) {
        String LOG_TAG = "command";
        Intent intent = new Intent();
        intent.setClassName(TermuxConstants.TERMUX_PACKAGE_NAME, TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE_NAME);
        intent.setAction(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND);
        intent.putExtra(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_COMMAND_PATH, "/data/data/com.termux/files/usr/bin/" + command);
        intent.putExtra(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_ARGUMENTS, ags);
        intent.putExtra(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_WORKDIR, "/data/data/com.termux/files/home");
        intent.putExtra(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_BACKGROUND, true);
        intent.putExtra(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_SESSION_ACTION, "0");
        intent.putExtra(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_COMMAND_LABEL, "top command");
        intent.putExtra(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_COMMAND_DESCRIPTION, "Runs the top command to show processes using the most resources.");

// Create the intent for the IntentService class that should be sent the result by TermuxService
        Intent pluginResultsServiceIntent = new Intent(MainActivity.this, PluginResultsService.class);

// Generate a unique execution id for this execution command
        int executionId = PluginResultsService.getNextExecutionId();

// Optional put an extra that uniquely identifies the command internally for your app.
// This can be an Intent extra as well with more extras instead of just an int.
        pluginResultsServiceIntent.putExtra(PluginResultsService.EXTRA_EXECUTION_ID, executionId);

// Create the PendingIntent that will be used by TermuxService to send result of
// commands back to the IntentService
// Note that the requestCode (currently executionId) must be unique for each pending
// intent, even if extras are different, otherwise only the result of only the first
// execution will be returned since pending intent will be cancelled by android
// after the first result has been sent back via the pending intent and termux
// will not be able to send more.
        PendingIntent pendingIntent = PendingIntent.getService(MainActivity.this, executionId,
                pluginResultsServiceIntent,
                PendingIntent.FLAG_ONE_SHOT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0));
        intent.putExtra(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_PENDING_INTENT, pendingIntent);

        try {
            // Send command intent for execution
            Log.d(LOG_TAG, "Sending execution command with id " + executionId);
            startService(intent);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to start execution command with id " + executionId + ": " + e.getMessage());
        }
    }

    private void uploadLots() {
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(30000);
                    BasicSetting setting = getInformationFromServer();
                    if (setting != null) {
                        Var<Integer> totalVar = new Var<>(0);
                        Var<Integer> leftVar = new Var<>(0);
                        Thread tLot = new Thread(() -> {
                            int total = 0;
                            try {
                                String slot = ApacheServerRequest.getLeftLot();
                                if (slot != null && !slot.isEmpty()) {
                                    JSONObject obj = new JSONArray(slot).getJSONObject(0);
                                    total += obj.getInt("car_slot");
                                    total += obj.getInt("pregnant_slot");
                                    total += obj.getInt("disabled_slot");
                                    total += obj.getInt("charging_slot");
                                    total -= obj.getInt("reserved_slot");
                                    total += obj.getInt("car_left");
                                    total += obj.getInt("pregnant_left");
                                    total += obj.getInt("charging_left");
                                    total += obj.getInt("disabled_left");
                                }
                                totalVar.set(total);
                                String cars = ApacheServerRequest.getCarInsideCount();
                                if (cars != null && !cars.isEmpty()) {
                                    JSONObject obj = new JSONArray(cars).getJSONObject(0);
                                    total -= obj.getInt("COUNT(*)");
                                }
                                leftVar.set(total);
                            } catch (Exception e) {
                                Log.d("getLeftLots", "getLeftLots");
                            }
                            try {
                                Thread.sleep(2000);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                        try {
                            tLot.start();
                            tLot.join();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        uploadLots(totalVar.get(), leftVar.get(), setting);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t.start();
    }

    private BasicSetting getInformationFromServer() {
        Var<BasicSetting> setting = new Var<>();
        Thread t = new Thread(() -> {
            String json = ApacheServerRequest.getCompanyInformation();
            try {
                if (json != null) {
                    JSONArray jsonArray = new JSONArray(json);
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    setting.set(gson.fromJson(jsonArray.getJSONObject(0).toString(), BasicSetting.class));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        try {
            t.start();
            t.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return setting.get();
    }

    private void uploadLots(int total, int left, @NonNull BasicSetting setting) {
        int upload = setting.getAuto_upload_server();
        if (upload == 1) {
            try {
                URL urlPass = new URL("https://ptps.tbkc.gov.tw/ParkingLot/Information");

                // Create connection
                HttpURLConnection conn = (HttpURLConnection) urlPass.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String id = setting.getParking_id();
                String area = setting.getParking_area();
                String address = setting.getParking_address();
                String apikey = setting.getParking_apikey();
                String content = "{\n" +
                        "\"id\": \"" + id + "\",\n" +
                        "\"name\": \"" + setting.getLot_name() + "\",\n" +
                        "\"smallcarParkingSpace\": \"" + total + "\",\n" +
                        "\"smallcarVacancy\": \"" + left + "\",\n" +
                        "\"largecarParkingSpace\": \"-1\",\n" +
                        "\"largecarVacancy\": \"-1\",\n" +
                        "\"motorcycleParkingSpace\": \"-1\",\n" +
                        "\"motorcycleVacancy\": \"-1\",\n" +
                        "\"areaname\": \"" + area + "\"\n" +
                        "\"location\": \"" + address + "\"\n" +
                        "\"telephone\": \"" + setting.getCompany_phone() + "\"\n" +
                        "\"businesshours\": \"24小時\"\n" +
                        "\"specialCharging\": \"" + getSpecialDescription() + "\"\n" +
                        "\"smallcarcharg\": \"30\"\n" +
                        "\"largecarcharg\": \"-1\"\n" +
                        "\"motorcyclecharg\": \"-1\"\n" +
                        "\"smallcarchargefrequency\": \"-1\"\n" +
                        "\"largecarchargefrequency\": \"-1\"\n" +
                        "\"motorcyclechargefrequency\": \"-1\"\n" +
                        "\n" +
                        "\"Apikey\": \"" + apikey + "\"\n" +
                        "}";
                // Write parameters to output stream
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] postDataBytes = content.getBytes("UTF-8");
                    os.write(postDataBytes);
                }

                int responseCode = conn.getResponseCode();
                // Read response
                // TODO: Handle response here
                // 读取响应内容
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                // Close connection
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String getSpecialDescription() {
        return "";
    }
}