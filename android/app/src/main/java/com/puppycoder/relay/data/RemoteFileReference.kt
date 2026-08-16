package com.puppycoder.relay.data

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

internal fun isRemoteFileReference(reference: String): Boolean {
    val value = reference.trim().removeSurrounding("<", ">")
    if (value.isBlank() || value.startsWith('#')) return false
    val scheme = SCHEME.find(value)?.groupValues?.get(1)?.lowercase()
    return scheme == null || scheme == "file"
}

internal fun resolveRemoteFileReference(
    workspace: String,
    reference: String,
    allowOutsideWorkspace: Boolean,
): String? {
    if (!isRemoteFileReference(reference)) return null
    var value = reference.trim().removeSurrounding("<", ">")
    value = value.substringBefore('#').substringBefore('?')
    if (value.startsWith("file://", ignoreCase = true)) value = value.substring("file://".length)
    value = runCatching {
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
    if (value.isBlank() || '\u0000' in value) return null

    val base = runCatching { Paths.get(workspace).normalize() }.getOrNull() ?: return null
    if (!base.isAbsolute) return null
    val resolved = runCatching {
        val candidate = Paths.get(value)
        (if (candidate.isAbsolute) candidate else base.resolve(candidate)).normalize()
    }.getOrNull() ?: return null
    if (!resolved.isAbsolute) return null
    if (!allowOutsideWorkspace && !resolved.startsWith(base)) return null
    return resolved.toString()
}

internal fun remoteFileReferenceFromActivity(title: String, detail: String): String? {
    if (!title.contains("image", ignoreCase = true) && !title.contains("view", ignoreCase = true)) return null
    return detail.lineSequence()
        .map(String::trim)
        .firstOrNull { line ->
            line.startsWith('/') || IMAGE_PATH.matches(line) || line.startsWith("file://")
        }
}

private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")
private val IMAGE_PATH = Regex("^[^\\s]+\\.(png|jpe?g|gif|webp|bmp|heic|heif|svg)$", RegexOption.IGNORE_CASE)
