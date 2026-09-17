package com.abhi.mymoney

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class FriendsActivity : Activity() {
    private lateinit var list: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_friends)
        list = findViewById(R.id.friendList)
        findViewById<Button>(R.id.backBtn).setOnClickListener { finish() }
        findViewById<Button>(R.id.addFriendBtn).setOnClickListener { add() }
        findViewById<Button>(R.id.addFriendBottom).setOnClickListener { add() }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) render()
    }

    fun money(v: Double): String = "₹" + String.format(Locale.US, "%.2f", v)

    fun render() {
        list.removeAllViews()
        val fs = MoneyStore.friends(this)
        val names = fs.map { it.name }.distinctBy { MoneyStore.key(it) }.sortedBy { it.lowercase() }

        if (names.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No friends recorded yet.\nTap 'Add Friend Payment' to record a split or loan."
            empty.textSize = 15f
            empty.setPadding(16, 40, 16, 40)
            list.addView(empty)
            return
        }

        names.forEach { name ->
            val due = MoneyStore.due(this, name)
            val all = fs.filter { MoneyStore.key(it.name) == MoneyStore.key(name) }
            val paid = all.filter { it.type.startsWith("I paid") }.sumOf { it.amount }
            val got = all.filter { !it.type.startsWith("I paid") }.sumOf { it.amount }

            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(20, 16, 20, 16)
            card.setBackgroundResource(R.drawable.card_bg)

            val title = TextView(this)
            title.text = name
            title.textSize = 18f
            title.setTypeface(null, Typeface.BOLD)

            val totals = TextView(this)
            totals.text = "You paid ${money(paid)}   •   They paid ${money(got)}"
            totals.setPadding(0, 4, 0, 4)

            val d = TextView(this)
            d.text = when {
                due > 0 -> "YOU WILL RECEIVE  ${money(due)}"
                due < 0 -> "YOU OWE  ${money(-due)}"
                else -> "SETTLED  ₹0.00"
            }
            d.textSize = 15f
            d.setTypeface(null, Typeface.BOLD)

            val open = Button(this)
            open.text = "View details"
            open.setOnClickListener {
                startActivity(Intent(this, FriendDetailActivity::class.java).putExtra("name", name))
            }

            card.addView(title)
            card.addView(totals)
            card.addView(d)
            card.addView(open)
            card.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }

            list.addView(card)
        }
    }

    private fun add() {
        FriendDetailActivity.dialog(this, null) { render() }
    }
}

