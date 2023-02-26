package com.cdts.synccapture;

import org.junit.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Scanner;

public class ServerTestCase {

    @Test
    public void main() throws IOException {

        new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("asdasdasdasd.");
                int index = 0;
                while (true) {
                    DatagramSocket dgSocket = null;
                    try {
                        Thread.sleep(50);
                        System.out.println("122123123wfsdfsdfsdfdf. " +index++);
                        dgSocket = new DatagramSocket();
                        String mString = "asdasdasd";
                        byte[] b = mString.getBytes();
                        DatagramPacket dgPacket = new DatagramPacket(b, b.length, InetAddress.getByName("192.168.48.255"), 12345);
                        dgSocket.send(dgPacket);
                        dgSocket.close();
                        System.out.println("send message is ok.");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

            }
        }).start();

    }
}
