package com.example.zenbo;

import android.app.Activity;
import android.content.DialogInterface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.asus.robotframework.API.RobotAPI;
import com.asus.robotframework.API.RobotCallback;
import com.asus.robotframework.API.Utility;
import com.asus.robotframework.API.RobotFace;
import com.asus.robotframework.API.RobotCmdState;
import com.asus.robotframework.API.RobotErrorCode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class MainActivity extends RobotActivity {
    public static final String API_KEY = "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";
    private static final int SERVER_PORT = 7366;
    private Handler handler = new Handler();
    private ServerThread serverThread;
    private List<Socket> clientList = new ArrayList<>();
    private ServerSocket server = null;
    private boolean flag = true;
    private TextView txt;
    private SensorManager mSensorManager;
    private Sensor mSensorCapacityTouch;
    private TextView mTextView_capacity_touch_value0;
    private TextView mTextView_capacity_touch_value1;
    private ChatGptAPI chatGptAPI;

    public static RobotCallback robotCallback = new RobotCallback() {
        @Override
        public void onResult(int cmd, int serial, RobotErrorCode err_code, Bundle result) {
            super.onResult(cmd, serial, err_code, result);
        }

        @Override
        public void onStateChange(int cmd, int serial, RobotErrorCode err_code, RobotCmdState state) {
            super.onStateChange(cmd, serial, err_code, state);
        }

        @Override
        public void initComplete() {
            super.initComplete();
        }
    };

    public static RobotCallback.Listen robotListenCallback = new RobotCallback.Listen() {
        @Override
        public void onFinishRegister() {}

        @Override
        public void onVoiceDetect(JSONObject jsonObject) {}

        @Override
        public void onSpeakComplete(String s, String s1) {}

        @Override
        public void onEventUserUtterance(JSONObject jsonObject) {}

        @Override
        public void onResult(JSONObject jsonObject) {}

        @Override
        public void onRetry(JSONObject jsonObject) {}
    };

    public MainActivity() {
        super(robotCallback, robotListenCallback);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        txt = findViewById(R.id.txt);
        txt.setText(getLocalIpAddress() + " : " + SERVER_PORT);

        this.robotAPI = new RobotAPI(getApplicationContext(), robotCallback);
        mTextView_capacity_touch_value0 = findViewById(R.id.id_sensor_type_capacity_touch_value0_value);
        mTextView_capacity_touch_value1 = findViewById(R.id.id_sensor_type_capacity_touch_value1_value);

        // 初始化 SensorManager 和傳感器
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorCapacityTouch = mSensorManager.getDefaultSensor(Utility.SensorType.CAPACITY_TOUCH);

        showIpAddress();
        serverThread = new ServerThread();
        serverThread.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(listenerCapacityTouch, mSensorCapacityTouch, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        robotAPI.release();
    }

    class ServerThread extends Thread {
        @Override
        public void run() {
            try {
                server = new ServerSocket(SERVER_PORT);
            } catch (IOException ex) {
                Log.v("ServerThread", ex.toString());
                return;
            }

            while (flag) {
                try {
                    Socket client = server.accept();
                    if (client != null) {
                        handler.post(() -> txt.append(client.getLocalAddress().toString() + "\n"));
                        SocketThread socketThread = new SocketThread(client);
                        socketThread.start();
                        clientList.add(client);
                    }
                } catch (IOException ex) {
                    Log.v("ServerThread", ex.toString());
                    break;
                }
            }
        }
    }

    class SocketThread extends Thread {
        private Socket socket;
        private BufferedReader in;
        private boolean isConnected = true;

        public SocketThread(Socket socket) {
            this.socket = socket;
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            } catch (IOException ex) {
                Log.v("Client SocketThread Construct", ex.toString());
            }
        }

        @Override
        public void run() {
            while (isConnected) {
                try {
                    String str = in.readLine();
                    JSONObject jsonObject = new JSONObject(str);
                    handler.post(() -> txt.append(jsonObject.toString() + "\n"));

                    String expression = jsonObject.optString("expression");
                    if (expression.equals("Happy")) {
                        robotAPI.robot.setExpression(RobotFace.HAPPY);
                    } else if (expression.equals("Sad")) {
                        robotAPI.robot.setExpression(RobotFace.HELPLESS);
                    } else if (expression.equals("Angry")) {
                        robotAPI.robot.setExpression(RobotFace.SERIOUS);
                    }

                    String msg = jsonObject.optString("msg");
                    if (msg.equals("look")) {
                        robotAPI.utility.lookAtUser(0);
                    } else if (!msg.isEmpty()) {
                        chatGptAPI = new ChatGptAPI(msg);
                        chatGptAPI.start();
                    }
                } catch (Exception ex) {
                    Log.v("SocketThread InputStream Read", ex.toString());
                    break;
                }
            }
        }
    }

    public void showIpAddress() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("IP :");
        builder.setMessage(getLocalIpAddress() + " : " + SERVER_PORT);
        builder.setNegativeButton("OK", (dialogInterface, i) -> dialogInterface.dismiss());
        builder.create().show();
    }

    public String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); en.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception ex) {
            Log.e("IP Address", ex.toString());
        }
        return null;
    }

    SensorEventListener listenerCapacityTouch = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            mTextView_capacity_touch_value0.setText(String.valueOf(event.values[0]));
            mTextView_capacity_touch_value1.setText(String.valueOf(event.values[1]));
            if (event.values[0] >= 2) {
                robotAPI.robot.setExpression(RobotFace.WORRIED, "what's wrong");
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    class ChatGptAPI extends Thread {
        private String message;

        public ChatGptAPI(String message) {
            this.message = message;
        }

        @Override
        public void run() {
            try {
                URL url = new URL("https://api.openai.com/v1/chat/completions");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + API_KEY);
                connection.setDoOutput(true);
                connection.setDoInput(true);

                String requestBody = GetJsonRequestBody("You are a chatbot.", message);
                DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
                outputStream.writeBytes(requestBody);
                outputStream.flush();
                outputStream.close();

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject jsonObject = new JSONObject(response.toString());
                String content = jsonObject.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");

                handler.post(() -> {
                    txt.setText("From client: " + message + "\nFrom ChatGPT: " + content + "\n");
                    robotAPI.robot.speak(content);
                });

            } catch (Exception e) {
                Log.v("API Response Fail", e.toString());
            }
        }
    }

    private String GetJsonRequestBody(String systemContent, String userContent) {
        try {
            JSONObject requestObject = new JSONObject();
            JSONArray messagesArray = new JSONArray();
            JSONObject systemObject = new JSONObject();
            systemObject.put("role", "system");
            systemObject.put("content", systemContent);

            JSONObject userObject = new JSONObject();
            userObject.put("role", "user");
            userObject.put("content", userContent);

            messagesArray.put(systemObject);
            messagesArray.put(userObject);

            requestObject.put("model", "gpt-3.5-turbo");
            requestObject.put("messages", messagesArray);
            return requestObject.toString();
        } catch (Exception e) {
            Log.v("json", e.toString());
            return "failed";
        }
    }
}
