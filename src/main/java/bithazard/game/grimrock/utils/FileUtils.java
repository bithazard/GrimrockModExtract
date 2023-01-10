package bithazard.game.grimrock.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.Optional;

public final class FileUtils {
    private FileUtils() {
    }

    public enum Filetype {
        ANIMATION("ANIM"),
        MODEL("MDL1"),
        SOUND("RIFF"),
        OGG_VORBIS("OggS"),
        TEXTURE("DDS "),
        CINEMATIC("DKIF");

        private final String magicNumber;

        Filetype(String magicNumber) {
            this.magicNumber = magicNumber;
        }

        public String getMagicNumber() {
            return magicNumber;
        }
    }

    public static Optional<Filetype> determineFiletype(File file) throws IOException {
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            char[] buffer = new char[4];
            int charsRead = reader.read(buffer, 0, 4);
            if (charsRead != 4) {
                return Optional.empty();
            }
            String potentialMagicNumber = new String(buffer);
            for (Filetype filetype : Filetype.values()) {
                if (filetype.getMagicNumber().equals(potentialMagicNumber)) {
                    return Optional.of(filetype);
                }
            }
            return Optional.empty();
        }
    }

    public static String getFilenameWithoutExtension(File file) {
        return getFilenameWithoutExtension(file.getName());
    }

    public static String getFilenameWithoutExtension(String filename) {
        int lastDotPosition = filename.lastIndexOf(".");
        return filename.substring(0, lastDotPosition);
    }

    public static String removeInvalidChars(final String fileName) {
        try {
            Paths.get(fileName);
            return fileName;
        } catch (final InvalidPathException e) {
            if (e.getInput() != null && !e.getInput().isEmpty() && e.getIndex() >= 0) {
                StringBuilder stringBuilder = new StringBuilder(e.getInput());
                stringBuilder.deleteCharAt(e.getIndex());
                return removeInvalidChars(stringBuilder.toString());
            }
            throw e;
        }
    }
}
