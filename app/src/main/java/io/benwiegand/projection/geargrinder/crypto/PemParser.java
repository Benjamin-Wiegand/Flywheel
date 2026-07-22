package io.benwiegand.projection.geargrinder.crypto;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.benwiegand.projection.geargrinder.exception.PemParseException;

public class PemParser {

    public static final String PEM_TAG_PRIVATE_KEY = "PRIVATE KEY";
    public static final String PEM_TAG_RSA_PRIVATE_KEY = "RSA PRIVATE KEY";
    public static final String PEM_TAG_CERTIFICATE = "CERTIFICATE";
    public static final String PEM_TAG_X509_CERTIFICATE = "X509 CERTIFICATE";
    public static final String PEM_TAG_X_509_CERTIFICATE = "X.509 CERTIFICATE";

    private static final Pattern BEGIN_TAG_PATTERN = Pattern.compile("-----BEGIN ([^-]+)-----");
    private static final Pattern END_TAG_PATTERN = Pattern.compile("-----END ([^-]+)-----");

    private final BufferedReader reader;
    private final Map<String, List<byte[]>> blockMap = new HashMap<>();

    private String currentTag = null;
    private String currentBlock = "";

    public PemParser(InputStream is) {
        reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
    }

    private void parseBegin(Matcher matcher) throws PemParseException {
        if (currentTag != null)
            throw new PemParseException("encountered block BEGIN before END of current block");

        String tag = matcher.group(1);
        assert tag != null;
        tag = tag.toUpperCase(Locale.ENGLISH);

        currentTag = tag;
        currentBlock = "";
    }

    private void parseEnd(Matcher matcher) throws PemParseException {
        if (currentTag == null)
            throw new PemParseException("encountered block END before BEGIN");

        String tag = matcher.group(1);
        assert tag != null;
        tag = tag.toUpperCase(Locale.ENGLISH);

        if (!currentTag.equals(tag))
            throw new PemParseException("block type mismatch in BEGIN/END");

        List<byte[]> blocks = blockMap.getOrDefault(currentTag, new ArrayList<>());
        assert blocks != null;
        blocks.add(Base64.decode(currentBlock, Base64.DEFAULT));
        blockMap.put(currentTag, blocks);

        currentTag = null;
    }

    private boolean parseLine() throws IOException, PemParseException {
        try {
            String line = reader.readLine();
            if (line == null) return false;

            Matcher beginMatcher = BEGIN_TAG_PATTERN.matcher(line);
            if (beginMatcher.matches()) {
                parseBegin(beginMatcher);
                return true;
            }

            Matcher endMatcher = END_TAG_PATTERN.matcher(line);
            if (endMatcher.matches()) {
                parseEnd(endMatcher);
                return true;
            }

            if (currentTag != null) currentBlock += line;
            return true;
        } catch (IOException | PemParseException e) {
            throw e;
        } catch (Throwable t) {
            throw new PemParseException("unexpected parsing error", t);
        }
    }

    public void parse() throws PemParseException, IOException {
        while (parseLine()) {}
    }

    public List<byte[]> getBlocks(String tag) {
        List<byte[]> blocks = blockMap.get(tag.toUpperCase(Locale.ENGLISH));
        if (blocks == null) return List.of();
        return List.copyOf(blocks);
    }
}
