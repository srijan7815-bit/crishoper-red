package red.crishoper.agent.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtTime(ts: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

fun fmtDate(ts: Long): String = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ts))

fun fmtDuration(ms: Long): String = when {
    ms <= 0 -> ""
    ms < 1000 -> "${ms}ms"
    ms < 10_000 -> String.format(Locale.US, "%.1fs", ms / 1000.0)
    else -> "${ms / 1000}s"
}

fun timeAgo(ts: Long): String {
    val d = System.currentTimeMillis() - ts
    return when {
        d < 60_000 -> "now"
        d < 3_600_000 -> "${d / 60_000}m"
        d < 86_400_000 -> "${d / 3_600_000}h"
        d < 604_800_000 -> "${d / 86_400_000}d"
        else -> fmtDate(ts)
    }
}

fun truncate(s: String, n: Int): String = if (s.length <= n) s else s.take(n - 1) + "…"

/* ── tiny markdown renderer for the answer pane ─────────────
   Supports: headings, bold/italic/inline code, links, fenced code,
   bullet and numbered lists, tables, blockquotes. Styling is applied by
   the composable; this only classifies the blocks.
   ───────────────────────────────────────────────────────── */

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val ordered: Boolean, val index: Int, val text: String) : MdBlock
    data class Code(val lang: String, val body: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Table(val head: List<String>, val rows: List<List<String>>) : MdBlock
    data class Rule(val ignored: Boolean = true) : MdBlock
}

fun parseMarkdown(src: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = src.replace("\r\n", "\n").split("\n")
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.startsWith("```") -> {
                val lang = line.removePrefix("```").trim()
                val body = StringBuilder()
                i++
                while (i < lines.size && !lines[i].startsWith("```")) { body.appendLine(lines[i]); i++ }
                out += MdBlock.Code(lang, body.toString().trimEnd())
            }
            line.startsWith("|") && i + 1 < lines.size && lines[i + 1].contains("---") -> {
                val head = splitRow(line)
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].startsWith("|")) { rows += splitRow(lines[i]); i++ }
                out += MdBlock.Table(head, rows)
                continue
            }
            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length.coerceAtMost(4)
                out += MdBlock.Heading(level, line.drop(level).trim())
            }
            line.startsWith("> ") -> out += MdBlock.Quote(line.removePrefix("> ").trim())
            Regex("^\\s*[-*+]\\s+").containsMatchIn(line) -> out += MdBlock.Bullet(false, 0, line.replaceFirst(Regex("^\\s*[-*+]\\s+"), ""))
            Regex("^\\s*\\d+[.)]\\s+").containsMatchIn(line) -> {
                val num = Regex("^\\s*(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                out += MdBlock.Bullet(true, num, line.replaceFirst(Regex("^\\s*\\d+[.)]\\s+"), ""))
            }
            line.trim() == "---" || line.trim() == "***" -> out += MdBlock.Rule()
            line.isBlank() -> Unit
            else -> {
                val buf = StringBuilder(line)
                i++
                while (i < lines.size && lines[i].isNotBlank() && !lines[i].startsWith("#") &&
                    !lines[i].startsWith("```") && !lines[i].startsWith("|") &&
                    !Regex("^\\s*[-*+]\\s+").containsMatchIn(lines[i])) {
                    buf.append(' ').append(lines[i].trim()); i++
                }
                out += MdBlock.Paragraph(buf.toString())
                continue
            }
        }
        i++
    }
    return out
}

private fun splitRow(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }

/* Inline spans: **bold**, `code`, [text](url), *italic* */
sealed interface Inline {
    data class Plain(val text: String) : Inline
    data class Bold(val text: String) : Inline
    data class Code(val text: String) : Inline
    data class Link(val text: String, val url: String) : Inline
    data class Italic(val text: String) : Inline
}

fun parseInline(src: String): List<Inline> {
    val out = mutableListOf<Inline>()
    var i = 0
    val buf = StringBuilder()
    fun flush() { if (buf.isNotEmpty()) { out += Inline.Plain(buf.toString()); buf.clear() } }
    while (i < src.length) {
        when {
            src.startsWith("**", i) -> {
                val end = src.indexOf("**", i + 2)
                if (end > 0) { flush(); out += Inline.Bold(src.substring(i + 2, end)); i = end + 2; continue }
            }
            src[i] == '`' -> {
                val end = src.indexOf('`', i + 1)
                if (end > 0) { flush(); out += Inline.Code(src.substring(i + 1, end)); i = end + 1; continue }
            }
            src.startsWith("[", i) -> {
                val close = src.indexOf(']', i)
                val open = src.indexOf('(', close)
                val end = src.indexOf(')', open)
                if (close > 0 && open == close + 1 && end > open) {
                    flush(); out += Inline.Link(src.substring(i + 1, close), src.substring(open + 1, end)); i = end + 1; continue
                }
            }
        }
        buf.append(src[i]); i++
    }
    flush()
    return out
}
