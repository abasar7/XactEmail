package com.xactsolutions.email.util;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class SystemUtils {

    private static final String HOST_COMMAND_LOCATION = "/usr/bin/host";
    private static final String SPF_DATA_PREFIX = "v=spf1 ";

    private static final Map<String, String> DNS_RECORD_PREFIX = new HashMap<>(){{
        put("A", "%s has address ");     // abc.co has address 18.31.111.50
        put("AAAA", "%s has IPv6 address address "); // abc.co has IPv6 address 2206:4300:3120::ac36:c5a6
        put("MX", "%s mail is handled by ");    // abc.co mail is handled by 7 18.31.111.50
        put("TXT", "%s descriptive text ");    // abc.co descriptive text "v=spf1 mx ~all"
    }};

    /**
     * @param command command to execute with arguments
     * @return string from standard system output/error after command being executed
     * @apiNote an example command <br>
     * ["/bin/systemctl", "status", "nginx"] <br>
     * and this will return the output string
     * */
    public static @NonNull String[] executeCommand(String[] command) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);   // error data will be merged to regular input stream (stdout)
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null)
                    output.append(line).append("\n");
            }
            int exitCode = process.waitFor();
            log.debug("Command executed and finishes with exit code {}", exitCode);
            return new String[] {String.valueOf(exitCode), output.toString()};
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error when executing a command", e);
        }
    }

    /**
     * @apiNote return example, <br>
     * ["18.31.111.50", ...] - for A record <br>
     * ["2206:4300:3120::ac36:c5a6", ...] - for AAAA record <br>
     * ["18.31.111.50", ...] - for MX record - priority will be removed<br>
     * ["v=spf1 mx ~all", "google-site-verification=6OB-29oecz30D"] - for TXT record - quotation will be removed
     **/
    public static List<String> resolveDnsRecord(String domain, final String type) {
        if (!DNS_RECORD_PREFIX.containsKey(domain))
            throw new IllegalArgumentException("Provided DNS Record type is not supported yet: " + type);

        String[] result = executeCommand(new String[]{HOST_COMMAND_LOCATION, "-t", type, domain});
        if (!result[0].equals("0"))
            throw new RuntimeException("Unknown exit code when resolving dns record (%s %s): %s".formatted(type, domain, result[0]));
        String prefix = DNS_RECORD_PREFIX.get(type).formatted(domain);
        return result[1].lines()
            .filter(l -> l.startsWith(prefix))
            .map(l -> {
                l = l.substring(prefix.length());
                if (type.equalsIgnoreCase("MX"))
                    return l.replaceAll("^\\d+\\s+", "");   // remove priority int
                else if (type.equalsIgnoreCase("TXT"))
                    return l.substring(1, l.length() - 1);  // remove quotations
                return l;
            })
            .toList();
    }

    /**
     * @apiNote return example, <br>
     * ["18.31.111.50", "mx"] - for TXT record like "v=spf1 18.31.111.50 mx ~all"
     **/
    public static List<String> resolveDnsSpfRecord(String domain) {
        List<String> records = resolveDnsRecord(domain, "TXT"); // v=spf1 mx ~all
        return records.stream()
            .filter(record -> record.startsWith(SPF_DATA_PREFIX))
            .map(record -> record.substring(SPF_DATA_PREFIX.length())
                .replaceAll("~([:\\w])*", "")   // ~all
                .replaceAll("\\s{2,}", " ")     // extra space
                .split(" "))
            .flatMap(Arrays::stream)
            .toList();
    }

}
