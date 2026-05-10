package com.xactsolutions.email.util;

public class Utils {

    public static boolean isEmpty(String str) {
        return str == null || str.isBlank();
    }


    /**
     * @return value of the field or null if not found.
     **/
    public static String getJsonFieldValue(String jsonObject, String fieldname) {
        jsonObject = jsonObject.trim();
        if (!jsonObject.startsWith("{") || !jsonObject.endsWith("}"))
            throw new RuntimeException("Given string is not a JSON Object!!");
        jsonObject = jsonObject.substring(1, jsonObject.length() - 1);       // remove starting { and ending } of object

        String[] fieldAndValues = jsonObject.split(",\\s*\"");
        // remove initial " from field name
        int firstQuoteIdx = fieldAndValues[0].indexOf("\"");
        if (firstQuoteIdx != -1)
            fieldAndValues[0] = fieldAndValues[0].substring(firstQuoteIdx + 1);

        fieldname = fieldname + "\"";
        for (String str : fieldAndValues) {
            if (str.startsWith(fieldname)) {
                int idx = str.indexOf(":");
                if (idx == -1) throw new RuntimeException("Invalid object format!!");
                String value = str.substring(idx + 1).trim();
                if (value.startsWith("\"") && value.endsWith("\""))
                    return value.substring(1, value.length() - 1);
                else if (value.equals("null"))
                    return null;
                return value;
            }
        }
        return null;
    }

}
