package com.abhi.mymoney

import android.app.*
import android.content.*
import android.graphics.Typeface
import android.os.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class FriendDetailActivity : Activity() {
    lateinit var name: String
    lateinit var history: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        name = intent.getStringExtra("name").orEmpty()
        setContentView(R.layout.activity_friend_detail)
        history = findViewById(R.id.history)
        findViewById<Button>(R.id.backBtn).setOnClickListener { finish() }
        findViewById<Button>(R.id.addBtn).setOnClickListener { dialog(this, null) { render() } }
        findViewById<Button>(R.id.bottomAdd).setOnClickListener { dialog(this, null) { render() } }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::history.isInitialized) render()
    }

    fun money(v: Double) = "₹" + String.format(Locale.US, "%.2f", v)

    fun render() {
        val all = MoneyStore.friends(this)
        val fs = all.filter { MoneyStore.key(it.name) == MoneyStore.key(name) }.sortedByDescending { it.id }
        val paid = fs.filter { it.type.startsWith("I paid") }.sumOf { it.amount }
        val got = fs.filter { !it.type.startsWith("I paid") }.sumOf { it.amount }
        val due = paid - got

        findViewById<TextView>(R.id.friendName).text = name
        findViewById<TextView>(R.id.paidValue).text = "You paid\n${money(paid)}"
        findViewById<TextView>(R.id.receivedValue).text = "They paid you\n${money(got)}"
        findViewById<TextView>(R.id.dueValue).text =
            if (due > 0) "YOU WILL RECEIVE ${money(due)}"
            else if (due < 0) "YOU OWE ${money(-due)}"
            else "SETTLED ₹0.00"

        history.removeAllViews()
        if (fs.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No payments recorded yet."
            empty.setPadding(12, 20, 12, 20)
            history.addView(empty)
            return
        }

        fs.forEach { p ->
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.setPadding(10, 12, 10, 12)

            val x = TextView(this)
            x.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            val noteText = if (p.note.isNotBlank()) "\n${p.note}" else ""
            x.text = "${p.date}\n${if (p.type.startsWith("I paid")) "You paid" else "Friend paid you"} • ${money(p.amount)}$noteText"

            val e = Button(this)
            e.text = "Edit"
            e.setOnClickListener { dialog(this, p) { render() } }

            val del = Button(this)
            del.text = "Delete"
            del.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Delete payment?")
                    .setMessage("The due balance will be recalculated.")
                    .setPositiveButton("Delete") { _, _ ->
                        MoneyStore.saveFriends(this, MoneyStore.friends(this).filterNot { it.id == p.id })
                        render()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            r.addView(x)
            r.addView(e)
            r.addView(del)
            history.addView(r)
        }
    }

    companion object {
        fun dialog(a: Activity, old: FriendTx?, done: () -> Unit) {
            val v = a.layoutInflater.inflate(R.layout.dialog_friend_payment, null)
            val n = v.findViewById<AutoCompleteTextView>(R.id.name)
            val d = v.findViewById<EditText>(R.id.date)
            val t = v.findViewById<Spinner>(R.id.type)
            val m = v.findViewById<EditText>(R.id.amount)
            val note = v.findViewById<EditText>(R.id.note)

            val existingFriends = MoneyStore.friends(a).map { it.name }.distinctBy { MoneyStore.key(it) }
            n.setAdapter(ArrayAdapter(a, android.R.layout.simple_dropdown_item_1line, existingFriends))

            t.adapter = ArrayAdapter(a, android.R.layout.simple_spinner_dropdown_item, arrayOf("I paid friend", "Friend paid me"))

            d.setOnClickListener {
                val cal = Calendar.getInstance()
                try {
                    val cur = SimpleDateFormat("dd-MM-yyyy", Locale.US).parse(d.text.toString())
                    if (cur != null) cal.time = cur
                } catch (_: Exception) {}
                DatePickerDialog(
                    a,
                    { _, y, month, day ->
                        d.setText(String.format(Locale.US, "%02d-%02d-%04d", day, month + 1, y))
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }

            if (old != null) {
                n.setText(old.name)
                d.setText(old.date)
                m.setText(old.amount.toString())
                note.setText(old.note)
                t.setSelection(if (old.type.startsWith("Friend paid")) 1 else 0)
            } else {
                if (a is FriendDetailActivity) {
                    n.setText(a.name)
                }
                d.setText(MoneyStore.today())
            }

            AlertDialog.Builder(a)
                .setTitle(if (old == null) "Add Friend Payment" else "Edit Friend Payment")
                .setView(v)
                .setPositiveButton("Save") { _, _ ->
                    val person = MoneyStore.normalize(n.text.toString())
                    val amount = m.text.toString().toDoubleOrNull()
                    if (person.isNotBlank() && amount != null && amount > 0) {
                        val l = MoneyStore.friends(a).toMutableList()
                        val item = FriendTx(
                            old?.id ?: MoneyStore.nextId(a),
                            d.text.toString().ifBlank { MoneyStore.today() },
                            person,
                            t.selectedItem.toString(),
                            amount,
                            note.text.toString().trim()
                        )
                        if (old == null) {
                            l.add(item)
                        } else {
                            val idx = l.indexOfFirst { it.id == old.id }
                            if (idx >= 0) l[idx] = item else l.add(item)
                        }
                        MoneyStore.saveFriends(
                            a,
                            l.map { if (MoneyStore.key(it.name) == MoneyStore.key(person)) it.copy(name = person) else it }
                        )
                        done()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}

