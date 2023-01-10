Grimrock Mod Extract
====================
Small tool that is capable of extracting mod files (usually .dat file ending) for the game [Legend of Grimrock 2](http://www.grimrock.net) from Almost Human. The extracted files can then be opened with the Legend of Grimrock 2 Editor.

### Requirements
* Only Java (>= 11) is required to run the program

### Execution
Open a command prompt and execute:  
``java -jar GrimrockModExtract-1.1.jar <source file> <target directory>``

Make sure when you are on Windows and use quotes around the target directory, that you don't have a backslash before the closing quote. Windows will otherwise pass the (escaped) quotation mark as part of the parameter. Alternatively you can just use forward slashes instead.

### FAQ
#### How does this work?
In principle the format of a mod file is very simple. You can basically compare it to a Zip-file with one big exception: It contains no file or folder names. Finding them is actually the hardest part of the process. Instead of those names it contains hashes of the original file names. If you know how hash functions work (in this case the FNV-1a hash function), you know that there is usually no way to calculate the original value from the hash. What you can do (simplified) is grab all the strings you can find in the extracted Lua files, calculate the hashes from them and hope that you find a matching filename for every hash. This works surprisingly well, depending on the mod and is exactly what this tool does.

#### I'm getting error messages like 'Could not determine correct filename for...' - what can I do?
This is precisely the result from the process explained under "How does this work?". It simply means, that there were some files left (the filename being the hash value + ".tmp") for which no matching filename was found. Unfortunately you cannot do very much about that. In the best case it means that those files were just contained in the mod but were not used anywhere (this happens more often than you might think). You can try to open the mod in the editor and see if it will just work without those files. Often at least viewing the mod in the editor works but you might get errors when you try to launch the mod from there. As you can also just run the packed mod from the game itself, this should not be a problem. 

#### I'm getting error messages like 'Error parsing... Expected...' - what can I do?
This is actually more a warning and there might be something you can do about it (a little programming experience required). You usually get this warning together with the other error message, mentioned above. If you only get these warnings, you can ignore them.

When the mod files are extracted, the program parses all Lua files it can find and searches for specific tokens that are known to contain paths to resource files (or other Lua files). It will then create a huge lookup table of all these resource file paths it has found and the according hash values. After that it iterates over all extracted files again and checks if it can find them in the lookup table. If it finds them, great, it will move and rename the file. If not, it leaves the file alone and logs the error message above.

The Lua parsing process is quite primitive. I did not want to execute Lua code to get a resource path. So as soon as there is any program logic to determine it, the process aborts (for the current token) and logs this warning. The warning contains the filename, line number and column number as well as a snippet of the code in question. With this you can play a more advanced parser and determine the correct outcome yourself. For example, assuming the code in question is something like the following:

```
for i=1,4 do
    ...
    texture = "mod_assets/textures/example_0"..i..".tga",
    ...
end
```

The resulting resource paths would be (actually with a .dds file ending instead of .tga - see below):
```
mod_assets/textures/example_01.tga
mod_assets/textures/example_02.tga
mod_assets/textures/example_03.tga
mod_assets/textures/example_04.tga
```

You can then create a file named "resourceStrings.txt" in the same directory as the mod file and put those strings in there. The program will automatically pick up the file and add those paths to the lookup table. There are a few things to consider though:
- It only makes sense to add resource strings that start with "mod_assets". Only those will be resources that are actually included in the mod. Every mod can use the default resources that are part of Legend of Grimrock 2 (usually starting with "assets"). But those paths won't help here. Side node: In theory the directory structure of a mod does not have to start with "mod_assets" (this could be changed in the ".dungeon_editor" file). In practice though, this should always be the case.
- File endings of certain resources are automatically "adjusted" by the game. For example files with the file ending "tga" are basically always changed to "dds". The resources strings have to be written like this to the "resourceStrings.txt". File endings of fields like "model" (or the method call "setModel") and "emitterMesh" (or the method call "setEmitterMesh") are changed from "fbx" to "model". File endings of fields like "animation" and "animations" are changed from "fbx" to "animation". 

In general, the method described here should only be a last resort. Usually the program should be able to extract a mod so that it can at least be opened in the editor. 
