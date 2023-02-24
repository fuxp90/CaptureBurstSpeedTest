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
        DatagramSocket dgSocket = new DatagramSocket();
        System.out.println("请输入您要发送的信息：");
        Scanner mScanner = new Scanner(System.in);
        String mString = "asdasdasd";
        byte[] b = mString.getBytes();
        DatagramPacket dgPacket = new DatagramPacket(b, b.length, InetAddress.getByName("192.168.48.255"), 12345);
        dgSocket.send(dgPacket);
        dgSocket.close();
        System.out.println("send message is ok.");
    }
}
