package bithazard.game.grimrock.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

public final class ByteUtils {
    private static final int FNV_1A_OFFSET_BASIS = 0x811c9dc5;
    private static final int FNV_1A_PRIME = 0x1000193;

    private ByteUtils() {
    }

    public static long[] convertToUnsigned32BitLittleEndian(byte[] bytes) {
        IntBuffer intBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        int[] signed32BitLittleEndian = new int[intBuffer.limit()];
        intBuffer.get(signed32BitLittleEndian);
        long[] unsigned32BitLittleEndian = new long[signed32BitLittleEndian.length];
        for (int i = 0; i < signed32BitLittleEndian.length; i++) {
            unsigned32BitLittleEndian[i] = Integer.toUnsignedLong(signed32BitLittleEndian[i]);
        }
        return unsigned32BitLittleEndian;
    }

    public static long readAsUnsigned32BitLittleEndian(byte[] bytes, int offset) {
        IntBuffer intBuffer = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        int signed32BitLittleEndian = intBuffer.get();
        return Integer.toUnsignedLong(signed32BitLittleEndian);
    }

    public static String calculateFnv1aHash(String stringToHash) {
        int fnv1aHash = calculateFnv1aHash(stringToHash.getBytes(StandardCharsets.UTF_8));
        return Integer.toHexString(fnv1aHash);
    }

    private static int calculateFnv1aHash(byte[] bytes) {
        int hash = FNV_1A_OFFSET_BASIS;
        for (int i = 0; i < bytes.length; i++) {
            int unsignedByte = bytes[i] & 0xff;
            hash ^= unsignedByte;
            hash *= FNV_1A_PRIME;
        }
        return hash;
    }
}
