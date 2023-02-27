package com.cdts.server;

import com.cdts.beans.Command;
import com.cdts.beans.DeviceBean;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.cdts.server.AdbScripts.ADB_INSTALL;
import static com.cdts.server.AdbScripts.ADB_LAUNCH_CAMERA;

public class SyncCaptureMain extends JFrame implements ActionListener {

    SyncCaptureMain() {
        setTitle("SyncCaptureServer");
        setSize(640, 480);
        initialized();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(true);
        setVisible(true);
    }

    UdpServer mUdpServer = new UdpServer();

    private static final int BTN_HEIGHT = 30;
    private static final String AudioSyncStart = "Start AudioSync";
    private static final String AudioSyncStop = "Stop AudioSync";
    private static final String InstallApk = "Install APK";
    private static final String ChooseApk = "Choose APK";

    private static final String REFRESH_DEVICE_LIST = "Refresh Dev";
    private static final String LAUNCH_APP = "Launch App";

    private static final String SET_PARAM = "Set Param";
    private final List<JButton> mButtonList = new ArrayList<>();

    private final JTextArea mLogTextArea = new JTextArea();
    private String mApkPath;
    private List<DeviceBean> mDeviceBeans = new ArrayList<>();

    void initialized() {
        mButtonList.add(new JButton(ChooseApk));
        mButtonList.add(new JButton(InstallApk));
        mButtonList.add(new JButton(AudioSyncStart));
        mButtonList.add(new JButton(REFRESH_DEVICE_LIST));
        mButtonList.add(new JButton(LAUNCH_APP));
        mButtonList.add(new JButton(SET_PARAM));

        for (int i = 0; i < mButtonList.size(); i++) {
            JButton button = mButtonList.get(i);
            button.setBounds(0, BTN_HEIGHT * i, 150, BTN_HEIGHT);
            button.addActionListener(this);
            add(button);
        }


        String[] labelText = {"NameOfUpcoming:", "ImageSize:", "FrameRate:", "ImageFormat:", "SaveType:", "3AMode:", "ISO:", "Exposure:", "ForceDistance:", "AWB offset:"};

        for (int i = 0; i < labelText.length; i++) {
            JLabel label = new JLabel(labelText[i]);
            label.setBounds(300, BTN_HEIGHT * i, 150, BTN_HEIGHT);
            add(label);
        }


        mLogTextArea.setLineWrap(true);
        mLogTextArea.setWrapStyleWord(true);
        mLogTextArea.setEditable(false);
        mLogTextArea.setCaretPosition(mLogTextArea.getText().length());
        JScrollPane scrollPane = new JScrollPane(mLogTextArea);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBounds(0, (getHeight() - (BTN_HEIGHT * 8)), getWidth(), BTN_HEIGHT * 6);

        add(scrollPane);
        setLayout(null);

        mUdpServer.setOnCommandSendListener(command -> appendLog("Send:" + command.toString()));

    }

    private void appendLog(String s) {
        mLogTextArea.append(s + "\n");
    }

    private void appendLog(List<String> s) {
        for (String str : s) {
            mLogTextArea.append(str + "\n");
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String command = e.getActionCommand();
        System.out.println("actionPerformed command:" + command);
        switch (command) {
            case ChooseApk:
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                fileChooser.setFileFilter(new FileNameExtensionFilter("Android apk file", "apk"));
                int option = fileChooser.showOpenDialog(this);
                if (option == JFileChooser.APPROVE_OPTION) {
                    File file = fileChooser.getSelectedFile();
                    mApkPath = file.getAbsolutePath();
                    System.out.println("SelectedFile:" + file);
                    appendLog(ChooseApk + ":" + file.getAbsolutePath());
                }
                break;
            case InstallApk:
                if (mApkPath == null) {
                    appendLog("Apk Path is invalid, please choose apk file first!");
                } else if (mDeviceBeans.isEmpty()) {
                    appendLog("Device list is empty, please click '" + REFRESH_DEVICE_LIST + "' to refresh and try again");
                } else {
                    for (DeviceBean bean : mDeviceBeans) {
                        List<String> ret = AdbScripts.installApk(bean, mApkPath);
                        appendLog(ret);
                    }
                }
                break;
            case AudioSyncStart:
            case AudioSyncStop:
                JButton button = (JButton) e.getSource();
                button.setText(command.equals(AudioSyncStart) ? AudioSyncStop : AudioSyncStart);
                mUdpServer.sendCmd(command.equals(AudioSyncStart) ? Command.AUDIO_SYNC_START : Command.AUDIO_SYNC_STOP, 1);
                break;

            case REFRESH_DEVICE_LIST:
                mDeviceBeans = AdbScripts.getDevice();
                if (checkDeviceList("Device not found!")) {
                    for (DeviceBean d : mDeviceBeans) {
                        appendLog(d.toString());
                    }
                }
                break;

            case LAUNCH_APP:
                if (checkDeviceList("Device not found!")) {
                    for (DeviceBean bean : mDeviceBeans) {
                        List<String> ret1 = AdbScripts.grantPermission(bean);
                        appendLog(ret1);
                        List<String> ret2 = AdbScripts.launchApk(bean);
                        appendLog(ret2);
                    }
                }
                break;

            case SET_PARAM:
                break;
        }

    }

    boolean checkDeviceList(String msg) {
        if (mDeviceBeans.isEmpty()) {
            appendLog(msg);
            return false;
        }
        return true;
    }
}
