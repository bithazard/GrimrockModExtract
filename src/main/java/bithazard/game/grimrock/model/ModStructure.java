package bithazard.game.grimrock.model;

import java.util.List;

public class ModStructure {
    private final EditorVersionInfo editorVersionInfo;
    private final List<DirectoryEntry> directoryEntries;
    private final ModInfo modInfo;

    public ModStructure(EditorVersionInfo editorVersionInfo, List<DirectoryEntry> directoryEntries, ModInfo modInfo) {
        this.editorVersionInfo = editorVersionInfo;
        this.directoryEntries = directoryEntries;
        this.modInfo = modInfo;
    }

    public EditorVersionInfo getEditorVersionInfo() {
        return editorVersionInfo;
    }

    public List<DirectoryEntry> getDirectoryEntries() {
        return directoryEntries;
    }

    public ModInfo getModInfo() {
        return modInfo;
    }
}
