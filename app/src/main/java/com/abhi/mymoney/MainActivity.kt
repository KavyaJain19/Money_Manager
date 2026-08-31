package com.abhi.mymoney
import android.app.*; import android.content.*; import android.os.*; import android.text.InputType; import android.widget.*; import java.io.*; import java.util.*

class MainActivity:Activity(){
    lateinit var balance:TextView; lateinit var income:TextView; lateinit var expense:TextView; lateinit var list:LinearLayout
    var pdfBytes:ByteArray?=null
    override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_main)
        balance=findViewById(R.id.balance);income=findViewById(R.id.income);expense=findViewById(R.id.expense);list=findViewById(R.id.list)
        findViewById<Button>(R.id.setBalance).setOnClickListener{setStart()}
        findViewById<Button>(R.id.addBtn).setOnClickListener{addTx()}
        findViewById<Button>(R.id.friendsBtn).setOnClickListener{startActivity(Intent(this,FriendsActivity::class.java))}
        findViewById<Button>(R.id.historyBtn).setOnClickListener{history()}
        findViewById<Button>(R.id.export).setOnClickListener{makePdf()}
        findViewById<Button>(R.id.bottomAddBtn).setOnClickListener{addTx()}
        findViewById<Button>(R.id.bottomFriendsBtn).setOnClickListener{startActivity(Intent(this,FriendsActivity::class.java))}
        findViewById<Button>(R.id.bottomHistoryBtn).setOnClickListener{history()}
        findViewById<TextView>(R.id.viewAll).setOnClickListener{history()}; refresh()
    }
    override fun onResume(){super.onResume();if(::list.isInitialized)refresh()}
    fun money(v:Double)="₹"+String.format(Locale.US,"%.2f",v)
    fun refresh(){
        val t=MoneyStore.tx(this); income.text=money(t.filter{it.type=="Income"}.sumOf{it.amount});expense.text=money(t.filter{it.type=="Expense"}.sumOf{it.amount});balance.text=money(MoneyStore.balance(this));list.removeAllViews()
        if(t.isEmpty()){TextView(this).also{it.text="No transactions yet. Tap Add Transaction.";it.setPadding(12,20,12,20);list.addView(it)}}
        else t.takeLast(8).reversed().forEach{addRow(it)}
    }
    fun addRow(t:Tx){
        val r=LinearLayout(this);r.orientation=LinearLayout.HORIZONTAL;r.setPadding(10,12,10,12)
        val x=TextView(this);x.layoutParams=LinearLayout.LayoutParams(0,-2,1f);x.text="${t.date} • ${t.category}\n${t.type}: ${money(t.amount)}${if(t.note.isNotBlank())"\n"+t.note else ""}";r.addView(x)
        val e=Button(this);e.text="Edit";e.setOnClickListener{editTx(t)};r.addView(e);list.addView(r)
    }
    fun setStart(){val e=EditText(this);e.inputType=2 or 8192;e.setText(MoneyStore.start(this).toString());AlertDialog.Builder(this).setTitle("Starting Balance").setView(e).setPositiveButton("Save"){_,_->e.text.toString().toDoubleOrNull()?.let{if(it>=0){MoneyStore.setStart(this,it);refresh()}}}.setNegativeButton("Cancel",null).show()}
    fun addTx(){editTx(null)}
    fun editTx(old:Tx?){
        val v=layoutInflater.inflate(R.layout.dialog_transaction,null);val type=v.findViewById<Spinner>(R.id.type);val date=v.findViewById<EditText>(R.id.date);val amt=v.findViewById<EditText>(R.id.amount);val cat=v.findViewById<EditText>(R.id.category);val met=v.findViewById<EditText>(R.id.method);val note=v.findViewById<EditText>(R.id.note)
        type.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,arrayOf("Expense","Income"))
        if(old!=null){type.setSelection(if(old.type=="Income")1 else 0);date.setText(old.date);amt.setText(old.amount.toString());cat.setText(old.category);met.setText(old.method);note.setText(old.note)}else date.setText(MoneyStore.today())
        val b=AlertDialog.Builder(this).setTitle(if(old==null)"Add Transaction" else "Edit Transaction").setView(v).setPositiveButton("Save"){_,_->val a=amt.text.toString().toDoubleOrNull();if(a!=null&&a>0){val l=MoneyStore.tx(this);val n=Tx(old?.id?:MoneyStore.nextId(this),date.text.toString().ifBlank{MoneyStore.today()},type.selectedItem.toString(),a,cat.text.toString().ifBlank{"Other"},met.text.toString().ifBlank{"Other"},note.text.toString());if(old==null)l.add(n)else l[l.indexOfFirst{it.id==old.id}]=n;MoneyStore.saveTx(this,l);refresh()}}.setNegativeButton("Cancel",null)
        if(old!=null)b.setNeutralButton("Delete"){_,_->AlertDialog.Builder(this).setTitle("Delete transaction?").setPositiveButton("Delete"){_,_->MoneyStore.saveTx(this,MoneyStore.tx(this).filterNot{it.id==old.id});refresh()}.setNegativeButton("Cancel",null).show()}
        b.show()
    }
    fun history(){val l=LinearLayout(this);l.orientation=LinearLayout.VERTICAL;MoneyStore.tx(this).reversed().forEach{t->val r=LinearLayout(this);r.orientation=LinearLayout.HORIZONTAL;r.setPadding(6,8,6,8);val x=TextView(this);x.layoutParams=LinearLayout.LayoutParams(0,-2,1f);x.text="${t.date} • ${t.category}\n${t.type}  ${money(t.amount)}";r.addView(x);val e=Button(this);e.text="Edit";e.setOnClickListener{editTx(t)};r.addView(e);l.addView(r)};if(MoneyStore.tx(this).isEmpty())TextView(this).also{it.text="No transactions yet.";l.addView(it)};val s=ScrollView(this);s.addView(l);AlertDialog.Builder(this).setTitle("All Transaction History").setView(s).setNegativeButton("Close",null).show()}
    fun makePdf(){
        val d=android.graphics.pdf.PdfDocument();val page=d.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,1).create());val c=page.canvas;val p=android.graphics.Paint();var y=40f
        fun line(s:String,size:Float=12f,b:Boolean=false){p.textSize=size;p.isFakeBoldText=b;c.drawText(s.take(85),32f,y,p);y+=size+8}
        line("MY MONEY",22f,true);line("Financial Report",15f);line("Generated ${MoneyStore.today()}");line("")
        line("ACCOUNT SUMMARY",16f,true);line("Starting Balance: ${money(MoneyStore.start(this))}");val tx=MoneyStore.tx(this);line("Income: ${money(tx.filter{it.type=="Income"}.sumOf{it.amount})}");line("Expenses: ${money(tx.filter{it.type=="Expense"}.sumOf{it.amount})}");line("Current Balance: ${money(MoneyStore.balance(this))}",14f,true);line("")
        line("TRANSACTION HISTORY",16f,true);tx.reversed().forEach{line("${it.date} | ${it.type} | ${it.category} | ${money(it.amount)}")}
        line("");line("FRIENDS & DUES",16f,true);MoneyStore.friends(this).map{it.name}.distinctBy{MoneyStore.key(it)}.forEach{name->val fs=MoneyStore.friends(this).filter{MoneyStore.key(it.name)==MoneyStore.key(name)};val due=MoneyStore.due(this,name);line(name,14f,true);line(if(due>0)"Due to you: ${money(due)}" else if(due<0)"You owe: ${money(-due)}" else "Settled");fs.reversed().forEach{line("${it.date} | ${if(it.type.startsWith("I paid"))"You paid" else "Friend paid you"} | ${money(it.amount)}",10f)}}
        d.finishPage(page);val out=ByteArrayOutputStream();d.writeTo(out);d.close();pdfBytes=out.toByteArray();startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{type="application/pdf";putExtra(Intent.EXTRA_TITLE,"My_Money_Report.pdf")},99)
    }
    override fun onActivityResult(r:Int,c:Int,data:Intent?){super.onActivityResult(r,c,data);if(r==99&&c==RESULT_OK&&data?.data!=null){contentResolver.openOutputStream(data.data!!)?.use{it.write(pdfBytes)};Toast.makeText(this,"PDF saved",Toast.LENGTH_SHORT).show()}}
}
