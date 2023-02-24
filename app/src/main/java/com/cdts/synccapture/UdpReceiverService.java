package com.cdts.synccapture;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public class UdpReceiverService extends Service {


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void start() {
        try {
            DatagramSocket dgSocket = null;
            int port = 12345;
            dgSocket = new DatagramSocket(null);
            dgSocket.setReuseAddress(true);
            dgSocket.bind(new InetSocketAddress(port));
            byte[] by = new byte[1024];
            DatagramPacket packet = new DatagramPacket(by, by.length);
            dgSocket.receive(packet);
            String str = new String(packet.getData(), 0, packet.getLength());
            System.out.println("接收到的数据为：" + str);
            Log.v("WANGRUI", "已获取服务器端发过来的数据。。。。。" + str);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
