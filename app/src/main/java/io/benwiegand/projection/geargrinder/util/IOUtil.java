package io.benwiegand.projection.geargrinder.util;

import java.io.IOException;
import java.io.InputStream;

public class IOUtil {

    public static void readAll(InputStream is, byte[] buffer, int offset, int totalLength) throws IOException {
        int len = 0, ret;
        while (len < totalLength) {
            ret = is.read(buffer, offset + len, totalLength - len);
            if (ret < 0) throw new IOException("stream closed (" + ret + ")");
            len += ret;
        }
    }
}
