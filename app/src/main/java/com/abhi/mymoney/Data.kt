package com.abhi.mymoney

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class Tx(
    val id: Long,
    val date: String,
    val type: String,
    val amount: Double,
    val category: String,
    val method: String,
    val note: String
)

data class FriendTx(
    val id: Long,
    val date: String,
    val name: String,
    val type: String,
    val amount: Double,
    val note: String
)

object MoneyStore {
    private const val P = "money"
    private fun p(c: Context) = c.getSharedPreferences(P, 0)

    val CATEGORIES = arrayOf(
        "Food & Dining",
        "Groceries",
        "Shopping",
        "Transportation",
        "Bills & Utilities",
        "Salary",
        "Entertainment",
        "Health & Medical",
        "Investment",
        "Personal Care",
        "Other"
    )

    val PAYMENT_METHODS = arrayOf(
        "Cash",
        "UPI / GPay / PhonePe",
        "Credit Card",
        "Debit Card",
        "Net Banking",
        "Other"
    )

    fun start(c: Context): Double = p(c).getString("start", "0")!!.toDoubleOrNull() ?: 0.0

    fun setStart(c: Context, v: Double) = p(c).edit().putString("start", v.toString()).apply()

    fun tx(c: Context): MutableList<Tx> {
        val raw = p(c).getString("tx", "").orEmpty().trim()
        if (raw.isEmpty()) return mutableListOf()

        val r = mutableListOf<Tx>()
        if (raw.startsWith("[")) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    r.add(
                        Tx(
                            id = o.optLong("id", i + 1L),
                            date = o.optString("date", today()),
                            type = o.optString("type", "Expense"),
                            amount = o.optDouble("amount", 0.0),
                            category = o.optString("category", "Other"),
                            method = o.optString("method", "Cash"),
                            note = o.optString("note", "")
                        )
                    )
                }
                return r
            } catch (_: Exception) {
                // fallback to legacy parser below if JSON parse failed
            }
        }

        // Legacy pipe-separated migration
        raw.split("\n").forEachIndexed { i, s ->
            val x = s.split("|")
            if (x.size >= 6) {
                val a = x[2].toDoubleOrNull() ?: return@forEachIndexed
                r.add(Tx(x.getOrNull(6)?.toLongOrNull() ?: (i + 1L), x[0], x[1], a, x[3], x[4], x[5]))
            }
        }
        if (r.isNotEmpty()) {
            saveTx(c, r) // Migrate immediately to JSON format
        }
        return r
    }

    fun friends(c: Context): MutableList<FriendTx> {
        val raw = p(c).getString("friends", "").orEmpty().trim()
        if (raw.isEmpty()) return mutableListOf()

        val r = mutableListOf<FriendTx>()
        if (raw.startsWith("[")) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    r.add(
                        FriendTx(
                            id = o.optLong("id", i + 1L),
                            date = o.optString("date", today()),
                            name = normalize(o.optString("name", "Friend")),
                            type = o.optString("type", "I paid friend"),
                            amount = o.optDouble("amount", 0.0),
                            note = o.optString("note", "")
                        )
                    )
                }
                return r
            } catch (_: Exception) {
                // fallback to legacy parser below if JSON parse failed
            }
        }

        // Legacy pipe-separated migration
        raw.split("\n").forEachIndexed { i, s ->
            val x = s.split("|")
            if (x.size >= 5) {
                val a = x[3].toDoubleOrNull() ?: return@forEachIndexed
                r.add(FriendTx(x.getOrNull(5)?.toLongOrNull() ?: (i + 1L), x[0], normalize(x[1]), x[2], a, x[4]))
            }
        }
        if (r.isNotEmpty()) {
            saveFriends(c, r) // Migrate immediately to JSON format
        }
        return r
    }

    fun saveTx(c: Context, l: List<Tx>) {
        val arr = JSONArray()
        l.forEach {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("date", it.date)
            o.put("type", it.type)
            o.put("amount", it.amount)
            o.put("category", it.category)
            o.put("method", it.method)
            o.put("note", it.note)
            arr.put(o)
        }
        p(c).edit().putString("tx", arr.toString()).apply()
    }

    fun saveFriends(c: Context, l: List<FriendTx>) {
        val arr = JSONArray()
        l.forEach {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("date", it.date)
            o.put("name", normalize(it.name))
            o.put("type", it.type)
            o.put("amount", it.amount)
            o.put("note", it.note)
            arr.put(o)
        }
        p(c).edit().putString("friends", arr.toString()).apply()
    }

    fun nextId(c: Context): Long = ((tx(c).map { it.id } + friends(c).map { it.id }).maxOrNull() ?: 0L) + 1L

    fun normalize(s: String): String = s.trim().replace(Regex("\\s+"), " ").lowercase(Locale.US).split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar { ch -> ch.titlecase(Locale.US) } }

    fun key(s: String): String = s.trim().replace(Regex("\\s+"), " ").lowercase(Locale.US)

    fun today(): String = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())

    fun balance(c: Context): Double {
        val t = tx(c)
        val f = friends(c)
        val income = t.filter { it.type == "Income" }.sumOf { it.amount }
        val expense = t.filter { it.type == "Expense" }.sumOf { it.amount }
        val paid = f.filter { it.type == "I paid friend" || it.type == "I paid for friend" }.sumOf { it.amount }
        val received = f.filter { it.type == "Friend paid me" || it.type == "Friend paid for me" }.sumOf { it.amount }
        return start(c) + income - expense - paid + received
    }

    fun due(c: Context, name: String): Double = friends(c).filter { key(it.name) == key(name) }.sumOf {
        when (it.type) {
            "I paid friend", "I paid for friend" -> it.amount
            "Friend paid me", "Friend paid for me" -> -it.amount
            else -> 0.0
        }
    }

    fun toCsv(c: Context): String {
        val sb = StringBuilder()
        sb.append("ID,Date,Type,Amount,Category,Payment Method,Note\n")
        tx(c).forEach {
            val cleanNote = it.note.replace("\"", "\"\"")
            val cleanCat = it.category.replace("\"", "\"\"")
            val cleanMet = it.method.replace("\"", "\"\"")
            sb.append("${it.id},\"${it.date}\",\"${it.type}\",${it.amount},\"$cleanCat\",\"$cleanMet\",\"$cleanNote\"\n")
        }
        return sb.toString()
    }

    fun backupJson(c: Context): String {
        val root = JSONObject()
        root.put("version", 2)
        root.put("exportDate", today())
        root.put("startBalance", start(c))

        val txArr = JSONArray()
        tx(c).forEach {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("date", it.date)
            o.put("type", it.type)
            o.put("amount", it.amount)
            o.put("category", it.category)
            o.put("method", it.method)
            o.put("note", it.note)
            txArr.put(o)
        }
        root.put("transactions", txArr)

        val fArr = JSONArray()
        friends(c).forEach {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("date", it.date)
            o.put("name", it.name)
            o.put("type", it.type)
            o.put("amount", it.amount)
            o.put("note", it.note)
            fArr.put(o)
        }
        root.put("friends", fArr)

        return root.toString(2)
    }

    fun restoreFromJson(c: Context, jsonStr: String): Boolean {
        return try {
            val root = JSONObject(jsonStr)
            if (root.has("startBalance")) {
                setStart(c, root.optDouble("startBalance", 0.0))
            }

            if (root.has("transactions")) {
                val txArr = root.getJSONArray("transactions")
                val list = mutableListOf<Tx>()
                for (i in 0 until txArr.length()) {
                    val o = txArr.getJSONObject(i)
                    list.add(
                        Tx(
                            id = o.optLong("id", i + 1L),
                            date = o.optString("date", today()),
                            type = o.optString("type", "Expense"),
                            amount = o.optDouble("amount", 0.0),
                            category = o.optString("category", "Other"),
                            method = o.optString("method", "Cash"),
                            note = o.optString("note", "")
                        )
                    )
                }
                saveTx(c, list)
            }

            if (root.has("friends")) {
                val fArr = root.getJSONArray("friends")
                val list = mutableListOf<FriendTx>()
                for (i in 0 until fArr.length()) {
                    val o = fArr.getJSONObject(i)
                    list.add(
                        FriendTx(
                            id = o.optLong("id", i + 1L),
                            date = o.optString("date", today()),
                            name = normalize(o.optString("name", "Friend")),
                            type = o.optString("type", "I paid friend"),
                            amount = o.optDouble("amount", 0.0),
                            note = o.optString("note", "")
                        )
                    )
                }
                saveFriends(c, list)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
