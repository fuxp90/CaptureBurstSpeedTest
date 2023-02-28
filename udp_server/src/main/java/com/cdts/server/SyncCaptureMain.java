package com.cdts.server;

import com.cdts.beans.Command;
import com.cdts.beans.DeviceBean;
import com.cdts.beans.ParamBean;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SyncCaptureMain extends JFrame implements ActionListener {

    SyncCaptureMain() {
        setTitle("SyncCaptureServer");
        setSize(640, 640);
        initialized();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(true);
        setVisible(true);
    }

    UdpServer mUdpServer = new UdpServer();

    private static final int BTN_HEIGHT = 30;
    private static final int BTN_WIDTH = 150;
    private static final String AudioSyncStart = "Start AudioSync";
    private static final String AudioSyncStop = "Stop AudioSync";
    private static final String InstallApk = "Install APK";
    private static final String ChooseApk = "Choose APK";

    private static final String REFRESH_DEVICE_LIST = "Refresh Dev";
    private static final String LAUNCH_APP = "Launch App";

    private static final String SET_PARAM = "Set Param";

    private static final String START_CAPTURE = "Start Capture";
    private static final String STOP_CAPTURE = "Stop Capture";

    private static final String PULL_IMAGES = "Pull Images";
    private final List<JButton> mButtonList = new ArrayList<>();

    private final JTextArea mLogTextArea = new JTextArea();
    private String mApkPath;
    private List<DeviceBean> mDeviceBeans = new ArrayList<>();

    private final ParamBean mParamBean = new ParamBean();
    JTextField mNameOfUpcomingInput = new JTextField("TestA");
    JTextField mImageSizeInput = new JTextField("4000*3000");
    JTextField[] mMode3aText = new JTextField[3];
    JCheckBox mClearImageCache = new JCheckBox();

    void initialized() {
        mButtonList.add(new JButton(ChooseApk));
        mButtonList.add(new JButton(InstallApk));
        mButtonList.add(new JButton(AudioSyncStart));
        mButtonList.add(new JButton(REFRESH_DEVICE_LIST));
        mButtonList.add(new JButton(LAUNCH_APP));
        mButtonList.add(new JButton(SET_PARAM));
        mButtonList.add(new JButton(START_CAPTURE));
        mButtonList.add(new JButton(PULL_IMAGES));

        for (int i = 0; i < mButtonList.size(); i++) {
            JButton button = mButtonList.get(i);
            button.setBounds(0, BTN_HEIGHT * i, BTN_WIDTH, BTN_HEIGHT);
            button.addActionListener(this);
            add(button);
        }


        String[] labelText = {"NameOfUpcoming:", "ImageSize:", "FrameRate:", "ImageFormat:", "SaveType:",
                "3AMode:", "ISO:", "Exposure(NS):", "ForceDistance:", "AWB offset:", "ClearImageCache:"};

        JLabel[] label = new JLabel[labelText.length];

        for (int i = 0; i < labelText.length; i++) {
            label[i] = new JLabel(labelText[i]);
            label[i].setHorizontalAlignment(SwingConstants.TRAILING);
            label[i].setBounds((int) (BTN_WIDTH * 1.3f), BTN_HEIGHT * i, BTN_WIDTH, BTN_HEIGHT);
            add(label[i]);

        }

        mNameOfUpcomingInput.setBounds(label[0].getX() + label[0].getWidth(), label[0].getY(), BTN_WIDTH, BTN_HEIGHT);
        add(mNameOfUpcomingInput);

        mImageSizeInput.setBounds(label[1].getX() + label[1].getWidth(), label[1].getY(), BTN_WIDTH, BTN_HEIGHT);
        add(mImageSizeInput);


        int defaultFps = 8;
        mParamBean.setFrameRate(defaultFps);
        JSlider rateInput = new JSlider();
        rateInput.setMinimum(1);
        rateInput.setMaximum(30);
        rateInput.setValue(defaultFps);
        rateInput.setBounds(label[2].getX() + label[2].getWidth(), label[2].getY(), BTN_WIDTH, BTN_HEIGHT);
        JLabel rateValue = new JLabel("fps:" + defaultFps);
        rateValue.setBounds(rateInput.getX() + rateInput.getWidth(), rateInput.getY(), BTN_WIDTH, BTN_HEIGHT);
        rateInput.addChangeListener(e -> {
            mParamBean.setFrameRate(rateInput.getValue());
            rateValue.setText("fps:" + rateInput.getValue());
        });
        add(rateValue);
        add(rateInput);


        List<String> fmt = new ArrayList<>();
        fmt.add("JPEG");
        fmt.add("RAW10");
        fmt.add("RAW_SENOR");
        fmt.add("YUV_420_888");

        JComboBox imageFmt = new JComboBox();
        for (String s : fmt) {
            imageFmt.addItem(s);
        }
        mParamBean.setImageFormat(0);
        imageFmt.setBounds(label[3].getX() + label[3].getWidth(), label[3].getY(), BTN_WIDTH, BTN_HEIGHT);
        imageFmt.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                System.out.println(e.getItem());
                mParamBean.setImageFormat(fmt.indexOf(e.getItem()));
            }
        });
        add(imageFmt);


        JComboBox saveType = new JComboBox();
        saveType.addItem("FLASH");
        saveType.addItem("RAM");
        mParamBean.setSaveType(0);
        saveType.setBounds(label[4].getX() + label[4].getWidth(), label[4].getY(), BTN_WIDTH, BTN_HEIGHT);
        saveType.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    System.out.println(e.getItem());
                    mParamBean.setSaveType("FLASH".equals(e.getItem()) ? 0 : 1);
                }
            }
        });
        add(saveType);


        JComboBox mode3a = new JComboBox();
        mode3a.addItem("Auto");
        mode3a.addItem("Manual");
        mParamBean.setAuto3A(true);
        mode3a.setBounds(label[5].getX() + label[5].getWidth(), label[5].getY(), BTN_WIDTH, BTN_HEIGHT);
        mode3a.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                System.out.println(e.getItem());
                mParamBean.setAuto3A("Auto".equals(e.getItem()));
            }
        });
        add(mode3a);


        String[] def3aValue = {"444", "10000000", "0"};
        for (int i = 0; i < mMode3aText.length; i++) {
            mMode3aText[i] = new JTextField(def3aValue[i]);
            mMode3aText[i].setBounds(label[6 + i].getX() + label[6 + i].getWidth(), label[6 + i].getY(), BTN_WIDTH, BTN_HEIGHT);
            add(mMode3aText[i]);
        }


        int defaultAwb = 0;
        JSlider mAwbInput = new JSlider();
        mAwbInput.setMinimum(-50);
        mAwbInput.setMaximum(50);
        mAwbInput.setValue(defaultAwb);
        mAwbInput.setBounds(label[9].getX() + label[9].getWidth(), label[9].getY(), BTN_WIDTH, BTN_HEIGHT);
        JLabel awbValue = new JLabel("offset:" + defaultAwb);
        awbValue.setBounds(mAwbInput.getX() + mAwbInput.getWidth(), mAwbInput.getY(), BTN_WIDTH, BTN_HEIGHT);
        mAwbInput.addChangeListener(e -> {
            awbValue.setText("offset:" + mAwbInput.getValue());
            mParamBean.setWhiteBalanceOffset(mAwbInput.getValue());
        });
        add(awbValue);
        add(mAwbInput);


        mClearImageCache.setSelected(true);
        mClearImageCache.setBounds(label[10].getX() + label[10].getWidth(), label[10].getY(), BTN_WIDTH, BTN_HEIGHT);
        add(mClearImageCache);


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
                List<String> list = AdbScripts.getDevices();
                appendLog(list);
                mDeviceBeans = AdbScripts.parseDeviceBean(list);
                if (checkDeviceList("Device not found!")) {
                    for (int i = 0; i < mDeviceBeans.size(); i++) {
                        DeviceBean d = mDeviceBeans.get(i);
                        appendLog("device:[" + i + "]" + d.toString());
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
                mParamBean.setNameOfUpcoming(mNameOfUpcomingInput.getText());
                mParamBean.setImageSize(mImageSizeInput.getText());
                boolean err = false;
                try {
                    mParamBean.setIso(Integer.parseInt(mMode3aText[0].getText()));
                } catch (Exception ex) {
                    err = true;
                    appendLog(ex.toString());
                }
                try {
                    mParamBean.setExposure(Long.parseLong(mMode3aText[1].getText()));
                } catch (Exception ex) {
                    err = true;
                    appendLog(ex.toString());
                }
                try {
                    mParamBean.setFocusDistance(Float.parseFloat(mMode3aText[2].getText()));
                } catch (Exception ex) {
                    err = true;
                    appendLog(ex.toString());
                }
                mParamBean.setClearLocalCache(mClearImageCache.isSelected());
                System.out.println(mParamBean);
                if (!err) {
                    Command.SET_PARAM.setParamBean(mParamBean);
                    mUdpServer.sendCmd(Command.SET_PARAM, 1);
                }
                break;

            case START_CAPTURE:
            case STOP_CAPTURE:
                JButton btn = (JButton) e.getSource();
                btn.setText(command.equals(START_CAPTURE) ? STOP_CAPTURE : START_CAPTURE);
                mUdpServer.sendCmd(command.equals(START_CAPTURE) ? Command.CAPTURE_START : Command.CAPTURE_STOP, 1);
                break;
            case PULL_IMAGES:
                if (checkDeviceList("Device not found!")) {
                    for (DeviceBean bean : mDeviceBeans) {
                        List<String> ret = AdbScripts.pullImages(bean);
                        appendLog(ret);
                    }
                }
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
