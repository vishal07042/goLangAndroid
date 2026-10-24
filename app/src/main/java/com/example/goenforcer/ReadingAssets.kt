package com.example.goenforcer

import android.content.Context

// ─── Reading Enforcement: bundled content ──────────────────────────────
// The Markdown cheat sheets in app/src/main/assets/reading/ are first-class
// library documents. Their text lives in the APK (not SharedPreferences), so
// the store only keeps their progress. Parsed text is cached in memory after
// the first read; the same deterministic chunker then applies to them exactly
// as it does to pasted text.
object ReadingAssets {

    private const val DIR = "reading"
    private const val PREFIX = "asset:"

    private val cache = mutableMapOf<String, List<ReadingDocument>>()

    fun isAssetId(id: String): Boolean = id.startsWith(PREFIX)

    fun documents(ctx: Context): List<ReadingDocument> {
        cache[ctx.packageName]?.let { return it }
        val names = runCatching { ctx.assets.list(DIR)?.toList() }.getOrNull()
            ?.filter { it.endsWith(".md", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        val docs = names.mapNotNull { name ->
            val raw = runCatching {
                ctx.assets.open("$DIR/$name").bufferedReader().use { it.readText() }
            }.getOrNull() ?: return@mapNotNull null
            ReadingDocument(
                id = PREFIX + name,
                title = prettyTitle(name),
                text = MarkdownText.toPlainText(
                    // Drop the redundant H1 title + promo blurb the docs lead
                    // with — the reading flow should start at the content.
                    MarkdownText.stripFrontMatter(raw)
                ),
                addedAt = 0L,
                builtIn = true
            )
        }
        cache[ctx.packageName] = docs
        return docs
    }

    fun document(ctx: Context, id: String): ReadingDocument? =
        documents(ctx).firstOrNull { it.id == id }

    /** "hld-url-shortener.md" → "HLD Url Shortener". */
    fun prettyTitle(fileName: String): String {
        val base = fileName.substringBeforeLast('.')
        return base.split('-', '_').filter { it.isNotBlank() }.joinToString(" ") { part ->
            SPECIAL[part.lowercase()] ?: part.replaceFirstChar { c ->
                if (c.isLowerCase()) c.titlecase() else c.toString()
            }
        }
    }

    private val SPECIAL = mapOf(
        "ai" to "AI",
        "hld" to "HLD",
        "ml" to "ML",
        "sql" to "SQL",
        "numpy" to "NumPy",
        "pandas" to "Pandas",
        "matplotlib" to "Matplotlib",
        "typescript" to "TypeScript",
        "javascript" to "JavaScript",
        "java" to "Java",
        "python" to "Python",
        "rust" to "Rust",
        "docker" to "Docker",
        "kubernetes" to "Kubernetes",
        "scikit" to "scikit",
        "url" to "URL",
        "cheat" to "Cheat",
        "sheet" to "Sheet"
    )
}
