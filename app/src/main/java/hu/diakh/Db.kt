package hu.diakh

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.Collator
import java.util.Locale

data class Student(val id: Long, val name: String, val birth: String, val mother: String)
data class Absence(
    val id: Long, val sid: Long, val name: String, val birth: String, val mother: String,
    val from: String, val to: String, val allDay: Boolean, val h1: Int, val h2: Int,
    val reporter: String, val reason: String
)

class Db(c: Context) : SQLiteOpenHelper(c, "diakh.db", null, 1) {
    private val col = Collator.getInstance(Locale("hu"))

    override fun onCreate(d: SQLiteDatabase) {
        d.execSQL("CREATE TABLE students(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,birth TEXT,mother TEXT)")
        d.execSQL("CREATE TABLE reporters(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT UNIQUE)")
        d.execSQL("CREATE TABLE reasons(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT UNIQUE)")
        d.execSQL("CREATE TABLE absences(id INTEGER PRIMARY KEY AUTOINCREMENT,sid INTEGER,d1 TEXT,d2 TEXT,allday INTEGER,h1 INTEGER,h2 INTEGER,reporter TEXT,reason TEXT)")
        listOf("Szülő", "Diák", "Osztályfőnök").forEach { d.execSQL("INSERT INTO reporters(name) VALUES(?)", arrayOf(it)) }
        listOf("Betegség", "Orvosi vizsgálat", "Családi ok", "Igazolatlan").forEach { d.execSQL("INSERT INTO reasons(name) VALUES(?)", arrayOf(it)) }
    }

    override fun onUpgrade(d: SQLiteDatabase, o: Int, n: Int) {}

    fun students(): List<Student> {
        val l = mutableListOf<Student>()
        readableDatabase.rawQuery("SELECT id,name,birth,mother FROM students", null).use {
            while (it.moveToNext()) l.add(Student(it.getLong(0), it.getString(1), it.getString(2), it.getString(3)))
        }
        return l.sortedWith { a, b -> col.compare(a.name, b.name) }
    }

    fun addStudents(rows: List<Triple<String, String, String>>): Int {
        var n = 0
        val d = writableDatabase
        d.beginTransaction()
        try {
            for ((a, b, c) in rows) {
                val ex = d.rawQuery("SELECT 1 FROM students WHERE name=? AND birth=? AND mother=?", arrayOf(a, b, c)).use { it.count > 0 }
                if (!ex) { d.execSQL("INSERT INTO students(name,birth,mother) VALUES(?,?,?)", arrayOf(a, b, c)); n++ }
            }
            d.setTransactionSuccessful()
        } finally { d.endTransaction() }
        return n
    }

    fun names(t: String): List<String> {
        val l = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT name FROM $t", null).use { while (it.moveToNext()) l.add(it.getString(0)) }
        return l.sortedWith { a, b -> col.compare(a, b) }
    }

    fun addName(t: String, n: String): Boolean {
        if (n.isBlank()) return false
        writableDatabase.execSQL("INSERT OR IGNORE INTO $t(name) VALUES(?)", arrayOf(n.trim()))
        return true
    }

    fun absences(): List<Absence> {
        val l = mutableListOf<Absence>()
        readableDatabase.rawQuery(
            "SELECT a.id,a.sid,s.name,s.birth,s.mother,a.d1,a.d2,a.allday,a.h1,a.h2,a.reporter,a.reason " +
                "FROM absences a JOIN students s ON s.id=a.sid ORDER BY a.d1 DESC, s.name", null
        ).use {
            while (it.moveToNext()) l.add(
                Absence(it.getLong(0), it.getLong(1), it.getString(2), it.getString(3), it.getString(4),
                    it.getString(5), it.getString(6), it.getInt(7) == 1, it.getInt(8), it.getInt(9),
                    it.getString(10), it.getString(11))
            )
        }
        return l
    }

    fun addAbsences(ids: List<Long>, f: FormState) {
        val d = writableDatabase
        d.beginTransaction()
        try {
            ids.forEach {
                d.execSQL("INSERT INTO absences(sid,d1,d2,allday,h1,h2,reporter,reason) VALUES(?,?,?,?,?,?,?,?)",
                    arrayOf(it, f.d1, f.d2, if (f.all) 1 else 0, f.h1, f.h2, f.rep, f.rea))
            }
            d.setTransactionSuccessful()
        } finally { d.endTransaction() }
    }

    fun update(id: Long, f: FormState) {
        writableDatabase.execSQL("UPDATE absences SET d1=?,d2=?,allday=?,h1=?,h2=?,reporter=?,reason=? WHERE id=?",
            arrayOf(f.d1, f.d2, if (f.all) 1 else 0, f.h1, f.h2, f.rep, f.rea, id))
    }

    /** id == null: minden rögzítés törlése */
    fun delete(id: Long?) {
        if (id == null) writableDatabase.execSQL("DELETE FROM absences")
        else writableDatabase.execSQL("DELETE FROM absences WHERE id=?", arrayOf(id))
    }
}
