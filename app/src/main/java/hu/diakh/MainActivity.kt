@file:OptIn(ExperimentalMaterial3Api::class)

package hu.diakh

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate

private val LIGHT = lightColorScheme(
    primary = Color(0xFFF57C00), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0B2), onPrimaryContainer = Color(0xFF3E2000),
    secondaryContainer = Color(0xFFFFE0B2), onSecondaryContainer = Color(0xFF3E2000),
    background = Color(0xFFFFFBF7), surface = Color(0xFFFFFBF7)
)
private val DARK = darkColorScheme(
    primary = Color(0xFFFFB74D), onPrimary = Color(0xFF3E2000),
    primaryContainer = Color(0xFF5D3A00), onPrimaryContainer = Color(0xFFFFE0B2),
    secondaryContainer = Color(0xFF5D3A00), onSecondaryContainer = Color(0xFFFFE0B2),
    background = Color(0xFF1C1612), surface = Color(0xFF1C1612)
)

/** Növelve frissíti a listákat minden képernyőn. */
private var rev by mutableIntStateOf(0)

class FormState(a: String = LocalDate.now().toString(), b: String = a, c: Boolean = true,
                h: Int = 1, i: Int = 7, r: String = "", o: String = "") {
    var d1 by mutableStateOf(a)
    var d2 by mutableStateOf(b)
    var all by mutableStateOf(c)
    var h1 by mutableStateOf(h)
    var h2 by mutableStateOf(i)
    var rep by mutableStateOf(r)
    var rea by mutableStateOf(o)
}

private fun Context.t(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
private fun Absence.span() = if (allDay) "egész nap" else "$h1–$h2. óra"
private fun Absence.dates() = if (from == to) from else "$from – $to"

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val db = Db(this)
        val pr = getSharedPreferences("p", 0)
        setContent {
            val sys = androidx.compose.foundation.isSystemInDarkTheme()
            var dark by remember { mutableStateOf(pr.getBoolean("dark", sys)) }
            MaterialTheme(colorScheme = if (dark) DARK else LIGHT) {
                var tab by remember { mutableIntStateOf(0) }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("DiákH", fontWeight = FontWeight.Bold) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                titleContentColor = MaterialTheme.colorScheme.onPrimary),
                            actions = {
                                TextButton(onClick = { dark = !dark; pr.edit().putBoolean("dark", dark).apply() }) {
                                    Text(if (dark) "☀️" else "🌙", fontSize = 22.sp)
                                }
                            })
                    },
                    bottomBar = {
                        NavigationBar {
                            listOf("➕" to "Új", "📋" to "Hiányzások", "⚙️" to "Beállítások").forEachIndexed { i, (ic, l) ->
                                NavigationBarItem(selected = tab == i, onClick = { tab = i },
                                    icon = { Text(ic, fontSize = 22.sp) }, label = { Text(l) })
                            }
                        }
                    }
                ) { p ->
                    Box(Modifier.padding(p).fillMaxSize()) {
                        when (tab) {
                            0 -> NewScreen(db)
                            1 -> AbsencesScreen(db)
                            else -> SettingsScreen(db, dark) { dark = it; pr.edit().putBoolean("dark", it).apply() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Dropdown(label: String, opts: List<String>, value: String, mod: Modifier = Modifier.fillMaxWidth(), onSel: (String) -> Unit) {
    var ex by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = ex, onExpandedChange = { ex = it }, modifier = mod) {
        OutlinedTextField(value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ex) },
            modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(expanded = ex, onDismissRequest = { ex = false }) {
            opts.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onSel(o); ex = false }) }
        }
    }
}

@Composable
fun DateField(label: String, v: String, mod: Modifier = Modifier, onSel: (String) -> Unit) {
    val c = LocalContext.current
    OutlinedButton(onClick = {
        val d = runCatching { LocalDate.parse(v) }.getOrDefault(LocalDate.now())
        DatePickerDialog(c, { _, y, m, dd -> onSel(LocalDate.of(y, m + 1, dd).toString()) },
            d.year, d.monthValue - 1, d.dayOfMonth).show()
    }, modifier = mod) { Text("$label: $v") }
}

@Composable
fun AbsenceFields(db: Db, f: FormState) {
    val reps = remember(rev) { db.names("reporters") }
    val reas = remember(rev) { db.names("reasons") }
    val hours = (1..7).map { it.toString() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField("Től", f.d1, Modifier.weight(1f)) { f.d1 = it; if (f.d2 < it) f.d2 = it }
            DateField("Ig", f.d2, Modifier.weight(1f)) { f.d2 = it; if (f.d1 > it) f.d1 = it }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = f.all, onCheckedChange = { f.all = it })
            Text("  Egész nap")
        }
        if (!f.all) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dropdown("Órától", hours, f.h1.toString(), Modifier.weight(1f)) { f.h1 = it.toInt() }
            Dropdown("Óráig", hours, f.h2.toString(), Modifier.weight(1f)) { f.h2 = it.toInt() }
        }
        Dropdown("Bejelentő", reps, f.rep) { f.rep = it }
        Dropdown("Hiányzás oka", reas, f.rea) { f.rea = it }
    }
}

private fun FormState.error(): String? = when {
    rep.isBlank() || rea.isBlank() -> "Add meg a bejelentőt és az okot"
    !all && h1 > h2 -> "Hibás óraköz"
    else -> null
}

@Composable
fun NewScreen(db: Db) {
    val ctx = LocalContext.current
    val students = remember(rev) { db.students() }
    var q by remember { mutableStateOf("") }
    val sel = remember { mutableStateListOf<Long>() }
    val f = remember { FormState() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(q, { q = it }, label = { Text("🔍 Diák keresése") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Kijelölve: ${sel.size} / ${students.size}", style = MaterialTheme.typography.labelMedium)
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().height(240.dp)) {
            if (students.isEmpty()) Text("Nincs diák. Importáld a Beállítások fülön.", Modifier.padding(16.dp))
            LazyColumn {
                items(students.filter { it.name.contains(q, true) }, key = { it.id }) { s ->
                    val toggle = { if (s.id in sel) sel.remove(s.id) else sel.add(s.id); Unit }
                    Row(Modifier.fillMaxWidth().clickable { toggle() }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = s.id in sel, onCheckedChange = { toggle() })
                        Column {
                            Text(s.name, fontWeight = FontWeight.Bold)
                            Text("${s.birth} • ${s.mother}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        AbsenceFields(db, f)
        Button(onClick = {
            val err = if (sel.isEmpty()) "Jelölj ki legalább egy diákot" else f.error()
            if (err != null) ctx.t(err) else {
                db.addAbsences(sel.toList(), f); sel.clear(); rev++; ctx.t("Rögzítve")
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("💾 Rögzítés") }
    }
}

@Composable
fun AbsencesScreen(db: Db) {
    val ctx = LocalContext.current
    val all = remember(rev) { db.absences() }
    var nq by remember { mutableStateOf("") }
    var dq by remember { mutableStateOf("") }
    var selId by remember { mutableStateOf<Long?>(null) }
    var edit by remember { mutableStateOf<Absence?>(null) }
    var del by remember { mutableIntStateOf(0) }
    val list = all.filter { it.name.contains(nq, true) && (dq == "" || (it.from <= dq && dq <= it.to)) }
    val sel = all.firstOrNull { it.id == selId }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(nq, { nq = it }, label = { Text("🔍 Diák neve") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateField("Dátum", if (dq == "") "—" else dq, Modifier.weight(1f)) { dq = it }
            TextButton(onClick = { dq = "" }) { Text("✕") }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list, key = { it.id }) { a ->
                val on = a.id == selId
                Card(Modifier.fillMaxWidth().clickable { selId = if (on) null else a.id },
                    colors = CardDefaults.cardColors(containerColor =
                        if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(a.name, fontWeight = FontWeight.Bold)
                        Text("${a.dates()} • ${a.span()}")
                        Text("${a.reporter} • ${a.reason}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Text("${list.size} találat", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { edit = sel }, enabled = sel != null, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp)) { Text("✏️ Módosít") }
            Button(onClick = { del = 1 }, enabled = sel != null, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp)) { Text("🗑️ Töröl") }
            Button(onClick = { del = 2 }, enabled = all.isNotEmpty(), modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp)) { Text("⚠️ Mind") }
        }
        OutlinedButton(onClick = { if (list.isEmpty()) ctx.t("Nincs megosztható adat") else share(ctx, list) },
            modifier = Modifier.fillMaxWidth()) { Text("📤 Megosztás Excelben (e-mail)") }
    }

    edit?.let { a ->
        val f = remember(a.id) { FormState(a.from, a.to, a.allDay, a.h1, a.h2, a.reporter, a.reason) }
        AlertDialog(onDismissRequest = { edit = null },
            title = { Text(a.name) },
            text = { Box(Modifier.verticalScroll(rememberScrollState())) { AbsenceFields(db, f) } },
            confirmButton = {
                TextButton(onClick = {
                    val err = f.error()
                    if (err != null) ctx.t(err) else { db.update(a.id, f); edit = null; rev++ }
                }) { Text("Mentés") }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Mégse") } })
    }
    if (del != 0) AlertDialog(onDismissRequest = { del = 0 },
        title = { Text(if (del == 2) "Minden rögzítés törlése?" else "Kijelölt hiányzás törlése?") },
        text = { Text("Ez a művelet nem vonható vissza.") },
        confirmButton = {
            TextButton(onClick = {
                db.delete(if (del == 2) null else selId); selId = null; del = 0; rev++
            }) { Text("Törlés") }
        },
        dismissButton = { TextButton(onClick = { del = 0 }) { Text("Mégse") } })
}

private fun share(ctx: Context, list: List<Absence>) {
    val rows = listOf(listOf("Név", "Születési dátum", "Anyja neve", "Mettől", "Meddig", "Időtartam", "Bejelentő", "Ok")) +
        list.map { listOf(it.name, it.birth, it.mother, it.from, it.to, it.span(), it.reporter, it.reason) }
    val f = File(ctx.cacheDir, "hianyzasok.xlsx")
    Xlsx.write(f, rows)
    val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        putExtra(Intent.EXTRA_STREAM, u)
        putExtra(Intent.EXTRA_SUBJECT, "DiákH – hiányzások")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, "Megosztás"))
}

@Composable
fun SettingsScreen(db: Db, dark: Boolean, onDark: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val count = remember(rev) { db.students().size }
    var rep by remember { mutableStateOf("") }
    var rea by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) try {
            val rows = ctx.contentResolver.openInputStream(u)!!.use { Xlsx.read(it) }
            val n = db.addStudents(rows); rev++
            ctx.t("Beolvasva: ${rows.size} sor, új diák: $n")
        } catch (e: Exception) { ctx.t("Az importálás nem sikerült (.xlsx kell)") }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("☀️  ", fontSize = 22.sp)
            Switch(checked = dark, onCheckedChange = onDark)
            Text("  🌙", fontSize = 22.sp)
        }
        HorizontalDivider()
        Text("👥 Diákok: $count", fontWeight = FontWeight.Bold)
        Text("Excel (.xlsx): A oszlop név, B születési dátum, C anyja neve.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("📥 Diákok importálása") }
        HorizontalDivider()
        OutlinedTextField(rep, { rep = it }, label = { Text("Új bejelentő") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { if (db.addName("reporters", rep)) { rep = ""; rev++; ctx.t("Felvéve") } }) { Text("➕ Bejelentő felvétele") }
        OutlinedTextField(rea, { rea = it }, label = { Text("Új hiányzási ok") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { if (db.addName("reasons", rea)) { rea = ""; rev++; ctx.t("Felvéve") } }) { Text("➕ Ok felvétele") }
    }
}
