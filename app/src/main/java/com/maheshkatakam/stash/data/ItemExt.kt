package com.maheshkatakam.stash.data

/** The heading shown on cards: your title, else the link's title, else a file name. */
val ItemFull.displayTitle: String
    get() = item.title.ifBlank { item.linkTitle.orEmpty() }.ifBlank {
        if (item.kind == ItemKind.FILE) attachments.firstOrNull()?.displayName.orEmpty() else ""
    }

/** Photos and videos, in the order they were added. */
val ItemFull.visuals: List<Attachment>
    get() = attachments.filter { it.kind == AttachmentKind.IMAGE || it.kind == AttachmentKind.VIDEO }
        .sortedBy { it.position }

val ItemFull.audios: List<Attachment>
    get() = attachments.filter { it.kind == AttachmentKind.AUDIO }.sortedBy { it.position }

val ItemFull.docs: List<Attachment>
    get() = attachments.filter { it.kind == AttachmentKind.FILE }.sortedBy { it.position }

val ItemFull.isList: Boolean
    get() = entries.isNotEmpty() || item.kind == ItemKind.LIST

val ItemFull.isTrashed: Boolean get() = item.trashedAt != null

fun ItemFull.isEmptyContent(): Boolean =
    item.title.isBlank() && item.body.isBlank() && item.url == null &&
        attachments.isEmpty() && entries.all { it.text.isBlank() }

/** Plain text version of an item, used for sharing it out and for search. */
fun ItemFull.plainText(includeChecked: Boolean = true): String = buildString {
    val heading = displayTitle
    if (heading.isNotBlank()) appendLine(heading)
    if (item.body.isNotBlank()) {
        if (isNotEmpty()) appendLine()
        appendLine(item.body.trim())
    }
    val sorted = entries.sortedWith(compareBy<ChecklistEntry>({ it.checked }, { it.position }))
    if (sorted.isNotEmpty()) {
        if (isNotEmpty()) appendLine()
        for (e in sorted) {
            if (e.text.isBlank() || (!includeChecked && e.checked)) continue
            appendLine((if (e.checked) "☑ " else "☐ ") + e.text.trim())
        }
    }
    item.url?.let {
        if (isNotEmpty()) appendLine()
        appendLine(it)
    }
}.trim()

fun ItemFull.matchesQuery(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    val words = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val haystack = buildString {
        append(item.title).append('\n')
        append(item.body).append('\n')
        item.url?.let { append(it).append('\n') }
        item.source?.let { append(it).append('\n') }
        item.linkTitle?.let { append(it).append('\n') }
        item.linkDescription?.let { append(it).append('\n') }
        entries.forEach { append(it.text).append('\n') }
        tags.forEach { append('#').append(it.name).append('\n') }
        attachments.forEach { append(it.displayName).append('\n') }
    }
    return words.all { haystack.contains(it.removePrefix("#"), ignoreCase = true) }
}

fun normalizeTag(raw: String): String? {
    val t = raw.trim().trimStart('#').trim().lowercase().replace(Regex("\\s+"), " ")
    return t.takeIf { it.isNotEmpty() }?.take(40)
}
