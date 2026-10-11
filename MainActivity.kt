package com.smarthisabbook.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Bundle
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HisabDb(context: android.content.Context) : SQLiteOpenHelper(context, "hisab_book.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE people(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT, kind TEXT NOT NULL)")
        db.execSQL("CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, person_id INTEGER NOT NULL, amount REAL NOT NULL, entry_type TEXT NOT NULL, note TEXT, created_at TEXT NOT NULL, FOREIGN KEY(person_id) REFERENCES people(id))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun addPerson(name: String, phone: String, kind: String) {
        writableDatabase.insertOrThrow("people", null, ContentValues().apply { put("name", name); put("phone", phone); put("kind", kind) })
    }
    fun people(): List<Person> {
        val out = mutableListOf<Person>()
        readableDatabase.rawQuery("SELECT id,name,phone,kind FROM people ORDER BY name COLLATE NOCASE", null).use { c ->
            while (c.moveToNext()) out.add(Person(c.getLong(0), c.getString(1), c.getString(2) ?: "", c.getString(3)))
        }
        return out
    }
    fun addEntry(personId: Long, amount: Double, type: String, note: String) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        writableDatabase.insertOrThrow("entries", null, ContentValues().apply { put("person_id", personId); put("amount", amount); put("entry_type", type); put("note", note); put("created_at", stamp) })
    }
    fun totals(): Triple<Double, Double, Int> {
        var receive = 0.0; var pay = 0.0
        readableDatabase.rawQuery("SELECT entry_type, SUM(amount) FROM entries GROUP BY entry_type", null).use { c ->
            while (c.moveToNext()) { if (c.getString(0) == "GAVE") { receive = c.getDouble(1) } else { pay = c.getDouble(1) } }
        }
        val count = readableDatabase.rawQuery("SELECT COUNT(*) FROM people", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        return Triple(receive, pay, count)
    }
    fun peopleWithBalances(): List<String> {
        val out = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT p.name,p.kind,COALESCE(SUM(CASE WHEN e.entry_type='GAVE' THEN e.amount WHEN e.entry_type='RECEIVED' THEN -e.amount WHEN e.entry_type='PAYABLE' THEN e.amount WHEN e.entry_type='PAID' THEN -e.amount ELSE 0 END),0) balance FROM people p LEFT JOIN entries e ON e.person_id=p.id GROUP BY p.id ORDER BY p.name COLLATE NOCASE", null).use { c ->
            while (c.moveToNext()) out.add("${c.getString(0)} (${if (c.getString(1)=="CUSTOMER") "Customer" else "Supplier"})\nBalance: ₹${money(c.getDouble(2))}")
        }
        return out
    }
    fun entryLines(): List<String> {
        val out = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT e.created_at,p.name,e.entry_type,e.amount,e.note FROM entries e JOIN people p ON p.id=e.person_id ORDER BY e.id DESC LIMIT 300", null).use { c ->
            while (c.moveToNext()) out.add("${c.getString(0)} · ${c.getString(1)}\n${typeLabel(c.getString(2))}: ₹${money(c.getDouble(3))}${if ((c.getString(4) ?: "").isNotBlank()) "\n${c.getString(4)}" else ""}")
        }
        return out
    }
    fun csv(): String {
        val sb = StringBuilder("record_type,id,name,phone,kind,person_id,amount,entry_type,note,created_at\n")
        readableDatabase.rawQuery("SELECT id,name,phone,kind FROM people ORDER BY id", null).use { c ->
            while (c.moveToNext()) sb.append("PERSON,${c.getLong(0)},${csvCell(c.getString(1))},${csvCell(c.getString(2) ?: "")},${c.getString(3)},,,,,\n")
        }
        readableDatabase.rawQuery("SELECT e.id,p.name,e.person_id,e.amount,e.entry_type,e.note,e.created_at FROM entries e JOIN people p ON p.id=e.person_id ORDER BY e.id", null).use { c ->
            while (c.moveToNext()) sb.append("ENTRY,${c.getLong(0)},${csvCell(c.getString(1))},,,${c.getLong(2)},${c.getDouble(3)},${c.getString(4)},${csvCell(c.getString(5) ?: "")},${c.getString(6)}\n")
        }
        return sb.toString()
    }
    private fun csvCell(s: String) = "\"${s.replace("\"", "\"\"").replace("\n", " ")}\""
}

data class Person(val id: Long, val name: String, val phone: String, val kind: String)

class MainActivity : Activity() {
    private lateinit var db: HisabDb
    private val createBackup = 7001
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_main); db = HisabDb(this)
        findViewById<Button>(R.id.addPersonButton).setOnClickListener { addPersonDialog() }
        findViewById<Button>(R.id.addEntryButton).setOnClickListener { addEntryDialog() }
        findViewById<Button>(R.id.viewPeopleButton).setOnClickListener { showList("Customers and suppliers", db.peopleWithBalances()) }
        findViewById<Button>(R.id.viewEntriesButton).setOnClickListener { showList("Transaction history", db.entryLines()) }
        findViewById<Button>(R.id.backupButton).setOnClickListener { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv").putExtra(Intent.EXTRA_TITLE, "smart-hisab-backup.csv"), createBackup) }
        refreshSummary()
    }
    private fun addPersonDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 8, 40, 0) }
        val name = EditText(this).apply { hint = "Name (required)" }; val phone = EditText(this).apply { hint = "Phone (optional)"; inputType = 3 }
        val kind = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Customer", "Supplier")) }
        box.addView(name); box.addView(phone); box.addView(kind)
        AlertDialog.Builder(this).setTitle("Add person").setView(box).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create().also { dialog ->
            dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = name.text.toString().trim(); if (n.isBlank()) { name.error = "Name required"; return@setOnClickListener }
                db.addPerson(n, phone.text.toString().trim(), if (kind.selectedItemPosition == 0) "CUSTOMER" else "SUPPLIER"); dialog.dismiss(); refreshSummary(); toast("Saved")
            } }; dialog.show()
        }
    }
    private fun addEntryDialog() {
        val people = db.people()
        if (people.isEmpty()) { toast("Pehle customer ya supplier add karein"); return }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 8, 40, 0) }
        val person = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, people.map { "${it.name} (${if(it.kind=="CUSTOMER") "Customer" else "Supplier"})" }) }
        val type = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Udhaar diya / lena hai", "Payment received", "Supplier bill / dena hai", "Supplier ko payment diya")) }
        val amount = EditText(this).apply { hint = "Amount in ₹"; inputType = 8194 }
        val note = EditText(this).apply { hint = "Note (optional)" }
        box.addView(person); box.addView(type); box.addView(amount); box.addView(note)
        AlertDialog.Builder(this).setTitle("Add hisaab entry").setView(box).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create().also { dialog ->
            dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = amount.text.toString().trim().toDoubleOrNull()
                if (value == null || value <= 0.0) { amount.error = "Valid amount enter karein"; return@setOnClickListener }
                val selected = people[person.selectedItemPosition]
                val chosen = listOf("GAVE", "RECEIVED", "PAYABLE", "PAID")[type.selectedItemPosition]
                if ((selected.kind == "CUSTOMER" && chosen in listOf("PAYABLE", "PAID")) || (selected.kind == "SUPPLIER" && chosen in listOf("GAVE", "RECEIVED"))) { toast("Is person ke type ke hisaab se entry type choose karein"); return@setOnClickListener }
                db.addEntry(selected.id, value, chosen, note.text.toString().trim()); dialog.dismiss(); refreshSummary(); toast("Entry saved")
            } }; dialog.show()
        }
    }
    private fun showList(title: String, items: List<String>) {
        val text = if (items.isEmpty()) "Abhi koi record nahi hai." else items.joinToString("\n\n──────────────\n\n")
        AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("OK", null).show()
    }
    private fun refreshSummary() {
        val t = db.totals()
        findViewById<TextView>(R.id.summaryReceivable).text = "Customers se lena hai: ₹${money(t.first)}"
        findViewById<TextView>(R.id.summaryPayable).text = "Suppliers ko dena hai: ₹${money(t.second)}"
        findViewById<TextView>(R.id.summaryCount).text = "Customers / Suppliers: ${t.third}"
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode, resultCode, data); if (requestCode == createBackup && resultCode == RESULT_OK) { val uri: Uri = data?.data ?: return; try { contentResolver.openOutputStream(uri)?.use { it.write(db.csv().toByteArray(Charsets.UTF_8)) }; toast("Backup exported. File ko safe jagah rakhein.") } catch (e: Exception) { toast("Backup failed: ${e.message}") } } }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}

private fun money(v: Double) = String.format(Locale.US, "%.2f", v)
private fun typeLabel(type: String) = when(type) { "GAVE" -> "Udhaar diya / lena hai"; "RECEIVED" -> "Payment received"; "PAYABLE" -> "Supplier bill / dena hai"; "PAID" -> "Supplier ko payment diya"; else -> type }
