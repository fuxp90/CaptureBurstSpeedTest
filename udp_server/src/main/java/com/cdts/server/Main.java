package com.cdts.server;

import com.formdev.flatlaf.FlatIntelliJLaf;


public class Main {
    public static void main(String[] args) {

        FlatIntelliJLaf.setup();
        new SyncCaptureMain();
    }
}