package com.syntona.dataflow.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.modules.PatchModule

/** Folder holding the bundled resources inside a source checkout. */
private const val RESOURCE_ROOT_MARKER = "composeResources/files/"

/** Top level folders of the bundled resource tree. */
private val RESOURCE_FOLDERS = listOf("examples/", "tutorials/", "help/", "instruments/", "sounds/")

/**
 * Where [path] sits relative to the resource root, or null when it is not a resource.
 *
 * Two shapes arrive here. A resource loaded from the app bundle is already relative, such as
 * `examples/foo.json`. One just saved through Save as Resource is an absolute path into the
 * checkout. Recognising only the first shape is what made a freshly saved resource behave like
 * an ordinary document: its title kept the extension, and its recorded path was rewritten to
 * .sntn while the file on disk was .json.
 */
internal fun resourceRelativePath(path: String?): String? {
    val normalized = path?.replace('\\', '/') ?: return null
    val marker = normalized.indexOf(RESOURCE_ROOT_MARKER)
    if (marker >= 0) return normalized.substring(marker + RESOURCE_ROOT_MARKER.length)
    return normalized.takeIf { candidate -> RESOURCE_FOLDERS.any { candidate.startsWith(it) } }
}

/**
 * One open document: a top level patch together with where it came from and whether it has
 * unsaved changes.
 *
 * Several of these can be open at once, so that loading an example or another patch does not have
 * to throw away work in progress. File actions apply to whichever document is frontmost, which
 * [FileState] tracks.
 */
class DocumentState(val rootPatch: PatchModule) {

    var isDirty by mutableStateOf(false)
        private set

    /** Full path on JVM, filename on WASM, or a resource path such as `examples/foo.sntn`. */
    var currentFilePath by mutableStateOf<String?>(null)
        private set

    val currentFileName: String?
        get() = currentFilePath?.let { p ->
            val name = p.substringAfterLast('/').substringAfterLast('\\').ifEmpty { p }
            val clean = name.removeSuffix(".json").removeSuffix(com.syntona.SYNTONA_FILE_SUFFIX)
            "$clean${com.syntona.SYNTONA_FILE_SUFFIX}"
        }

    /**
     * This document's path within the resource tree, such as `examples/foo.json`, or null when
     * it is an ordinary file. The form the app bundle and the URL both speak, whatever shape
     * [currentFilePath] happens to be in.
     */
    val resourcePath: String?
        get() = resourceRelativePath(currentFilePath)

    /** True for the examples, tutorials, and help files that ship with the app, which must not be overwritten. */
    val isResource: Boolean
        get() = resourcePath != null

    /**
     * Title for this document's window: a resource shows a clean name, an ordinary file keeps its
     * extension.
     *
     * Resources drop the extension because it is noise the user cannot act on - the app chooses
     * .json for them - and because a patch opened from the Help menu has always been titled this
     * way. A saved one showing `xyz.json` would look like a different kind of thing.
     */
    val displayName: String
        get() {
            val file = currentFilePath ?: return rootPatch.name
            val name = file.substringAfterLast('/').substringAfterLast('\\').ifEmpty { file }
            return if (isResource) {
                name.removeSuffix(".json").removeSuffix(com.syntona.SYNTONA_FILE_SUFFIX)
            } else {
                name
            }
        }

    fun markDirty() {
        isDirty = true
    }

    /**
     * Records where the file actually went.
     *
     * The path is stored as given. An earlier version rewrote a .json path to .sntn for anything
     * it did not recognise as a resource, which left this disagreeing with the disk: the title
     * showed the wrong extension, and the next plain Save wrote a second file beside the first.
     */
    fun markSaved(path: String) {
        currentFilePath = path
        isDirty = false
        rootPatch.filePath = path
    }

    fun markNew() {
        currentFilePath = null
        isDirty = false
        rootPatch.filePath = null
        rootPatch.name = "root"
    }

    /** Unsaved work that would actually be lost. A bundled resource can always be reloaded. */
    val hasUnsavedWork: Boolean
        get() = isDirty && !isResource
}
