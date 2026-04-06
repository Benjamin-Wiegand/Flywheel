package io.benwiegand.projection.geargrinder.crypto;

import static io.benwiegand.projection.geargrinder.crypto.CryptoConstants.BEGIN_CERTIFICATE_MARKER;
import static io.benwiegand.projection.geargrinder.crypto.CryptoConstants.END_CERTIFICATE_MARKER;

import android.util.Base64;

import java.security.PrivateKey;
import java.security.cert.Certificate;

public record KeyWithChain(PrivateKey key, Certificate... certChain) {

    private String certChainDumpToString() {
        StringBuilder sb = new StringBuilder();
        for (Certificate cert : certChain()) {
            byte[] encoded;
            try {
                encoded = cert.getEncoded();
            } catch (Throwable t) {
                sb.append("Failed to dump certificate: ")
                        .append(t)
                        .append("\n");
                continue;
            }

            sb.append("\n")
                    .append(BEGIN_CERTIFICATE_MARKER)
                    .append("\n")
                    .append(Base64.encodeToString(encoded, 0))
                    .append("\n")
                    .append(END_CERTIFICATE_MARKER)
                    .append("\n");
        }

        return sb.toString();
    }

    public String certChainInfoToString() {
        StringBuilder sb = new StringBuilder();
        Certificate[] chain = certChain();
        for (int i = 0; i < chain.length; i++) {
            Certificate cert = chain[i];

            sb.append("Certificate ")
                    .append(i)
                    .append(":");

            String toString;
            try {
                toString = cert.toString();
            } catch (Throwable t) {
                sb.append(" (failed to get certificate info: ")
                        .append(t)
                        .append(")\n");
                continue;
            }

            sb.append("\n")
                    .append(toString)
                    .append("\n");
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return "KeyWithChain{" +
                "\n    key=" + key +
                ",\n    certChain=[\n" + certChainDumpToString() + "]" +
                '}';
    }
}
