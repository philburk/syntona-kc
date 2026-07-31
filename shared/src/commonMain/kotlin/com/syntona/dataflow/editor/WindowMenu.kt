package com.syntona.dataflow.editor

import com.syntona.dataflow.modules.PatchModule

/** One line in the Window menu. */
class WindowMenuEntry(
    val window: MdiWindowInfo,
    val label: String,
    /** True for a top level document, false for a patch window nested inside one. */
    val isDocument: Boolean,
    /** True for the window currently in front. */
    val isActive: Boolean,
    val isMinimized: Boolean
) {
    /**
     * The line as it appears in the menu.
     *
     * The front window marker sits to the left of the indent, and takes the same width whether or
     * not it is showing, so every name at a given level starts in the same column and the marker
     * never shifts anything sideways.
     */
    val menuText: String
        get() = buildString {
            append(if (isActive) "$ACTIVE_MARKER " else "  ")
            if (!isDocument) append(INDENT)
            append(label)
            if (isMinimized) append("  (minimized)")
        }

    companion object {
        private const val ACTIVE_MARKER = "*"
        private const val INDENT = "    "
    }
}

/**
 * Builds the contents of the Windows menu.
 *
 * Entries are grouped by document, with each document's patch windows listed under it. That
 * grouping does most of the disambiguating on its own: two patches called "osc" in different
 * documents are already told apart by which document they sit under.
 *
 * Documents themselves are named by file, so only they can really collide. Those are resolved the
 * way editors usually do it, by showing just enough of the path to tell them apart - so two files
 * called `glow_flash.json` become `examples/glow_flash.json` and `tutorials/glow_flash.json` -
 * falling back to a numbered suffix for documents with no path at all, such as two unsaved
 * patches both called "root".
 */
object WindowMenu {

    fun build(mdiState: MdiState): List<WindowMenuEntry> {
        val frontmost = mdiState.windows.lastOrNull { !it.minimized }
        val entries = mutableListOf<WindowMenuEntry>()
        val documents = FileState.documents.toList()
        val labels = documentLabels(documents)
        val claimed = mutableSetOf<MdiWindowInfo>()

        documents.forEachIndexed { index, document ->
            val documentWindow = mdiState.windows.firstOrNull { it.id === document.rootPatch }
            if (documentWindow != null) {
                claimed.add(documentWindow)
                entries.add(
                    WindowMenuEntry(
                        window = documentWindow,
                        label = labels[index] + if (document.hasUnsavedWork) "*" else "",
                        isDocument = true,
                        isActive = documentWindow === frontmost,
                        isMinimized = documentWindow.minimized
                    )
                )
            }
            // Patch windows opened from inside this document.
            val children = mdiState.windows.filter { window ->
                window !== documentWindow &&
                    FileState.documentFor(window.graphState) === document
            }
            val childLabels = uniqueLabels(children.map { labelFor(it) })
            children.forEachIndexed { childIndex, window ->
                claimed.add(window)
                entries.add(
                    WindowMenuEntry(
                        window = window,
                        label = childLabels[childIndex],
                        isDocument = false,
                        isActive = window === frontmost,
                        isMinimized = window.minimized
                    )
                )
            }
        }

        // Anything not owned by a document, such as a standalone editor window.
        val orphans = mdiState.windows.filter { it !in claimed }
        val orphanLabels = uniqueLabels(orphans.map { labelFor(it) })
        orphans.forEachIndexed { index, window ->
            entries.add(
                WindowMenuEntry(
                    window = window,
                    label = orphanLabels[index],
                    isDocument = false,
                    isActive = window === frontmost,
                    isMinimized = window.minimized
                )
            )
        }
        return entries
    }

    /** Brings [window] to the front, restoring it first if it has been minimized. */
    fun show(mdiState: MdiState, window: MdiWindowInfo) {
        if (window.minimized) window.minimized = false
        mdiState.bringToFront(window)
    }

    private fun labelFor(window: MdiWindowInfo): String {
        val patch = window.id as? PatchModule ?: return window.title
        return patch.name + if (patch.isModified) "*" else ""
    }

    /**
     * Names each document, widening to more of its path only where that is needed to tell two
     * apart, so the common case stays as short as `glow_flash.json`.
     */
    internal fun documentLabels(documents: List<DocumentState>): List<String> {
        val labels = MutableList(documents.size) { documents[it].displayName }

        // Widen colliding names one path segment at a time until they separate.
        for (depth in 2..MAX_PATH_SEGMENTS) {
            val collisions = labels.withIndex()
                .groupBy { it.value }
                .filterValues { it.size > 1 }
            if (collisions.isEmpty()) break
            var widened = false
            for ((_, group) in collisions) {
                for ((index, _) in group) {
                    val path = documents[index].currentFilePath ?: continue
                    val widenedLabel = lastSegments(path, depth)
                    if (widenedLabel != labels[index]) {
                        labels[index] = widenedLabel
                        widened = true
                    }
                }
            }
            if (!widened) break
        }
        // Whatever is still identical - typically unsaved patches with no path - gets numbered.
        return uniqueLabels(labels)
    }

    /** Appends " (2)", " (3)" and so on to repeats, leaving the first of each name alone. */
    private fun uniqueLabels(labels: List<String>): List<String> {
        val counts = mutableMapOf<String, Int>()
        return labels.map { label ->
            val seen = (counts[label] ?: 0) + 1
            counts[label] = seen
            if (seen == 1) label else "$label ($seen)"
        }
    }

    /** The last [count] path segments of [path], e.g. `examples/glow_flash.json`. */
    private fun lastSegments(path: String, count: Int): String {
        val segments = path.split('/', '\\').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return path
        return segments.takeLast(count).joinToString("/")
    }

    private const val MAX_PATH_SEGMENTS = 6
}
