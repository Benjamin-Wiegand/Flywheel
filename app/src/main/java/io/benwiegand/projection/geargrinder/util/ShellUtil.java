package io.benwiegand.projection.geargrinder.util;

public class ShellUtil {

    public static String wrapSingleQuote(String s) {
        String escaped = s.replaceAll("'", "'\\''");
        return "'" + escaped + "'";
    }

}
