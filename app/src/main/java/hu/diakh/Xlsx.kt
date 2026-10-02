package hu.diakh

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Minimális .xlsx olvasó/író, külső könyvtár nélkül. */
object Xlsx {
    fun read(input: InputStream): List<Triple<String, String, String>> {
        var shared = ByteArray(0)
        var sheet = ByteArray(0)
        ZipInputStream(input).use { z ->
            var e = z.nextEntry
            while (e != null) {
                when (e.name) {
                    "xl/sharedStrings.xml" -> shared = z.readBytes()
                    "xl/worksheets/sheet1.xml" -> sheet = z.readBytes()
                }
                e = z.nextEntry
            }
        }
        val ss = if (shared.isEmpty()) emptyList() else parseShared(shared)
        val out = mutableListOf<Triple<String, String, String>>()
        for (r in parseSheet(sheet, ss)) {
            val name = r.getOrElse(0) { "" }.trim()
            var birth = r.getOrElse(1) { "" }.trim()
            val mother = r.getOrElse(2) { "" }.trim()
            if (name.isEmpty() || birth.none { it.isDigit() }) continue // fejléc / üres sor
            if (Regex("^\\d{5}(\\.0)?$").matches(birth))
                birth = LocalDate.of(1899, 12, 30).plusDays(birth.toDouble().toLong()).toString()
            out.add(Triple(name, birth, mother))
        }
        return out
    }

    private fun parseShared(b: ByteArray): List<String> {
        val p = Xml.newPullParser(); p.setInput(ByteArrayInputStream(b), null)
        val out = mutableListOf<String>(); var sb = StringBuilder(); var inT = false
        var e = p.eventType
        while (e != XmlPullParser.END_DOCUMENT) {
            when (e) {
                XmlPullParser.START_TAG -> if (p.name == "si") sb = StringBuilder() else if (p.name == "t") inT = true
                XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                XmlPullParser.END_TAG -> if (p.name == "t") inT = false else if (p.name == "si") out.add(sb.toString())
            }
            e = p.next()
        }
        return out
    }

    private fun parseSheet(b: ByteArray, ss: List<String>): List<List<String>> {
        val p = Xml.newPullParser(); p.setInput(ByteArrayInputStream(b), null)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>(); var col = 0; var type = ""; var sb = StringBuilder(); var inV = false
        var e = p.eventType
        while (e != XmlPullParser.END_DOCUMENT) {
            when (e) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> row = mutableListOf()
                    "c" -> {
                        val r = p.getAttributeValue(null, "r") ?: "A1"
                        col = r.takeWhile { it.isLetter() }.fold(0) { a, ch -> a * 26 + (ch.uppercaseChar() - 'A' + 1) } - 1
                        type = p.getAttributeValue(null, "t") ?: ""
                        sb = StringBuilder()
                    }
                    "v", "t" -> inV = true
                }
                XmlPullParser.TEXT -> if (inV) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> inV = false
                    "c" -> {
                        var v = sb.toString()
                        if (type == "s") v = ss.getOrElse(v.toIntOrNull() ?: -1) { "" }
                        while (row.size <= col) row.add("")
                        row[col] = v
                    }
                    "row" -> rows.add(row)
                }
            }
            e = p.next()
        }
        return rows
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun write(f: File, rows: List<List<String>>) {
        ZipOutputStream(f.outputStream()).use { z ->
            fun put(n: String, s: String) { z.putNextEntry(ZipEntry(n)); z.write(s.toByteArray()); z.closeEntry() }
            val h = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            val rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
            put("[Content_Types].xml", """$h<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            put("_rels/.rels", """$h<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$rel/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", """$h<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="$rel"><sheets><sheet name="Hianyzasok" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", """$h<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$rel/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            val sb = StringBuilder("$h<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
            for (r in rows) {
                sb.append("<row>")
                for (c in r) sb.append("<c t=\"inlineStr\"><is><t>").append(esc(c)).append("</t></is></c>")
                sb.append("</row>")
            }
            sb.append("</sheetData></worksheet>")
            put("xl/worksheets/sheet1.xml", sb.toString())
        }
    }
}
