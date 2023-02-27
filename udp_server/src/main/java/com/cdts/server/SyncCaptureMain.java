package com.cdts.server;

import com.cdts.beans.AdbScripts;
import com.cdts.beans.Command;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

public class SyncCaptureMain extends JFrame implements ActionListener {

    SyncCaptureMain() {
        setTitle("SyncCaptureServer");
        setSize(640, 480);
        initialized();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        setVisible(true);
    }

    UdpServer mUdpServer = new UdpServer();

    private static final int BTN_HEIGHT = 30;
    private static final String AudioSyncStart = "Start AudioSync";
    private static final String AudioSyncStop = "Stop AudioSync";
    private static final String InstallApk = "Install APK";
    private static final String ChooseApk = "Choose APK";
    private final List<JButton> mButtonList = new ArrayList<>();

    private final JTextArea mLogTextArea = new JTextArea();
    private String mApkPath;

    void initialized() {
        mButtonList.add(new JButton(ChooseApk));
        mButtonList.add(new JButton(InstallApk));
        mButtonList.add(new JButton(AudioSyncStart));

        for (int i = 0; i < mButtonList.size(); i++) {
            JButton button = mButtonList.get(i);
            button.setBounds(0, BTN_HEIGHT * i, 140, BTN_HEIGHT);
            button.addActionListener(this);
            add(button);
        }


        mLogTextArea.setLineWrap(true);
        mLogTextArea.setWrapStyleWord(true);
        mLogTextArea.setCaretPosition(mLogTextArea.getText().length());
        JScrollPane scrollPane = new JScrollPane(mLogTextArea);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBounds(0, (int) (getHeight() - (BTN_HEIGHT * 6.5)), getWidth(), BTN_HEIGHT * 6);

        add(scrollPane);
        setLayout(null);

        mUdpServer.setOnCommandSendListener(command -> appendLog("Send:" + command.toString()));

    }

    private void appendLog(String s) {
        mLogTextArea.append(s + "\n");
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
                    appendLog("Apk Path:" + file.getAbsolutePath());
                }
                break;
            case InstallApk:
                if (mApkPath == null) {
                    appendLog("Apk Path is invalid, please choose apk file first!");
                } else {
                    AdbScripts.installApk(mApkPath);
                }
                break;
            case AudioSyncStart:
            case AudioSyncStop:
                JButton button = (JButton) e.getSource();
                button.setText(command.equals(AudioSyncStart) ? AudioSyncStop : AudioSyncStart);
                mUdpServer.sendCmd(command.equals(AudioSyncStart) ? Command.AUDIO_SYNC_START : Command.AUDIO_SYNC_STOP, 1);
                break;
        }

    }
}
