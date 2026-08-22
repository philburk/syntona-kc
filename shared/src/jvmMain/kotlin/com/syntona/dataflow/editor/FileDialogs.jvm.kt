package com.syntona.dataflow.editor

import com.syntona.DEFAULT_PATCH_FILENAME
import com.syntona.SYNTONA_FILE_EXTENSION
import com.syntona.SYNTONA_FILE_SUFFIX
import com.syntona.storage.SettingsStore
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** Root of the bundled resource files in the source tree, when running from a dev checkout. */
private val resourceRootDirectory: File? = File(
    System.getProperty("user.home"),
    "work/syntona-kc/shared/src/commonMain/composeResources/files/"
).takeIf { it.exists() }

private var lastResourceDirectory: File? = resourceRootDirectory

/**
 * Extension for patches saved into composeResources. Resources are served over HTTP by the web
 * build, where a known type carries better than an invented one, and the bundled patches are
 * .json almost to a file.
 */
private const val RESOURCE_FILE_SUFFIX = ".json"

/** Extensions a saved patch may already carry, in which case nothing is appended. */
private val PATCH_SUFFIXES = listOf(RESOURCE_FILE_SUFFIX, SYNTONA_FILE_SUFFIX)

/**
 * Forces the resource extension onto a name or path.
 *
 * A patch under composeResources is always .json. A .sntn name is converted rather than
 * accepted: a static web server has no content type for .sntn, so such a file is served wrongly
 * or not at all, and the failure only shows up once the site is deployed. Choosing the extension
 * here is the one place that can prevent it.
 */
internal fun asResourcePath(path: String): String {
    val stem = PATCH_SUFFIXES.firstOrNull { path.endsWith(it, ignoreCase = true) }
        ?.let { path.dropLast(it.length) }
        ?: path
    return stem + RESOURCE_FILE_SUFFIX
}

/**
 * The name to offer when saving into composeResources.
 *
 * The name arrives from the open document, so it is usually `something.sntn`; resources want
 * `.json`, and offering the wrong one is how a folder of .json files ends up with a stray .sntn.
 */
internal fun defaultResourceFileName(documentName: String): String =
    asResourcePath(documentName.trim().ifEmpty { DEFAULT_PATCH_FILENAME })

/**
 * Adds [defaultSuffix] only when the name does not already end in a patch extension.
 *
 * Appending unconditionally is what turned a deliberately typed `xyz.json` into `xyz.json.sntn`:
 * a name the user has already given an extension to should be taken at their word.
 */
internal fun withPatchExtension(path: String, defaultSuffix: String): String =
    if (PATCH_SUFFIXES.any { path.endsWith(it, ignoreCase = true) }) path else path + defaultSuffix

/**
 * Turns a resource path such as `examples/wavey_square.sntn` into the directory it lives in,
 * so that saving a loaded example starts in the folder it came from rather than the resource
 * root. Returns null when the path is not a resource or the folder is not there.
 */
internal fun resolveResourceDirectory(root: File?, relativePath: String?): File? {
    if (root == null || relativePath.isNullOrEmpty()) return null
    return File(root, relativePath).parentFile?.takeIf { it.isDirectory }
}

private fun getInitialDocumentDirectory(): File {
    val savedPath = try {
        val docPath = SettingsStore.default.getString(SettingsStore.KEY_LAST_DOCUMENT_DIRECTORY)
        if (docPath.isNotEmpty()) docPath
        else SettingsStore.default.getString(SettingsStore.KEY_LAST_DIRECTORY)
    } catch (_: Exception) { "" }

    if (savedPath.isNotEmpty()) {
        val dir = File(savedPath)
        if (dir.exists() && dir.isDirectory) {
            return dir
        }
    }

    val docsDir = File(System.getProperty("user.home"), "Documents/Syntona")
    if (!docsDir.exists()) {
        docsDir.mkdirs()
    }
    return docsDir
}

private var lastDocumentDirectory: File = getInitialDocumentDirectory()

private fun updateLastDocumentDirectory(dir: File) {
    if (!dir.exists()) {
        dir.mkdirs()
    }
    lastDocumentDirectory = dir
    try {
        SettingsStore.default.edit {
            putString(SettingsStore.KEY_LAST_DOCUMENT_DIRECTORY, dir.absolutePath)
            putString(SettingsStore.KEY_LAST_DIRECTORY, dir.absolutePath)
        }
    } catch (_: Exception) {}
}

actual fun isSaveAsResourceAvailable(): Boolean {
    return lastResourceDirectory?.exists() == true
}

actual fun showOpenFileDialog(onResult: (path: String, content: String) -> Unit) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Open Patch"
        fileFilter = FileNameExtensionFilter("Syntona files (*$SYNTONA_FILE_SUFFIX)", SYNTONA_FILE_EXTENSION)
        if (!lastDocumentDirectory.exists()) lastDocumentDirectory.mkdirs()
        currentDirectory = lastDocumentDirectory
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        val file = chooser.selectedFile
        updateLastDocumentDirectory(file.parentFile)
        onResult(file.absolutePath, file.readText())
    }
}

actual fun showSaveFileDialog(content: String, defaultName: String, onSaved: (path: String) -> Unit) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Save Patch"
        fileFilter = FileNameExtensionFilter("Syntona files (*$SYNTONA_FILE_SUFFIX)", SYNTONA_FILE_EXTENSION)
        if (!lastDocumentDirectory.exists()) lastDocumentDirectory.mkdirs()
        currentDirectory = lastDocumentDirectory
        selectedFile = File(lastDocumentDirectory, defaultName)
    }
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        // Documents default to .sntn, but a name the user has already given a patch extension to
        // is kept as typed rather than doubled up.
        val file = File(withPatchExtension(chooser.selectedFile.path, SYNTONA_FILE_SUFFIX))
        updateLastDocumentDirectory(file.parentFile)
        file.writeText(content)
        onSaved(file.absolutePath)
    }
}

actual fun showSaveAsResourceFileDialog(content: String, defaultName: String, onSaved: (path: String) -> Unit) {
    // Prefer the folder the currently loaded resource came from, so that saving an example
    // opened from the Help menu starts in examples/ rather than the resource root. Once the
    // patch has been saved somewhere concrete it is no longer a resource, and the directory
    // last used for saving takes over again.
    // The resource-relative path, not the raw one: once a patch has been saved here its path is
    // absolute, and joining that to the root would resolve somewhere unintended.
    val loadedFrom = resolveResourceDirectory(resourceRootDirectory, FileState.resourcePath)
    val dir = loadedFrom ?: lastResourceDirectory ?: return
    val chooser = JFileChooser().apply {
        dialogTitle = "Save Patch as Resource"
        fileFilter = FileNameExtensionFilter(
            "Patch resources (*$RESOURCE_FILE_SUFFIX, *$SYNTONA_FILE_SUFFIX)",
            RESOURCE_FILE_SUFFIX.removePrefix("."),
            SYNTONA_FILE_EXTENSION
        )
        currentDirectory = dir
        selectedFile = File(dir, defaultResourceFileName(defaultName))
    }
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        // Forced, not merely defaulted: picking an existing .sntn from the file list fills the
        // name field with it, which is the easiest way to put one here by accident.
        val file = File(asResourcePath(chooser.selectedFile.path))
        lastResourceDirectory = file.parentFile
        file.writeText(content)
        onSaved(file.absolutePath)
    }
}

actual fun saveFileDirect(path: String, content: String) {
    val file = File(path)
    file.parentFile?.mkdirs()
    file.writeText(content)
    val parent = file.parentFile
    if (parent != null) {
        val resourceDir = lastResourceDirectory
        if (resourceDir != null && file.absolutePath.startsWith(resourceDir.absolutePath)) {
            lastResourceDirectory = parent
        } else {
            updateLastDocumentDirectory(parent)
        }
    }
}

actual fun saveResourceFileDirect(relativePath: String, content: String) {
    val root = resourceRootDirectory ?: lastResourceDirectory
    val file = if (root != null) {
        File(root, relativePath)
    } else {
        File(relativePath)
    }
    file.parentFile?.mkdirs()
    file.writeText(content)
}

actual fun chooseWavRecordFile(defaultName: String, onSelected: (path: String) -> Unit) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Record Audio"
        fileFilter = FileNameExtensionFilter("WAV files (*.wav)", "wav")
        if (!lastDocumentDirectory.exists()) lastDocumentDirectory.mkdirs()
        currentDirectory = lastDocumentDirectory
        selectedFile = File(lastDocumentDirectory, defaultName)
    }
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        var file = chooser.selectedFile
        if (!file.name.endsWith(".wav")) file = File(file.path + ".wav")
        updateLastDocumentDirectory(file.parentFile)

        if (file.exists()) {
            val response = javax.swing.JOptionPane.showConfirmDialog(
                null,
                "${file.name} already exists. Do you want to replace it?",
                "Confirm Save As",
                javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE
            )
            if (response != javax.swing.JOptionPane.YES_OPTION) {
                return
            }
        }

        onSelected(file.absolutePath)
    }
}

actual fun chooseWavOpenFile(onSelected: (path: String) -> Unit) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Open WAV File"
        fileFilter = FileNameExtensionFilter("WAV files (*.wav)", "wav", "WAV")
        if (!lastDocumentDirectory.exists()) lastDocumentDirectory.mkdirs()
        currentDirectory = lastDocumentDirectory
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        val file = chooser.selectedFile
        updateLastDocumentDirectory(file.parentFile)
        onSelected(file.absolutePath)
    }
}

actual fun readFileDirect(path: String): String? {
    return try {
        File(path).readText()
    } catch (e: Exception) {
        null
    }
}

actual fun readBinaryFileDirect(path: String): ByteArray? {
    val candidates = listOfNotNull(
        File(path),
        FileState.currentFilePath?.let { File(File(it).parentFile, path) },
        resourceRootDirectory?.let { File(it, path) },
        lastResourceDirectory?.let { File(it, path) },
        File("src/commonMain/composeResources/files/$path"),
        File("shared/src/commonMain/composeResources/files/$path"),
        File("../shared/src/commonMain/composeResources/files/$path")
    )
    for (file in candidates) {
        if (file.exists() && file.isFile) {
            return try { file.readBytes() } catch (_: Exception) { null }
        }
    }
    return null
}

actual fun readResourceFileDirect(relativePath: String): String? {
    val candidates = listOfNotNull(
        resourceRootDirectory?.let { File(it, relativePath) },
        lastResourceDirectory?.let { File(it, relativePath) },
        File("src/commonMain/composeResources/files/$relativePath"),
        File("shared/src/commonMain/composeResources/files/$relativePath"),
        File("../shared/src/commonMain/composeResources/files/$relativePath")
    )
    for (file in candidates) {
        if (file.exists() && file.isFile) {
            return try { file.readText() } catch (_: Exception) { null }
        }
    }
    val stem = relativePath.substringAfterLast('/').substringBeforeLast(".json").substringBeforeLast(SYNTONA_FILE_SUFFIX)
    return com.syntona.dataflow.registry.InstrumentRegistry.instrumentJsonCache[stem]
        ?: com.syntona.dataflow.registry.SoundRegistry.soundJsonCache[stem]
}
