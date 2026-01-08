package com.xactsolutions.email.maddy;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

@Slf4j
public class MaddyServiceHelper {

    private static final String STATUS_OUTPUT_PREFIX = "Active: ";
    private static final String[] STATUS_COMMAND = {"/bin/systemctl", "status", "maddy"};
    private static final String[] RESTART_COMMAND = {"/bin/systemctl", "restart", "maddy"};


    /**
     * @return true if maddy service is active. false otherwise
     * */
    public static boolean getServiceStatus() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(STATUS_COMMAND);
            processBuilder.redirectErrorStream(true);   // error data will be merged to regular input stream (stdout)
            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null)
                    if (line.trim().startsWith(STATUS_OUTPUT_PREFIX))
                        return !line.trim().substring(STATUS_OUTPUT_PREFIX.length()).startsWith("inactive");
            }
            int exitCode = process.waitFor();
            log.debug("Status exit code {}", exitCode);
            if (exitCode != 0) throw new RuntimeException("Unexpected maddy status exit code: " + exitCode);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to restart maddy server", e);
        }
        return false;
    }

    static void restartService() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(RESTART_COMMAND);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) throw new RuntimeException("Unexpected maddy restart exit code: " + exitCode);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to restart maddy server", e);
        }
    }

}
