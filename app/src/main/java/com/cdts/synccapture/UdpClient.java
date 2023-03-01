package com.cdts.synccapture;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.cdts.beans.Command;
import com.google.gson.Gson;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.List;

public class UdpClient implements Runnable {


    private final static String TAG = "UdpClient";
    private boolean isActive;

    private final Context mContext;

    @SuppressLint("StaticFieldLeak")
    private static UdpClient sUdpClient;

    public static UdpClient getInstance(Context context) {
        if (sUdpClient == null) {
            sUdpClient = new UdpClient(context);
        }
        return sUdpClient;
    }

    private UdpClient(Context context) {
        mContext = context;
    }

    public void setListener(OnCommandReceivedListener listener) {
        mListener = listener;
        Log.d(TAG, "setListener: " + mListener + " " + this + " " + isActive);
    }

    public void start() {
        if (!isActive) {
            isActive = true;
            new Thread(this).start();
        }
    }

    public void stop() {
        isActive = false;
    }

    private static final int PORT = 8989;
    private static final byte[] mBuffer = new byte[1024 * 60];//最多接收60K
    private static final int SLP = 200;

    private OnCommandReceivedListener mListener;

    interface OnCommandReceivedListener {
        void onCommandReceived(Command command);
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what == 1 && mListener != null) {
                mListener.onCommandReceived((Command) msg.obj);
            } else {
                Log.e(TAG, this + " mListener: " + mListener + " what:" + msg.what);
            }
        }
    };

    public static final boolean isGson = true;

    @Override
    public void run() {
        Log.d(TAG, "UPD client start " + isActive);
        WifiManager manager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock lock = manager.createMulticastLock("udp wifi");
        lock.acquire();
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(new InetSocketAddress(PORT));
            socket.setReuseAddress(true);
            socket.setBroadcast(true);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        while (isActive) {
            try {
                DatagramPacket packet = new DatagramPacket(mBuffer, mBuffer.length);
                socket.receive(packet);
                InetAddress address = packet.getAddress();

                if (isGson) {
                    String msg = new String(packet.getData(), 0, packet.getLength());
                    Log.d(TAG, address.toString() + " receive run: " + msg);
                    Gson gson = new Gson();
                    Command command = gson.fromJson(msg, Command.class);
                    mHandler.obtainMessage(1, command).sendToTarget();
                } else {

                    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
                    ObjectInputStream objectInputStream = new ObjectInputStream(new BufferedInputStream(byteArrayInputStream));
                    Object object = objectInputStream.readObject();
                    Log.d(TAG, address.toString() + " receive run: " + object);
                    objectInputStream.close();
                    if (object instanceof Command) {
                        mHandler.obtainMessage(1, object).sendToTarget();
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        lock.release();
        socket.close();
        Log.d(TAG, "UPD client stop");
    }
}
