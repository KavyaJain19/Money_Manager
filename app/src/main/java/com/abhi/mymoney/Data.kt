package com.abhi.mymoney

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*
data class Tx(val id:Long,val date:String,val type:String,val amount:Double,val category:String,val method:String,val note:String)
data class FriendTx(val id:Long,val date:String,val name:String,val type:String,val amount:Double,val note:String)

object MoneyStore {
    private const val P="money"
    private fun p(c:Context)=c.getSharedPreferences(P,0)
    fun start(c:Context)=p(c).getString("start","0")!!.toDoubleOrNull()?:0.0
    fun setStart(c:Context,v:Double)=p(c).edit().putString("start",v.toString()).apply()

    fun tx(c:Context):MutableList<Tx>{
        val r=mutableListOf<Tx>()
        p(c).getString("tx","").orEmpty().takeIf{it.isNotEmpty()}?.split("\n")?.forEachIndexed{i,s->
            val x=s.split("|"); if(x.size>=6){
                val a=x[2].toDoubleOrNull()?:return@forEachIndexed
                r.add(Tx(x.getOrNull(6)?.toLongOrNull()?:i+1L,x[0],x[1],a,x[3],x[4],x[5]))
            }}
        return r
    }
    fun friends(c:Context):MutableList<FriendTx>{
        val r=mutableListOf<FriendTx>()
        p(c).getString("friends","").orEmpty().takeIf{it.isNotEmpty()}?.split("\n")?.forEachIndexed{i,s->
            val x=s.split("|"); if(x.size>=5){
                val a=x[3].toDoubleOrNull()?:return@forEachIndexed
                r.add(FriendTx(x.getOrNull(5)?.toLongOrNull()?:i+1L,x[0],normalize(x[1]),x[2],a,x[4]))
            }}
        return r
    }
    fun saveTx(c:Context,l:List<Tx>){
        p(c).edit().putString("tx",l.joinToString("\n"){listOf(it.date,it.type,it.amount,it.category,it.method,it.note,it.id).joinToString("|")}).apply()
    }
    fun saveFriends(c:Context,l:List<FriendTx>){
        p(c).edit().putString("friends",l.joinToString("\n"){listOf(it.date,normalize(it.name),it.type,it.amount,it.note,it.id).joinToString("|")}).apply()
    }
    fun nextId(c:Context)=((tx(c).map{it.id}+friends(c).map{it.id}).maxOrNull()?:0)+1
    fun normalize(s:String)=s.trim().replace(Regex("\\s+")," ").lowercase(Locale.US).split(" ").filter{it.isNotBlank()}
        .joinToString(" "){it.replaceFirstChar{ch->ch.titlecase(Locale.US)}}
    fun key(s:String)=s.trim().replace(Regex("\\s+")," ").lowercase(Locale.US)
    fun today()=SimpleDateFormat("dd-MM-yyyy",Locale.US).format(Date())

    fun balance(c:Context):Double{
        val t=tx(c); val f=friends(c)
        val income=t.filter{it.type=="Income"}.sumOf{it.amount}
        val expense=t.filter{it.type=="Expense"}.sumOf{it.amount}
        val paid=f.filter{it.type=="I paid friend"||it.type=="I paid for friend"}.sumOf{it.amount}
        val received=f.filter{it.type=="Friend paid me"||it.type=="Friend paid for me"}.sumOf{it.amount}
        return start(c)+income-expense-paid+received
    }
    fun due(c:Context,name:String):Double=friends(c).filter{key(it.name)==key(name)}.sumOf{
        when(it.type){
            "I paid friend","I paid for friend"->it.amount
            "Friend paid me","Friend paid for me"->-it.amount
            else->0.0
        }
    }
}
