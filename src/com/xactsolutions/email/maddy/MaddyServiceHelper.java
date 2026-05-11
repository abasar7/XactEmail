package com.xactsolutions.email.maddy;

import com.xactsolutions.email.util.SystemUtils;

public class MaddyServiceHelper {

    private static final String STATUS_OUTPUT_PREFIX = "Active: ";
    private static final String[] STATUS_COMMAND = {"/bin/systemctl", "status", "maddy"};
    private static final String[] RESTART_COMMAND = {"/bin/systemctl", "restart", "maddy"};


    /**
     * @return true if maddy service is active. false otherwise
     * */
    public static boolean getServiceStatus() {
        String[] result = SystemUtils.executeCommand(STATUS_COMMAND);
        if (!result[0].equals("0"))
            throw new RuntimeException("Unknown exit code when executing Maddy status command: " + result[0]);

        return result[1].lines()
            .anyMatch(line -> {   // find out inactive status
                line = line.trim();
                if (line.startsWith(STATUS_OUTPUT_PREFIX)) {
                    line = line.substring(STATUS_OUTPUT_PREFIX.length()).trim();
                    return line.toLowerCase().startsWith("inactive");
                }
                return true;    // assume inactive by default
            });
    }

    static void restartService() {
        String[] result = SystemUtils.executeCommand(RESTART_COMMAND);
        if (!result[0].equals("0"))
            throw new RuntimeException("Unknown exit code when executing Maddy restart command: " + result[0]);
    }

    static void createAcc(String username, String password) {
        String[] result = SystemUtils.executeCommand(new String[]{"maddy", "creds", "create", username}, password);
        if (!result[0].equals("0"))
            throw new RuntimeException("Unknown exit code when executing Maddy credential create command: " + result[0]);
    }

    static void removeAcc(String username) {
        String[] result = SystemUtils.executeCommand(new String[]{"maddy", "creds", "remove", username}, "y");
        if (!result[0].equals("0"))
            throw new RuntimeException("Unknown exit code when executing Maddy credential create command: " + result[0]);
    }

}
