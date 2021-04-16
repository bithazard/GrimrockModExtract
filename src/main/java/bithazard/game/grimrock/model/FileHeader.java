package bithazard.game.grimrock.model;

import java.nio.charset.StandardCharsets;

public class FileHeader {
    public static final int LENGTH = 4;
    private static final String LOG2_FILE_HEADER = "GRA2";
    private final byte[] bytes;
    private String headerAsString;

    public FileHeader(byte[] bytes) {
        if (bytes.length != LENGTH) {
            throw new InvalidByteSizeException("File header must be exactly " + LENGTH + " bytes. " + bytes.length + " bytes were passed.");
        }
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public boolean isValid() {
        return getHeaderAsString().equals(LOG2_FILE_HEADER);
    }

    @Override
    public String toString() {
        return getHeaderAsString();
    }

    private String getHeaderAsString() {
        if (headerAsString == null) {
            headerAsString = new String(bytes, 0, LENGTH, StandardCharsets.UTF_8);
        }
        return headerAsString;
    }
}
