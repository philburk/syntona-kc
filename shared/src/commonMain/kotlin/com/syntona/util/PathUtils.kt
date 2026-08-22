package com.syntona.util

/**
 * Returns true if [path] is an absolute file path on Unix, macOS, Windows, or URL/network scheme.
 */
fun isAbsolutePath(path: String): Boolean {
    if (path.isEmpty()) return false
    val normalized = path.replace('\\', '/')
    if (normalized.startsWith('/')) return true
    if (normalized.length >= 2 && normalized[0].isLetter() && normalized[1] == ':') return true
    if (path.startsWith("\\\\") || normalized.startsWith("//")) return true
    return false
}

/**
 * Returns the directory containing [path].
 * If [path] ends with a slash, returns the path without trailing slash.
 * If [path] contains a filename, returns its parent directory path.
 * If [path] has no directory components, returns empty string.
 */
fun getDirectoryOf(path: String): String {
    if (path.isEmpty()) return ""
    val normalized = path.replace('\\', '/')
    if (normalized.endsWith('/')) {
        return normalized.trimEnd('/')
    }
    val lastSlash = normalized.lastIndexOf('/')
    return if (lastSlash >= 0) normalized.substring(0, lastSlash) else ""
}

/**
 * If [targetPath] is at or below the directory of [basePath], returns a relative path
 * (normalized with forward slashes).
 *
 * If [targetPath] is outside, above, or on a different drive from [basePath], or if
 * [basePath] is null/empty, returns [targetPath] unchanged.
 */
fun relativizePath(targetPath: String, basePath: String?): String {
    if (basePath.isNullOrBlank() || targetPath.isBlank()) return targetPath
    if (!isAbsolutePath(targetPath)) return targetPath.replace('\\', '/')
    if (!isAbsolutePath(basePath)) return targetPath

    val normalizedTarget = targetPath.replace('\\', '/')
    val baseDir = getDirectoryOf(basePath).trimEnd('/')
    if (baseDir.isEmpty()) return targetPath

    val prefix = "$baseDir/"
    if (normalizedTarget.startsWith(prefix, ignoreCase = true)) {
        return normalizedTarget.substring(prefix.length)
    }
    if (normalizedTarget.equals(baseDir, ignoreCase = true)) {
        return "."
    }
    return targetPath
}

/**
 * Resolves [targetPath] relative to the directory of [basePath] if [targetPath] is relative.
 * If [targetPath] is already absolute, or [basePath] is null/empty, returns [targetPath].
 */
fun resolvePath(targetPath: String, basePath: String?): String {
    if (targetPath.isBlank() || isAbsolutePath(targetPath) || basePath.isNullOrBlank()) {
        return targetPath
    }
    val baseDir = getDirectoryOf(basePath).trimEnd('/')
    if (baseDir.isEmpty()) return targetPath
    val cleanTarget = targetPath.replace('\\', '/').trimStart('/')
    return "$baseDir/$cleanTarget"
}
