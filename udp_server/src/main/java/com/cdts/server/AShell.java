package com.cdts.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class AShell {

    public static List<String> exec(String cmd) {
        BufferedReader input = null;
        Process process = null;
        try {
            List<String> ret = new ArrayList<>();
            System.out.println("AShell cmd:" + cmd);
            process = Runtime.getRuntime().exec(cmd);
            input = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = null;
            while ((line = input.readLine()) != null) {
                if (!line.isEmpty()) {
                    ret.add(line);
                }
            }
            System.out.println("AShell ret:" + ret);
            ret.add(0, cmd);
            return ret;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (process != null) {
                process.destroy();
            }
        }
        return null;
    }

}
