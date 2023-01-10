package bithazard.game.grimrock;

import bithazard.game.grimrock.model.DirectoryEntry;
import bithazard.game.grimrock.model.EditorVersionInfo;
import bithazard.game.grimrock.model.FileHeader;
import bithazard.game.grimrock.model.ModInfo;
import bithazard.game.grimrock.model.ModInfoEntry;
import bithazard.game.grimrock.model.ModStructure;
import bithazard.game.grimrock.parse.ErrorCollector;
import bithazard.game.grimrock.parse.LuaResourceParser;
import bithazard.game.grimrock.utils.ByteUtils;
import bithazard.game.grimrock.utils.FileUtils;
import bithazard.game.grimrock.utils.LuaUtils;
import org.apache.commons.io.IOUtils;

import javax.script.ScriptException;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.InflaterInputStream;

public class ModExtractor {
    private static final String USER_RESOURCE_STRINGS_FILENAME = "resourceStrings.txt";

    public static void main(String[] args) throws IOException, ScriptException {
        if (args.length != 2) {
            System.err.println("Please pass a path to a mod file as first parameter and an output path as second parameter.");
            return;
        }

        String sourcePath = args[0];
        String targetPath = args[1];

        ModExtractor modExtractor = new ModExtractor();
        File modFilePath = new File(sourcePath);
        System.out.println("Reading mod file...");
        ModStructure modStructure = modExtractor.readModStructure(new FileInputStream(modFilePath));
        ModInfo modInfo = modStructure.getModInfo();
        String modName = FileUtils.removeInvalidChars(modInfo.getDungeonName());
        File targetPathWithModDir = new File(targetPath, modName);
        Files.createDirectories(targetPathWithModDir.toPath());

        File editorFile = new File(targetPathWithModDir, modName + ".dungeon_editor");
        BufferedWriter editorFileWriter = new BufferedWriter(new FileWriter(editorFile));
        modExtractor.writeEditorFile(modInfo, editorFileWriter);
        System.out.println("Extracting files...");
        List<File> extractedFiles = modExtractor.extractModFiles(modStructure.getDirectoryEntries(), new FileInputStream(modFilePath), targetPathWithModDir);

        System.out.println("Parsing extracted files...");
        ErrorCollector errorCollector = new ErrorCollector();
        Collection<String> resourceStrings = modExtractor.findResourceStrings(extractedFiles, errorCollector);
        resourceStrings.addAll(getMandatoryResourceStrings(modInfo.getDungeonFolder()));
        Collection<String> userProvidedResourceStrings = getUserProvidedResourceStrings(modFilePath.getParentFile());
        if (!userProvidedResourceStrings.isEmpty()) {
            System.out.println("Picked up " + userProvidedResourceStrings.size() + " resource strings from " + USER_RESOURCE_STRINGS_FILENAME);
            resourceStrings.addAll(userProvidedResourceStrings);
        }
        Map<String, String> resourceHashes = modExtractor.calculateResourceHashes(resourceStrings);
        errorCollector.updateFilenames(resourceHashes);
        errorCollector.getErrors().forEach(System.out::println);

        System.out.println("Moving and renaming extracted files...");
        modExtractor.moveFiles(extractedFiles, resourceHashes);
    }

    public ModStructure readModStructure(InputStream modFileInputStream) throws IOException {
        try (modFileInputStream) {
            byte[] headerBytes = modFileInputStream.readNBytes(FileHeader.LENGTH);
            FileHeader fileHeader = new FileHeader(headerBytes);
            if (!fileHeader.isValid()) {
                throw new InvalidFileException("Passed file is not a Legend of Grimrock 2 mod.");
            }
            byte[] editorVersionInfoBytes = modFileInputStream.readNBytes(EditorVersionInfo.LENGTH);
            EditorVersionInfo editorVersionInfo = new EditorVersionInfo(editorVersionInfoBytes);
            byte[] modInfoEntryBytes = modFileInputStream.readNBytes(ModInfoEntry.LENGTH);
            ModInfoEntry modInfoEntry = new ModInfoEntry(modInfoEntryBytes);
            List<DirectoryEntry> directoryEntries = new ArrayList<>();
            long directoryEnd = modInfoEntry.getPosition();
            int bytesReadSoFar = FileHeader.LENGTH + EditorVersionInfo.LENGTH + ModInfoEntry.LENGTH;
            for (int i = bytesReadSoFar; i < directoryEnd; i += DirectoryEntry.LENGTH) {
                byte[] directoryEntryBytes = modFileInputStream.readNBytes(DirectoryEntry.LENGTH);
                DirectoryEntry directoryEntry = new DirectoryEntry(directoryEntryBytes);
                directoryEntries.add(directoryEntry);
            }
            long modInfoLength = modInfoEntry.getCompressedSize();
            byte[] modInfoBytes = modFileInputStream.readNBytes((int)modInfoLength);
            ModInfo modInfo = new ModInfo(modInfoBytes);
            return new ModStructure(editorVersionInfo, directoryEntries, modInfo);
        }
    }

    private void writeEditorFile(ModInfo modInfo, BufferedWriter writer) throws IOException, ScriptException {
        try (writer) {
            writer.write("-- This file has been generated by Grimrock Mod Extractor from a mod with UUID " + modInfo.getUuid());
            writer.newLine();
            writer.newLine();
            writer.write("dungeonName \"" + modInfo.getDungeonName() + "\"");
            writer.newLine();
            writer.write("author \"" + modInfo.getAuthor() + "\"");
            writer.newLine();
            String descriptionEscaped = LuaUtils.escapeForLua(modInfo.getDescription());
            writer.write("description " + descriptionEscaped);
            writer.newLine();
            writer.write("dungeonFolder \"" + modInfo.getDungeonFolder() + "\"");
            writer.newLine();
        }
    }

    private List<File> extractModFiles(List<DirectoryEntry> directoryEntries, InputStream modFileInputStream, File targetPath) throws IOException {
        try (modFileInputStream) {
            List<File> extractedFiles = new ArrayList<>();
            Optional<DirectoryEntry> firstDirectoryEntry = directoryEntries.stream().findFirst();
            if (firstDirectoryEntry.isPresent()) {
                IOUtils.skipFully(modFileInputStream, firstDirectoryEntry.get().getPosition());
            }
            for (DirectoryEntry directoryEntry : directoryEntries) {
                byte[] compressedFileBytes = modFileInputStream.readNBytes((int)directoryEntry.getCompressedSize());
                String fnv1aHashHex = Long.toHexString(directoryEntry.getFnv1aHash());
                File targetFile = new File(targetPath, fnv1aHashHex + ".tmp");
                try (InputStream inflaterInputStream = new InflaterInputStream(new ByteArrayInputStream(compressedFileBytes));
                     OutputStream fileOutputStream = new FileOutputStream(targetFile)) {
                    inflaterInputStream.transferTo(fileOutputStream);
                }
                extractedFiles.add(targetFile);
            }
            return extractedFiles;
        }
    }

    private Collection<String> findResourceStrings(List<File> extractedFiles, ErrorCollector errorCollector) throws IOException {
        LuaResourceParser luaResourceParser = new LuaResourceParser();
        Collection<String> resourceStrings = new LinkedHashSet<>();
        for (File extractedFile : extractedFiles) {
            Optional<FileUtils.Filetype> filetype = FileUtils.determineFiletype(extractedFile);
            if (filetype.isPresent()) {
                continue;
            }
            ErrorCollector fileErrorCollector = new ErrorCollector(extractedFile.getName());
            Collection<String> fileResourceStrings = luaResourceParser.findResourceStrings(new FileInputStream(extractedFile), fileErrorCollector);
            errorCollector.addAllErrors(fileErrorCollector);
            resourceStrings.addAll(fileResourceStrings);
        }
        return resourceStrings;
    }

    private static Collection<String> getMandatoryResourceStrings(String dungeonFolder) {
        return List.of(dungeonFolder + "/dungeon.lua", dungeonFolder + "/init.lua");
    }

    private static Collection<String> getUserProvidedResourceStrings(File modFolder) throws IOException {
        File userResourceStringsFile = new File(modFolder, USER_RESOURCE_STRINGS_FILENAME);
        if (!userResourceStringsFile.exists()) {
            return Collections.emptyList();
        }
        return Files.readAllLines(userResourceStringsFile.toPath());
    }

    private Map<String, String> calculateResourceHashes(Collection<String> resourceStrings) {
        Map<String, String> resourceHashes = new LinkedHashMap<>();
        for (String resourceString : resourceStrings) {
            String resourceStringHash = ByteUtils.calculateFnv1aHash(resourceString);
            resourceHashes.put(resourceStringHash, resourceString);
        }
        return resourceHashes;
    }

    private void moveFiles(List<File> extractedFiles, Map<String, String> resourceHashes) {
        for (File extractedFile : extractedFiles) {
            String filename = FileUtils.getFilenameWithoutExtension(extractedFile);
            String correctFilename = resourceHashes.get(filename);
            if (correctFilename == null) {
                System.out.println("Could not determine correct filename for " + extractedFile);
                continue;
            }
            Path correctPath = Path.of(extractedFile.getParent(), correctFilename);
            try {
                Files.createDirectories(correctPath.getParent());
                Files.move(extractedFile.toPath(), correctPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.err.println("Move from " + extractedFile + " to " + correctPath + " was not successful.");
            }
        }
    }
}
