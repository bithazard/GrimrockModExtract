package bithazard.game.grimrock.model;

import bithazard.game.grimrock.utils.ByteUtils;

public class ModInfoEntry {
    public static final int LENGTH = 20;
    private final byte[] bytes;
    //ints are signed in Java so we have to use longs
    private long[] unsigned32BitLittleEndian;

    public ModInfoEntry(byte[] bytes) {
        if (bytes.length != LENGTH) {
            throw new InvalidByteSizeException("Preamble must be exactly " + LENGTH + " bytes. " + bytes.length + " bytes were passed.");
        }
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public long getFnv1aHash() {
        return getUnsigned32BitLittleEndian()[0];
    }

    public long getPosition() {
        return getUnsigned32BitLittleEndian()[1];
    }

    public long getUnknown1() {
        return getUnsigned32BitLittleEndian()[2];
    }

    public long getCompressedSize() {
        return getUnsigned32BitLittleEndian()[3];
    }

    public long getUnknown2() {
        return getUnsigned32BitLittleEndian()[4];
    }

    @Override
    public String toString() {
        return "Fnv1aHash: " + getFnv1aHash() + " Position: " + getPosition() + " Unknown1: " + getUnknown1() + " CompressedSize: "
                + getCompressedSize() + " Unknown2: " + getUnknown2();
    }

    private long[] getUnsigned32BitLittleEndian() {
        if (unsigned32BitLittleEndian == null) {
            unsigned32BitLittleEndian = ByteUtils.convertToUnsigned32BitLittleEndian(bytes);
        }
        return unsigned32BitLittleEndian;
    }
}
