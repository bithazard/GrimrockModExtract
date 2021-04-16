package bithazard.game.grimrock.model;

import bithazard.game.grimrock.utils.ByteUtils;

import java.nio.charset.StandardCharsets;

public class ModInfo {
    private final byte[] bytes;
    private boolean initialized;
    private String uuid;
    private String dungeonName;
    private String author;
    private String description;
    private String dungeonFolder;

    public ModInfo(byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public String getUuid() {
        initialize();
        return uuid;
    }

    public String getDungeonName() {
        initialize();
        return dungeonName;
    }

    public String getAuthor() {
        initialize();
        return author;
    }

    public String getDescription() {
        initialize();
        return description;
    }

    public String getDungeonFolder() {
        initialize();
        return dungeonFolder;
    }

    @Override
    public String toString() {
        return "Uuid: " + getUuid() + " DungeonName: " + getDungeonName() + " Author: " + getAuthor() + " Description: " + getDescription() + " DungeonFolder: "
                + getDungeonFolder();
    }

    private void initialize() {
        if (!initialized) {
            int offset = 0;
            long uuidLength = ByteUtils.readAsUnsigned32BitLittleEndian(bytes, offset);
            offset += 4;
            uuid = new String(bytes, offset, Math.toIntExact(uuidLength), StandardCharsets.UTF_8);
            offset += uuidLength;
            long dungeonNameLength = ByteUtils.readAsUnsigned32BitLittleEndian(bytes, offset);
            offset += 4;
            dungeonName = new String(bytes, offset, Math.toIntExact(dungeonNameLength), StandardCharsets.UTF_8);
            offset += dungeonNameLength;
            long authorLength = ByteUtils.readAsUnsigned32BitLittleEndian(bytes, offset);
            offset += 4;
            author = new String(bytes, offset, Math.toIntExact(authorLength), StandardCharsets.UTF_8);
            offset += authorLength;
            long descriptionLength = ByteUtils.readAsUnsigned32BitLittleEndian(bytes, offset);
            offset += 4;
            description = new String(bytes, offset, Math.toIntExact(descriptionLength), StandardCharsets.UTF_8);
            offset += descriptionLength;
            long dungeonFolderLength = ByteUtils.readAsUnsigned32BitLittleEndian(bytes, offset);
            offset += 4;
            dungeonFolder = new String(bytes, offset, Math.toIntExact(dungeonFolderLength), StandardCharsets.UTF_8);
            initialized = true;
        }
    }
}
