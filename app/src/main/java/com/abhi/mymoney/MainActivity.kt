package com.abhi.mymoney

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private lateinit var balance: TextView
    private lateinit var income: TextView
    private lateinit var expense: TextView
    private lateinit var list: LinearLayout

    private var pendingBytes: ByteArray? = null

    companion object {
        private const val REQ_PDF = 99
        private const val REQ_CSV = 98
        private const val REQ_BACKUP = 97
        private const val REQ_RESTORE = 96
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)

        balance = findViewById(R.id.balance)
        income = findViewById(R.id.income)
        expense = findViewById(R.id.expense)
        list = findViewById(R.id.list)

        findViewById<Button>(R.id.setBalance).setOnClickListener { setStart() }
        findViewById<Button>(R.id.addBtn).setOnClickListener { addTx() }
        findViewById<Button>(R.id.friendsBtn).setOnClickListener {
            startActivity(Intent(this, FriendsActivity::class.java))
        }
        findViewById<Button>(R.id.historyBtn).setOnClickListener { history() }
        findViewById<Button>(R.id.export).setOnClickListener { showExportBackupMenu() }

        findViewById<Button>(R.id.bottomAddBtn).setOnClickListener { addTx() }
        findViewById<Button>(R.id.bottomFriendsBtn).setOnClickListener {
            startActivity(Intent(this, FriendsActivity::class.java))
        }
        findViewById<Button>(R.id.bottomHistoryBtn).setOnClickListener { history() }
        findViewById<TextView>(R.id.viewAll).setOnClickListener { history() }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) refresh()
    }

    fun money(v: Double): String = "₹" + String.format(Locale.US, "%.2f", v)

    fun refresh() {
        val t = MoneyStore.tx(this)
        val inc = t.filter { it.type == "Income" }.sumOf { it.amount }
        val exp = t.filter { it.type == "Expense" }.sumOf { it.amount }
        income.text = "Income\n${money(inc)}"
        expense.text = "Expense\n${money(exp)}"
        balance.text = money(MoneyStore.balance(this))

        list.removeAllViews()
        if (t.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No transactions yet. Tap Add Transaction."
            empty.setPadding(16, 24, 16, 24)
            list.addView(empty)
        } else {
            t.takeLast(8).reversed().forEach { addRow(it) }
        }
    }

    private fun addRow(t: Tx) {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.setPadding(12, 14, 12, 14)

        val x = TextView(this)
        x.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        val noteStr = if (t.note.isNotBlank()) "\n${t.note}" else ""
        x.text = "${t.date} • ${t.category} (${t.method})\n${t.type}: ${money(t.amount)}$noteStr"

        val e = Button(this)
        e.text = "Edit"
        e.setOnClickListener { editTx(t) }

        r.addView(x)
        r.addView(e)
        list.addView(r)
    }

    private fun setStart() {
        val e = EditText(this)
        e.inputType = 2 or 8192 // numberDecimal
        e.setText(MoneyStore.start(this).toString())
        AlertDialog.Builder(this)
            .setTitle("Starting Balance")
            .setView(e)
            .setPositiveButton("Save") { _, _ ->
                e.text.toString().toDoubleOrNull()?.let {
                    if (it >= 0) {
                        MoneyStore.setStart(this, it)
                        refresh()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun addTx() {
        editTx(null)
    }

    fun editTx(old: Tx?) {
        val v = layoutInflater.inflate(R.layout.dialog_transaction, null)
        val type = v.findViewById<Spinner>(R.id.type)
        val date = v.findViewById<EditText>(R.id.date)
        val amt = v.findViewById<EditText>(R.id.amount)
        val cat = v.findViewById<AutoCompleteTextView>(R.id.category)
        val met = v.findViewById<AutoCompleteTextView>(R.id.method)
        val note = v.findViewById<EditText>(R.id.note)

        type.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("Expense", "Income"))

        // Category dropdown
        cat.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, MoneyStore.CATEGORIES))
        cat.setOnClickListener { cat.showDropDown() }

        // Method dropdown
        met.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, MoneyStore.PAYMENT_METHODS))
        met.setOnClickListener { met.showDropDown() }

        // Date Picker
        date.setOnClickListener {
            val cal = Calendar.getInstance()
            try {
                val cur = SimpleDateFormat("dd-MM-yyyy", Locale.US).parse(date.text.toString())
                if (cur != null) cal.time = cur
            } catch (_: Exception) {}
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    date.setText(String.format(Locale.US, "%02d-%02d-%04d", d, m + 1, y))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        if (old != null) {
            type.setSelection(if (old.type == "Income") 1 else 0)
            date.setText(old.date)
            amt.setText(old.amount.toString())
            cat.setText(old.category)
            met.setText(old.method)
            note.setText(old.note)
        } else {
            date.setText(MoneyStore.today())
            cat.setText("Food & Dining")
            met.setText("Cash")
        }

        val b = AlertDialog.Builder(this)
            .setTitle(if (old == null) "Add Transaction" else "Edit Transaction")
            .setView(v)
            .setPositiveButton("Save") { _, _ ->
                val a = amt.text.toString().toDoubleOrNull()
                if (a != null && a > 0) {
                    val l = MoneyStore.tx(this)
                    val n = Tx(
                        old?.id ?: MoneyStore.nextId(this),
                        date.text.toString().ifBlank { MoneyStore.today() },
                        type.selectedItem.toString(),
                        a,
                        cat.text.toString().ifBlank { "Other" },
                        met.text.toString().ifBlank { "Cash" },
                        note.text.toString().trim()
                    )
                    if (old == null) {
                        l.add(n)
                    } else {
                        val idx = l.indexOfFirst { it.id == old.id }
                        if (idx >= 0) l[idx] = n else l.add(n)
                    }
                    MoneyStore.saveTx(this, l)
                    refresh()
                }
            }
            .setNegativeButton("Cancel", null)

        if (old != null) {
            b.setNeutralButton("Delete") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Delete transaction?")
                    .setPositiveButton("Delete") { _, _ ->
                        MoneyStore.saveTx(this, MoneyStore.tx(this).filterNot { it.id == old.id })
                        refresh()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        b.show()
    }

    fun history() {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(16, 12, 16, 12)

        val search = EditText(this)
        search.hint = "🔍 Search category, method or note..."
        search.setPadding(12, 12, 12, 12)
        container.addView(search)

        val listLayout = LinearLayout(this)
        listLayout.orientation = LinearLayout.VERTICAL

        fun populate(query: String) {
            listLayout.removeAllViews()
            val all = MoneyStore.tx(this).reversed()
            val filtered = if (query.isBlank()) all else all.filter {
                it.category.contains(query, ignoreCase = true) ||
                it.note.contains(query, ignoreCase = true) ||
                it.method.contains(query, ignoreCase = true) ||
                it.date.contains(query, ignoreCase = true)
            }

            if (filtered.isEmpty()) {
                val empty = TextView(this)
                empty.text = "No matching transactions found."
                empty.setPadding(12, 20, 12, 20)
                listLayout.addView(empty)
                return
            }

            filtered.forEach { t ->
                val r = LinearLayout(this)
                r.orientation = LinearLayout.HORIZONTAL
                r.setPadding(8, 10, 8, 10)

                val x = TextView(this)
                x.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                val noteStr = if (t.note.isNotBlank()) "\n${t.note}" else ""
                x.text = "${t.date} • ${t.category} (${t.method})\n${t.type}: ${money(t.amount)}$noteStr"

                val e = Button(this)
                e.text = "Edit"
                e.setOnClickListener {
                    editTx(t)
                    refresh()
                    populate(search.text.toString())
                }

                r.addView(x)
                r.addView(e)
                listLayout.addView(r)
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                populate(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        populate("")

        val scroll = ScrollView(this)
        scroll.addView(listLayout)
        container.addView(scroll)

        AlertDialog.Builder(this)
            .setTitle("Transaction History")
            .setView(container)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showExportBackupMenu() {
        val options = arrayOf(
            "📄 Export Multi-page PDF Report",
            "📊 Export CSV (Excel Spreadsheet)",
            "💾 Save Offline Backup (JSON)",
            "📥 Restore from Backup (JSON)"
        )
        AlertDialog.Builder(this)
            .setTitle("Export & Backup")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> makePdf()
                    1 -> exportCsv()
                    2 -> exportBackupJson()
                    3 -> pickRestoreFile()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun makePdf() {
        val d = PdfDocument()
        val pageW = 595
        val pageH = 842
        var pageNum = 1

        var page = d.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
        var c = page.canvas
        val p = Paint()
        var y = 40f

        fun newPage() {
            d.finishPage(page)
            pageNum++
            page = d.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            c = page.canvas
            y = 40f
            p.textSize = 10f
            p.isFakeBoldText = false
            p.color = Color.DKGRAY
            c.drawText("My Money Report • Page $pageNum", 36f, y, p)
            y += 24f
        }

        fun line(s: String, size: Float = 11f, bold: Boolean = false) {
            if (y + size + 8f > 800f) {
                newPage()
            }
            p.textSize = size
            p.isFakeBoldText = bold
            p.color = Color.BLACK
            c.drawText(s.take(85), 36f, y, p)
            y += size + 8f
        }

        line("MY MONEY", 22f, true)
        line("Financial Statement & Transaction History", 14f)
        line("Generated on: ${MoneyStore.today()}")
        line("")

        line("ACCOUNT OVERVIEW", 16f, true)
        line("Starting Balance: ${money(MoneyStore.start(this))}")
        val txList = MoneyStore.tx(this)
        line("Total Income: ${money(txList.filter { it.type == "Income" }.sumOf { it.amount })}")
        line("Total Expenses: ${money(txList.filter { it.type == "Expense" }.sumOf { it.amount })}")
        line("Current Balance: ${money(MoneyStore.balance(this))}", 14f, true)
        line("")

        line("TRANSACTION HISTORY", 16f, true)
        if (txList.isEmpty()) {
            line("No transactions recorded yet.")
        } else {
            txList.reversed().forEach {
                val notePart = if (it.note.isNotBlank()) " | ${it.note.take(25)}" else ""
                line("${it.date} | ${it.type} | ${money(it.amount)} | ${it.category} (${it.method})$notePart", 10f)
            }
        }

        line("")
        line("FRIENDS & DUES SUMMARY", 16f, true)
        val allFriends = MoneyStore.friends(this)
        val friendNames = allFriends.map { it.name }.distinctBy { MoneyStore.key(it) }
        if (friendNames.isEmpty()) {
            line("No friend payment records.")
        } else {
            friendNames.forEach { name ->
                val fs = allFriends.filter { MoneyStore.key(it.name) == MoneyStore.key(name) }
                val due = MoneyStore.due(this, name)
                line(name, 13f, true)
                line(if (due > 0) "Status: Due to you ${money(due)}" else if (due < 0) "Status: You owe ${money(-due)}" else "Status: Settled")
                fs.reversed().forEach {
                    line("${it.date} | ${if (it.type.startsWith("I paid")) "You paid" else "Friend paid you"} | ${money(it.amount)}", 9f)
                }
            }
        }

        d.finishPage(page)
        val out = ByteArrayOutputStream()
        d.writeTo(out)
        d.close()
        pendingBytes = out.toByteArray()

        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_TITLE, "My_Money_Report_${MoneyStore.today()}.pdf")
            },
            REQ_PDF
        )
    }

    private fun exportCsv() {
        val csvStr = MoneyStore.toCsv(this)
        pendingBytes = csvStr.toByteArray(Charsets.UTF_8)
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_TITLE, "My_Money_Transactions_${MoneyStore.today()}.csv")
            },
            REQ_CSV
        )
    }

    private fun exportBackupJson() {
        val json = MoneyStore.backupJson(this)
        pendingBytes = json.toByteArray(Charsets.UTF_8)
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "My_Money_Backup_${MoneyStore.today()}.json")
            },
            REQ_BACKUP
        )
    }

    private fun pickRestoreFile() {
        AlertDialog.Builder(this)
            .setTitle("Restore Data")
            .setMessage("Restoring from a backup will overwrite existing records with the backup data. Do you want to proceed?")
            .setPositiveButton("Select Backup File") { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(intent, REQ_RESTORE)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onActivityResult(r: Int, c: Int, data: Intent?) {
        super.onActivityResult(r, c, data)
        if (c != RESULT_OK || data?.data == null) return

        val uri = data.data ?: return
        when (r) {
            REQ_PDF, REQ_CSV, REQ_BACKUP -> {
                pendingBytes?.let { bytes ->
                    contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    val label = when (r) {
                        REQ_PDF -> "PDF report saved successfully"
                        REQ_CSV -> "CSV file saved successfully"
                        else -> "Backup saved successfully"
                    }
                    Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
                }
            }
            REQ_RESTORE -> {
                try {
                    val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (!content.isNullOrBlank() && MoneyStore.restoreFromJson(this, content)) {
                        refresh()
                        Toast.makeText(this, "Data successfully restored!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Failed to restore: Invalid backup file.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error restoring file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

