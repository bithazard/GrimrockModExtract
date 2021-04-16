package bithazard.game.grimrock.model;

import bithazard.game.grimrock.utils.ByteUtils;

public class EditorVersionInfo {
    public static final int LENGTH = 4;
    private final byte[] bytes;
    private Long versionNumber;

    public EditorVersionInfo(byte[] bytes) {
        if (bytes.length != LENGTH) {
            throw new InvalidByteSizeException("Editor version info must be exactly " + LENGTH + " bytes. " + bytes.length + " bytes were passed.");
        }
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public long getVersionNumber() {
        if (versionNumber == null) {
            versionNumber = ByteUtils.readAsUnsigned32BitLittleEndian(bytes, 0);
        }
        return versionNumber;
    }

    @Override
    public String toString() {
        return String.valueOf(getVersionNumber());
    }
}
