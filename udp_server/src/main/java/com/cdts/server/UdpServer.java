package com.cdts.server;

import com.cdts.beans.Command;

import javax.swing.*;
import java.net.*;

public class UdpServer implements Runnable {

    private boolean isActive = false;

    private Command mCommand;
    private int mSendCount;
    private final ConditionVariable mConditionVariable = new ConditionVariable();

    private final static int SLP = 100;

    private static final String IP = "255.255.255.255";
    private static final int PORT = 8989;

    public UdpServer() {
        isActive = true;
        new Thread(this).start();
    }

    private OnCommandSendListener mOnCommandSendListener;

    public void setOnCommandSendListener(OnCommandSendListener onCommandSendListener) {
        mOnCommandSendListener = onCommandSendListener;
    }

    public interface OnCommandSendListener {
        void onCommandSend(Command command);
    }

    @Override
    public void run() {
        System.out.println("UdpServer Start...");
        DatagramSocket socket;
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        while (isActive) {
            try {
                mConditionVariable.block();
                if (mCommand != null) {
                    System.out.println("sendBroadcast E [" + mSendCount + "] " + mCommand);
                    sendBroadcast(socket, mCommand.getJsonByte());
                    System.out.println("sendBroadcast X");
                    if (mOnCommandSendListener != null) {
                        SwingUtilities.invokeLater(() -> mOnCommandSendListener.onCommandSend(mCommand));
                    }
                }
                mConditionVariable.close();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        socket.close();
    }

    void sendBroadcast(DatagramSocket socket, byte[] data) throws InterruptedException {
        for (int i = 0; i < mSendCount; i++) {
            try {
                DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(IP), PORT);
                socket.send(packet);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            Thread.sleep(SLP);
        }
    }

    public boolean isActive() {
        return isActive;
    }

    public void stop() {
        isActive = false;
    }

    public void sendCmd(Command command, int count) {
        mCommand = command;
        mSendCount = count;
        mConditionVariable.open();
    }

}
