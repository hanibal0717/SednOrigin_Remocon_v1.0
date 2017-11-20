package com.inucreative.sednremocon;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, View.OnTouchListener {
    static final int REMOCON_PORT = 7078;

    static final int NUM_VOLUME_BAR = 26;
    static final String SPKEY_FOR_CONNECTION_TYPE = "ConnectionType";
    static final String SPKEY_FOR_SERVER_ADDRESS = "ServerIPAddress";
    static final String BLUETOOTH_UUID = "3e6eb1e4-ba1b-4de8-802c-830bee9a1403";

    static final int MODE_WIFI = 1;
    static final int MODE_BLUETOOTH = 2;
    int mConnMode;

    Socket mTCPSocket;
    InputStream mTCPInputStream;
    OutputStream mTCPOutputStream;

    BluetoothSocket mBTSocket;
    BluetoothDevice mBTDevice;
    InputStream mBTInputStream;
    OutputStream mBTOutputStream;

    Handler handler = new Handler();

    HashMap<Integer, Integer> keyMap;
    String mServerIPAddr;
    InputMethodManager mIMEmanager;

    // 블루투스 기기 찾아서 아답터에 추가
    BluetoothAdapter btAdapter;
    BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals(BluetoothDevice.ACTION_FOUND)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(device != null) {
                    String device_name = device.getName();
                    LogUtil.d("bluetooth device found - " + ", " + device_name + ", " + device.getAddress());

                    if (device_name != null) {
                        if (device_name.startsWith("SEDN STB")) {
                            btDeviceAdapter.add(device);
                        }
                    }
                }
            } else if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                // check if still connected
                LogUtil.d("ACTION_ACL_DISCONNECTED");
                if(mBTSocket != null) {
                    disconnectEverything();
                    setConnectionText();
                }
            }
        }
    };

    String mStbText;

    VolumeLayout mLayoutVolume;
    ImageView[] volumeBar;

    View showKeyPad;

    ImageButton ibSetup;

    AlertDialog setupDialog;
    TextView tvCurConntection;
    View layoutSetup;
    View layoutWifi;
    View layoutBluetooth;
    RadioButton rbWifi;
    RadioButton rbBluetooth;
    RadioGroup rgConnection;

    ListView lvBTDevice;
    ArrayList<BluetoothDevice> btDeviceList;
    BTDeviceAdapter btDeviceAdapter;
    Handler btScanStopHandler;

    EditText etIPAddress;
    Button btConnect;

    View layoutScanning;
    Animation ani_scanning;
    ImageView ivScanning;

    PacketSender mPacketSender;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermission();
    }

    private void init() {



        mPacketSender = new PacketSender();
        mPacketSender.start();

        // 설정 다이얼로그
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        layoutSetup = inflater.inflate(R.layout.setup_dialog,(ViewGroup) findViewById(R.id.ip_address_input_dialog));
        tvCurConntection = (TextView)layoutSetup.findViewById(R.id.tvCurConntection);

        layoutWifi = layoutSetup.findViewById(R.id.layoutWifi);
        layoutBluetooth = layoutSetup.findViewById(R.id.layoutBluetooth);
        rbWifi = (RadioButton)layoutSetup.findViewById(R.id.rbWifi);
        rgConnection = (RadioGroup)layoutSetup.findViewById(R.id.rgConnection);
        rgConnection.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(checkedId == R.id.rbWifi) {
                    setLayout(MODE_WIFI);
                    stopBTscanning();
                } else {
                    setLayout(MODE_BLUETOOTH);
                    if(setupDialog.isShowing())
                        startBTscanning();
                }
            }
        });
        rbBluetooth = (RadioButton)layoutSetup.findViewById(R.id.rbBluetooth);
        lvBTDevice = (ListView)layoutSetup.findViewById(R.id.lvBTDevice);
        btDeviceList = new ArrayList<>();
        btDeviceAdapter = new BTDeviceAdapter(this, R.layout.item_bluetooth_device, btDeviceList);
        lvBTDevice.setAdapter(btDeviceAdapter);
        lvBTDevice.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // 같은 연결은 중복해서 만들지 않음
                if(mBTSocket != null && mBTSocket.isConnected() && mBTSocket.getRemoteDevice().getAddress().equals(btDeviceList.get(position).getAddress())) {
                    showToast(R.string.already_connected);
                    return;
                }
                stopBTscanning();
                // 기존 연결 해제
                disconnectEverything();
                BTConnectionThread thread = new BTConnectionThread(btDeviceList.get(position), MainActivity.this);
                thread.start();
            }
        });
        layoutScanning = layoutSetup.findViewById(R.id.layoutScanning);
        ani_scanning = AnimationUtils.loadAnimation(this, R.anim.scanning);
        ivScanning = (ImageView)layoutSetup.findViewById(R.id.ivScanning);

        etIPAddress = (EditText)layoutSetup.findViewById(R.id.etIPAddress);
        etIPAddress.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etIPAddress.setKeyListener(DigitsKeyListener.getInstance("0123456789."));
        etIPAddress.setImeOptions(EditorInfo.IME_ACTION_DONE);
        btConnect = (Button)layoutSetup.findViewById(R.id.btConnect);
        btConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String inputStr = etIPAddress.getText().toString();
                if (inputStr != null && !inputStr.isEmpty()) {
                    // 같은 연결을 중복해서 만들지 않음
                    //if(mTCPSocket != null && mTCPSocket.isConnected() && mTCPSocket.getInetAddress().getHostAddress().equals(inputStr)) {
                    //    showToast(R.string.already_connected);
                    //    return;
                    //}
                    // 기존 연결 해제
                    disconnectEverything();
                    TCPConnectionThread tcpConnectionThread = new TCPConnectionThread(inputStr);
                    tcpConnectionThread.start();
                }
            }
        });

        AlertDialog.Builder setupDialogBuilder = new AlertDialog.Builder(this);
        setupDialogBuilder.setTitle(R.string.ip_input_dialog_title);
        setupDialogBuilder.setView(layoutSetup);
        setupDialogBuilder.setPositiveButton("닫기", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                // replaced by IPDialogListener
            }
        });
        setupDialog = setupDialogBuilder.create();
        setupDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                stopBTscanning();
            }
        });

        mTCPSocket = null;
        mTCPInputStream = null;
        mTCPOutputStream = null;

        mBTSocket = null;
        mBTDevice = null;
        mBTInputStream = null;
        mBTOutputStream = null;

        SharedPreferences pref = getSharedPreferences("pref", MODE_PRIVATE);
        mConnMode = pref.getInt(SPKEY_FOR_CONNECTION_TYPE, 1);
        mServerIPAddr = pref.getString(SPKEY_FOR_SERVER_ADDRESS, null);

        initKeyMap();
        initButton();
        initVolume();

        showKeyPad = findViewById(R.id.btKeypad);
        showKeyPad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSednKeyboard();
            }
        });

        mIMEmanager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        ibSetup = (ImageButton) findViewById(R.id.btSetup);
        ibSetup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSetupDialog();
            }
        });

        mStbText = "";
        FeedbackListener feedbackListener = new FeedbackListener(this);
        feedbackListener.start();

        btAdapter = BluetoothAdapter.getDefaultAdapter();

        btScanStopHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                stopBTscanning();
            }
        };

        // 개인방송 테스트용 - TTA 인증을 위해서 주석처리 2017.10.24
//        findViewById(R.id.btCast).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Toast.makeText(getApplicationContext(), "Cast", Toast.LENGTH_SHORT).show();
//
//                // todo : 서버로부터 라이브 스트리밍 접속 URL을 할당받는다
//
//                Intent i = new Intent(MainActivity.this, CameraActivity.class);
//                //i.putExtra("url", "rtmp://192.168.100.12/live/myStream");
//                i.putExtra("url", "rtmp://182.162.172.130:1935/live/stream.sdp");
//                startActivity(i);
//            }
//        });

    }

    private void setLayout(int mode) {
        LogUtil.d("setLayout - " + mode);
        if(mode == MODE_WIFI) {
            layoutWifi.setVisibility(View.VISIBLE);
            layoutBluetooth.setVisibility(View.GONE);
        } else {
            layoutWifi.setVisibility(View.GONE);
            layoutBluetooth.setVisibility(View.VISIBLE);
        }
    }

    private void initKeyMap() {
        keyMap = new HashMap<>();

        keyMap.put(R.id.btPower, KeyEvent.KEYCODE_STB_POWER);

        keyMap.put(R.id.btQuickHome, KeyEvent.KEYCODE_BUTTON_1);
        keyMap.put(R.id.btQuickVOD, KeyEvent.KEYCODE_BUTTON_2);
        keyMap.put(R.id.btQuickLive, KeyEvent.KEYCODE_BUTTON_3);
        keyMap.put(R.id.btQuickMypage, KeyEvent.KEYCODE_BUTTON_4);
        keyMap.put(R.id.btQuickSearch, KeyEvent.KEYCODE_BUTTON_5);
        keyMap.put(R.id.btQuickSetup, KeyEvent.KEYCODE_BUTTON_6);

        keyMap.put(R.id.btRed, KeyEvent.KEYCODE_PROG_RED);
        keyMap.put(R.id.btGreen, KeyEvent.KEYCODE_PROG_GREEN);
        keyMap.put(R.id.btYellow, KeyEvent.KEYCODE_PROG_YELLOW);
        keyMap.put(R.id.btBlue, KeyEvent.KEYCODE_PROG_BLUE);

        keyMap.put(R.id.btUp, KeyEvent.KEYCODE_DPAD_UP);
        keyMap.put(R.id.btDown, KeyEvent.KEYCODE_DPAD_DOWN);
        keyMap.put(R.id.btLeft, KeyEvent.KEYCODE_DPAD_LEFT);
        keyMap.put(R.id.btRight, KeyEvent.KEYCODE_DPAD_RIGHT);
        keyMap.put(R.id.btConfirm, KeyEvent.KEYCODE_DPAD_CENTER);
        keyMap.put(R.id.btPrev, KeyEvent.KEYCODE_BACK);
        keyMap.put(R.id.btExit, KeyEvent.KEYCODE_ESCAPE);

        keyMap.put(R.id.btMute, KeyEvent.KEYCODE_VOLUME_MUTE);
    }

    private void initButton() {
        for (Integer viewID : keyMap.keySet()) {
            View button = findViewById(viewID);
            button.setOnClickListener(this);
            button.setOnTouchListener(this);
        }
    }

    private void initVolume() {
        volumeBar = new ImageView[NUM_VOLUME_BAR];

        mLayoutVolume = (VolumeLayout) findViewById(R.id.layout_volume);
        mLayoutVolume.setOnVolumeLevelChangedListener(new VolumeLayout.OnVolumeLevelChangedListener() {
            @Override
            public void onVolumeLevelChanged(float positionRatio) {
                int level = Math.round(NUM_VOLUME_BAR * positionRatio);

                if(level < 0) level = 0;
                if(level > NUM_VOLUME_BAR) level = NUM_VOLUME_BAR;

                setVolumeLevel(level);
                sendVolumetoServer(level);
            }
        });

        for(int i = 0; i < NUM_VOLUME_BAR; i++) {
            volumeBar[i] = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
            params.weight = 8;
            volumeBar[i].setLayoutParams(params);
            volumeBar[i].setBackgroundResource(R.drawable.icon_volume_down);
            mLayoutVolume.addView(volumeBar[i]);

            if(i < NUM_VOLUME_BAR - 1) {
                View volumeBarGap = new View(this);
                LinearLayout.LayoutParams params2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
                params2.weight = 8;
                volumeBarGap.setLayoutParams(params);
                volumeBarGap.setVisibility(View.INVISIBLE);
                mLayoutVolume.addView(volumeBarGap);
            }
        }

        setVolumeLevel(NUM_VOLUME_BAR/2);
    }

    private void setVolumeLevel(int level) {
        for(int i = 0; i < NUM_VOLUME_BAR; i++) {
            if(i < level) {
                volumeBar[i].setBackgroundResource(R.drawable.icon_volume_up);
            } else {
                volumeBar[i].setBackgroundResource(R.drawable.icon_volume_down);
            }
        }
    }

    private void showSednKeyboard() {
        KeyboardDialog mKeyboardDialog  = new KeyboardDialog(this, mStbText);
        mKeyboardDialog.show();
    }
    private void showSetupDialog() {
        if(mConnMode == MODE_WIFI)
            rbWifi.setChecked(true);
        else {
            rbBluetooth.setChecked(true);
            startBTscanning();
        }

        if(mServerIPAddr == null)
            etIPAddress.setText("192.168.0.");
        else
            etIPAddress.setText(mServerIPAddr);
        etIPAddress.setSelection(etIPAddress.length());

        setConnectionText();

        setupDialog.show();
        Button button = setupDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        button.setOnClickListener(new IPDialogListener(setupDialog));
    }

    private void startBTscanning() {
        LogUtil.d("startBTscanning");

        if(!btAdapter.isEnabled()) {
            LogUtil.d("Bluetooth not enabled");
            boolean res = btAdapter.enable();
            LogUtil.d("Enable successful? - " + res);
        }

        btDeviceList.clear();
        btAdapter.startDiscovery();
        registerReceiver(btReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        registerReceiver(btReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
        layoutScanning.setVisibility(View.VISIBLE);
        ivScanning.startAnimation(ani_scanning);

        btScanStopHandler.removeMessages(0);
        btScanStopHandler.sendEmptyMessageDelayed(0, 10000);
    }

    private void stopBTscanning() {
        LogUtil.d("stopBTscanning");
        btAdapter.cancelDiscovery();
        layoutScanning.setVisibility(View.INVISIBLE);
        ivScanning.clearAnimation();
    }

    class IPDialogListener implements View.OnClickListener {
        private final Dialog dialog;
        public IPDialogListener(Dialog dialog) {
            this.dialog = dialog;
        }
        @Override
        public void onClick(View v) {
            /*
            try {
                String inputStr = etIPAddress.getText().toString();

                if(inputStr != null && !inputStr.isEmpty()) {
                    mServerAddr = InetAddress.getByName(inputStr);

                    SharedPreferences pref = getSharedPreferences("pref", MODE_PRIVATE);
                    SharedPreferences.Editor editor = pref.edit();
                    editor.putString(SPKEY_FOR_SERVER_ADDRESS, etIPAddress.getText().toString());
                    editor.commit();

                    dialog.dismiss();
                } else {
                    Toast.makeText(MainActivity.this, R.string.invalid_ip, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e){
                Toast.makeText(MainActivity.this, R.string.invalid_ip, Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
            */
            dialog.dismiss();
        }
    }

    @Override
    public void onClick(View v) {
        // onclick 리스너는 버튼 ripple 효과를 위해서 남겨둠
        /*
        int ID = v.getId();

        if(mServerAddr != null) {
            sendKeytoServer(keyMap.get(ID));
        } else {
            Toast.makeText(this, R.string.server_addr_null, Toast.LENGTH_SHORT).show();
        }
*/
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        //LogUtil.d("onTouchEvent " + event.getAction());

        if(event.getAction() == MotionEvent.ACTION_DOWN) {
            int ID = v.getId();

            // power off 키는 다이얼로그 띄움
            if(ID == R.id.btPower) {
                showPowerOffConfirmDialog();
            } else {
                sendKeytoServer(keyMap.get(ID));
            }
        }
        return true;
    }


    private void showPowerOffConfirmDialog() {
        AlertDialog.Builder alert_confirm = new AlertDialog.Builder(this);
        alert_confirm.setMessage(R.string.msg_poweroff_confirm).setCancelable(false).setPositiveButton("확인",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 'YES'
                        dialog.dismiss();
                        //sendKeytoServer(R.id.btPower);
                        sendKeytoServer(KeyEvent.KEYCODE_STB_POWER);

                    }
                }).setNegativeButton("취소",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 'No'
                        dialog.dismiss();
                        return;
                    }
                });
        AlertDialog alert = alert_confirm.create();
        alert.show();
    }

    public void sendKeytoServer(int keyCode) {
        mPacketSender.send(new RemoconPacket(RemoconPacket.SEND_KEYCODE, keyCode));
        //RemoteClient clientThread = new RemoteClient(this, mSocket, mServerAddr, RemoteClient.SEND_KEYCODE, keyCode);
        //clientThread.start();
    }

    public void sendStringtoServer(String str) {
        mPacketSender.send(new RemoconPacket(RemoconPacket.SEND_STRING, str));
        //RemoteClient clientThread = new RemoteClient(this, mSocket, mServerAddr, RemoteClient.SEND_STRING, str);
        //clientThread.start();
    }

    // 0 ~ 100 으로 normalize한다.
    public void sendVolumetoServer(int volume) {
        mPacketSender.send(new RemoconPacket(RemoconPacket.SEND_VOLUME, volume * 100 / NUM_VOLUME_BAR));
        //RemoteClient clientThread = new RemoteClient(this, mSocket, mServerAddr, RemoteClient.SEND_VOLUME, volume * 100 / NUM_VOLUME_BAR);
        //clientThread.start();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        LogUtil.d("onKeyDown " + keyCode);
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        LogUtil.d("onKeyUp " + keyCode);
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        LogUtil.d("dispatchKeyEvent " + event.toString());
        return super.dispatchKeyEvent(event);
    }

    public void showToast(int strID) {
        showToast(getResources().getString(strID));
    }

    public void showToast(String str) {
        final String msgStr = str;

        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msgStr, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveConfig() {
        SharedPreferences pref = getSharedPreferences("pref", MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putInt(SPKEY_FOR_CONNECTION_TYPE, mConnMode);
        if(mConnMode == MODE_WIFI)
            editor.putString(SPKEY_FOR_SERVER_ADDRESS, etIPAddress.getText().toString());
        editor.commit();
    }

    private void setConnectionText() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mConnMode == MODE_WIFI) {
                    if(mTCPSocket != null && mTCPSocket.isConnected()) {
                        tvCurConntection.setText("Wi-Fi로 연결됨 :\n          " + mServerIPAddr);
                    } else {
                        tvCurConntection.setText(R.string.msg_stb_not_connected);
                    }
                } else {
                    if (mBTSocket != null && mBTSocket.isConnected()) {
                        tvCurConntection.setText("Bluetooth로 연결됨 :\n          " + mBTDevice.getName());
                    } else {
                        tvCurConntection.setText(R.string.msg_stb_not_connected);
                    }
                }
            }
        });
    }

    class TCPConnectionThread extends Thread {
        String hostName;

        public TCPConnectionThread(String address) {
            hostName = address;
        }

        @Override
        public void run() {
            try {
                mTCPSocket = new Socket(InetAddress.getByName(hostName), REMOCON_PORT);
                mTCPInputStream = mTCPSocket.getInputStream();
                mTCPOutputStream = mTCPSocket.getOutputStream();
                LogUtil.d("TCP socket connected + " + mTCPSocket + ", " + mTCPInputStream + ", " + mTCPOutputStream);

                TCPInputThread tcpInputThread = new TCPInputThread(mTCPInputStream);
                tcpInputThread.start();

                mConnMode = MODE_WIFI;
                mServerIPAddr = etIPAddress.getText().toString();
                saveConfig();
                setConnectionText();

                showToast(R.string.msg_wifi_connected);
            } catch (UnknownHostException e) {
                showToast(R.string.invalid_ip);
            } catch (Exception e) {
                showToast(R.string.msg_wifi_connection_fail);
                e.printStackTrace();
            }
        }
    }

    class BTConnectionThread extends Thread {
        Context mContext;

        public BTConnectionThread(BluetoothDevice device, Context context) {
            mContext = context;
            mBTDevice = device;

//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//                device.setPairingConfirmation(true);
//                //successfull pairing
//            } else {
//                //impossible to automatically perform pairing
//            }

            try {
                if(mBTSocket != null) {
                    mBTSocket.close();
                    LogUtil.d("Closing existing BT socket");
                }
                mBTSocket = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(BLUETOOTH_UUID));
                LogUtil.d("new BT socket created : " + mBTSocket);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        @Override
        public void run() {
            try {
                mBTSocket.connect();
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    mBTSocket.close();
                } catch (IOException e2) {
                }
                ((MainActivity)mContext).showToast(R.string.msg_bluetooth_connect_failed);
                setConnectionText();
                return;
            }
            ((MainActivity)mContext).showToast(R.string.msg_bluetooth_connected);
            LogUtil.d("bluetooth connected!!!!");

            try {
                mBTInputStream = mBTSocket.getInputStream();
                mBTOutputStream = mBTSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            BTInputThread inputThread = new BTInputThread(mBTInputStream);
            inputThread.start();

            mConnMode = MODE_BLUETOOTH;
            saveConfig();
            setConnectionText();
        }
    }

    class BTDeviceAdapter extends ArrayAdapter<BluetoothDevice> {
        private ArrayList<BluetoothDevice> items;

        public BTDeviceAdapter(Context context, int resID, ArrayList<BluetoothDevice> items) {
            super(context, resID, items);
            this.items = items;
        }

        public void add(BluetoothDevice device) {
            LogUtil.d("add " + device.getName());
            items.add(device);
            notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LogUtil.d("getView " + position + "/" + items.size());
            View v = convertView;
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.item_bluetooth_device, null);
                LogUtil.d("create view");
            }
            BluetoothDevice data = items.get(position);
            if(data != null) {
                TextView tvBTDevice = (TextView)v.findViewById(R.id.tvBTDevice);
                tvBTDevice.setText(data.getName());
            }
            return v;
        }
    }

    class PacketSender extends Thread {
        ArrayBlockingQueue<RemoconPacket> sendQueue;

        public PacketSender() {
            sendQueue = new ArrayBlockingQueue<>(1024);
        }

        public void send(RemoconPacket packet) {
            try {
                sendQueue.put(packet);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            while(true) {
                try {
                    RemoconPacket packet = sendQueue.take();
                    LogUtil.d("packet send~~");
                    if(mConnMode == MODE_WIFI) {
                        if(mTCPSocket != null && mTCPSocket.isConnected() && mTCPOutputStream != null) {
                            mTCPOutputStream.write(packet.getByteArray());
                        } else {
                            showToast(R.string.msg_stb_not_connected);
                        }
                    } else {
                        if(mBTSocket != null && mBTSocket.isConnected() && mBTOutputStream != null) {
                            mBTOutputStream.write(packet.getByteArray());
                        } else {
                            showToast(R.string.msg_stb_not_connected);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    showToast(R.string.msg_socket_failure);
                }
            }
        }
    }

    private class BTInputThread extends Thread {
        InputStream mInputStream;

        public BTInputThread(InputStream stream) {
            mInputStream = stream;
        }

        @Override
        public void run() {
            byte[] buf = new byte[1024];

            while(true) {
                try {
                    int numRead = mInputStream.read(buf);
                    if(numRead == -1) {
                        LogUtil.d("BT Connection lost");
                        disconnectEverything();
                        setConnectionText();
                        break;
                    }
                    LogUtil.d("BT message - " + buf);
                    processServerCmd(buf);
                } catch (IOException e) {
                    e.printStackTrace();
                    LogUtil.d("BTInputThread finished");
                    break;
                }
            }
        }
    }

    private class TCPInputThread extends Thread {
        InputStream mInputStream;

        public TCPInputThread(InputStream stream) {
            mInputStream = stream;
        }

        @Override
        public void run() {
            byte[] buf = new byte[1024];

            while(true) {
                try {
                    int numRead = mInputStream.read(buf);
                    if(numRead == -1) {
                        LogUtil.d("TCP Connection Lost");
                        disconnectEverything();
                        setConnectionText();
                        break;
                    }
                    LogUtil.d("TCP message - " + buf);
                    processServerCmd(buf);
                } catch (IOException e) {
                    e.printStackTrace();
                    LogUtil.d("TCPInputThread finished");
                    break;
                }
            }
        }
    }

    private void processServerCmd(byte[] buf) {
        try {
            ByteArrayInputStream baos = new ByteArrayInputStream(buf);
            ObjectInputStream oos = new ObjectInputStream(baos);
            String cmd = (String) oos.readObject();
            LogUtil.d(cmd);

            if (cmd.equals("SednText")) {
                final String stbText = (String) oos.readObject();
                mStbText = stbText;
                LogUtil.d("Text :  " + stbText);
            }
            else if(cmd.equals("SednVolume")) {
                final String stbVolume = (String) oos.readObject();
                LogUtil.d("Volume :  " + stbVolume);

                // todo: UI 갱신
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        float fVolume = Integer.valueOf(stbVolume) / 100.0f;
                        int level = Math.round(NUM_VOLUME_BAR * fVolume);

                        if(level < 0) level = 0;
                        if(level > NUM_VOLUME_BAR) level = NUM_VOLUME_BAR;

                        setVolumeLevel(level);
                    }
                });


            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void disconnectEverything() {
        try {
            if (mBTSocket != null && mBTSocket.isConnected()) {
                mBTInputStream.close();
                mBTOutputStream.close();
                mBTSocket.close();
                mBTInputStream = null;
                mBTOutputStream = null;
                mBTSocket = null;
            }
            if (mTCPSocket != null && mTCPSocket.isConnected()) {
                mTCPInputStream.close();
                mTCPOutputStream.close();
                mTCPSocket.close();
                mTCPInputStream = null;
                mTCPOutputStream = null;
                mTCPSocket = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 권한 체크
     * @return
     */
    private boolean checkPermission() {
        boolean bRes = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            final String[] permissions = new String[] { android.Manifest.permission.BLUETOOTH, android.Manifest.permission.BLUETOOTH_ADMIN, android.Manifest.permission.ACCESS_FINE_LOCATION
                                                        /*, android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.CAMERA */};

            boolean bGranted = true;

            for (String permission : permissions) {

                int result = PermissionChecker.checkSelfPermission(this, permission);
                if (result != PermissionChecker.PERMISSION_GRANTED) {
                    bGranted = false;
                    break;
                }
            }

            if (bGranted) {
                // 권한 모두 수락했음
                init();
            } else {
                ActivityCompat.requestPermissions(MainActivity.this, permissions, 123);
            }
        } else {
            bRes = true;
            // 마시멜로 미만이면 바로 정상적인 진행
            init();
        }

        return bRes;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        int nGrantCnt = 0;

        for (int i = 0; i < permissions.length; i++) {
            String p = permissions[i];

            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                nGrantCnt++;
            }
        }

        if (nGrantCnt == permissions.length) {
            // 권한 모두 수락했음
            init();
        } else {
            Toast.makeText(getApplicationContext(), "권한을 수락하지 않으시면 사용하실 수 없습니다", Toast.LENGTH_LONG).show();
            finish();
        }
    }




}
