package com.example.shoppinglist

import android.app.*
import android.os.Bundle
import android.content.*
import android.graphics.Color
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.*

data class Item(var name:String,var qty:Double,var unit:String,var price:Long,var category:String,var note:String="",var done:Boolean=false)

class MainActivity:AppCompatActivity(){
 private lateinit var box:LinearLayout
 private lateinit var summary:TextView
 private lateinit var search:EditText
 private val items=mutableListOf<Item>()
 private val lists=mutableListOf("خرید امروز")
 private var current="خرید امروز"
 private val nf=NumberFormat.getNumberInstance(Locale.US)
 private val prefs by lazy{getSharedPreferences("shopping_v3",0)}

 override fun onCreate(b:Bundle?){super.onCreate(b);load();buildUi();render()}

 private fun buildUi(){
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,15,18,12);layoutDirection=View.LAYOUT_DIRECTION_RTL}
  root.addView(TextView(this).apply{text="🛒  لیست خرید من";textSize=26f;gravity=Gravity.CENTER;setPadding(0,5,0,10)})
  val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val spinner=Spinner(this)
  top.addView(spinner,LinearLayout.LayoutParams(0,55,1f))
  top.addView(Button(this).apply{text="+ لیست";setOnClickListener{newList()}})
  root.addView(top)
  spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,lists)
  spinner.setSelection(lists.indexOf(current).coerceAtLeast(0))
  spinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
   override fun onNothingSelected(p:AdapterView<*>?){}
   override fun onItemSelected(p:AdapterView<*>?,v:View?,pos:Int,id:Long){current=lists[pos];render()}
  }
  search=EditText(this).apply{hint="🔎 جستجوی کالا...";isSingleLine=true}
  search.addTextChangedListener(object:android.text.TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,a:Int,c:Int,d:Int){}
   override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){render()}
   override fun afterTextChanged(e:android.text.Editable?){}
  })
  root.addView(search)
  summary=TextView(this).apply{textSize=15f;gravity=Gravity.CENTER;setPadding(0,7,0,7)}
  root.addView(summary)
  box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  root.addView(ScrollView(this).apply{addView(box)},LinearLayout.LayoutParams(-1,0,1f))
  root.addView(Button(this).apply{text="+ افزودن کالا";setOnClickListener{addItem()}})
  val buttons=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  buttons.addView(Button(this).apply{text="🧹 خریدهای انجام‌شده";setOnClickListener{items.removeAll{belongs(it)&&it.done};save();render()}},LinearLayout.LayoutParams(0,-2,1f))
  buttons.addView(Button(this).apply{text="📊 خروجی CSV";setOnClickListener{exportCsv()}},LinearLayout.LayoutParams(0,-2,1f))
  root.addView(buttons)
  root.addView(Button(this).apply{text="⭐ کالاهای پرتکرار";setOnClickListener{frequent()}})
  setContentView(root)
 }

 private fun belongs(x:Item)=x.note.startsWith("[$current]")
 private fun visible():List<Pair<Int,Item>>{
  val q=search.text.toString().trim()
  return items.withIndex().filter{belongs(it.value)&&(q.isEmpty()||it.value.name.contains(q,true)||it.value.category.contains(q,true))}
   .map{it.index to it.value}.sortedBy{it.second.done}
 }

 private fun render(){
  if(!::box.isInitialized)return
  box.removeAllViews()
  val v=visible()
  v.forEach{(idx,x)->
   val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(2,5,2,5)}
   row.addView(CheckBox(this).apply{isChecked=x.done;setOnCheckedChangeListener{_,z->x.done=z;save();render()}},LinearLayout.LayoutParams(0,-2,.8f))
   val total=x.qty*x.price
   row.addView(TextView(this).apply{
    text="${x.name} × ${x.qty} ${x.unit}\n${nf.format(x.price)} تومان | ${nf.format(total.toLong())} تومان\n${x.category}"
    textSize=16f;setPadding(4,0,4,0);if(x.done)paintFlags=paintFlags or 16
   },LinearLayout.LayoutParams(0,-2,4.2f))
   row.addView(Button(this).apply{text="✎";setOnClickListener{editItem(idx)}},LinearLayout.LayoutParams(52,52))
   row.addView(Button(this).apply{text="×";setOnClickListener{items.removeAt(idx);save();render()}},LinearLayout.LayoutParams(52,52))
   box.addView(row)
   box.addView(View(this).apply{setBackgroundColor(0xFFE0E0E0.toInt())},LinearLayout.LayoutParams(-1,1))
  }
  val left=v.count{!it.second.done}
  val total=v.filter{!it.second.done}.sumOf{it.second.qty*it.second.price}
  summary.text="باقی‌مانده: $left | خریداری‌شده: ${v.size-left}\nمجموع برآورد: ${nf.format(total.toLong())} تومان"
 }

 private fun fields(old:Item?=null):Pair<LinearLayout,List<EditText>>{
  val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(22,0,22,0)}
  val a=listOf(
   EditText(this).apply{hint="نام کالا";setText(old?.name?:"")},
   EditText(this).apply{hint="تعداد";setText(old?.qty?.toString()?:"1")},
   EditText(this).apply{hint="واحد";setText(old?.unit?:"عدد")},
   EditText(this).apply{hint="قیمت واحد (تومان)";setText(old?.price?.toString()?:"0")},
   EditText(this).apply{hint="دسته‌بندی";setText(old?.category?:"خوراکی")},
   EditText(this).apply{hint="یادداشت";setText(old?.note?.removePrefix("[$current] ")?:"")}
  )
  a.forEach{b.addView(it)}
  return b to a
 }

 private fun addItem(){
  val f=fields()
  AlertDialog.Builder(this).setTitle("افزودن کالا").setView(f.first)
   .setNegativeButton("انصراف",null).setPositiveButton("افزودن"){_,_->
    val a=f.second
    if(a[0].text.isNotBlank()){
     items.add(Item(a[0].text.toString().trim(),a[1].text.toString().toDoubleOrNull()?:1.0,a[2].text.toString().ifBlank{"عدد"},
      a[3].text.toString().filter{it.isDigit()}.toLongOrNull()?:0,a[4].text.toString().ifBlank{"سایر"},"[$current] "+a[5].text))
     save();render()
    }
   }.show()
 }

 private fun editItem(i:Int){
  val f=fields(items[i])
  AlertDialog.Builder(this).setTitle("ویرایش کالا").setView(f.first)
   .setNegativeButton("انصراف",null).setPositiveButton("ذخیره"){_,_->
    val a=f.second;x(items[i],a);save();render()
   }.show()
 }

 private fun x(x:Item,a:List<EditText>){
  x.name=a[0].text.toString().trim()
  x.qty=a[1].text.toString().toDoubleOrNull()?:1.0
  x.unit=a[2].text.toString().ifBlank{"عدد"}
  x.price=a[3].text.toString().filter{it.isDigit()}.toLongOrNull()?:0
  x.category=a[4].text.toString().ifBlank{"سایر"}
  x.note="[$current] "+a[5].text.toString()
 }

 private fun newList(){
  val e=EditText(this).apply{hint="نام لیست جدید"}
  AlertDialog.Builder(this).setTitle("ساخت لیست جدید").setView(e)
   .setNegativeButton("انصراف",null).setPositiveButton("ساخت"){_,_->
    val n=e.text.toString().trim()
    if(n.isNotEmpty()&&!lists.contains(n)){lists.add(n);current=n;save();recreate()}
   }.show()
 }

 private fun frequent(){
  val freq=items.map{it.name}.filter{it.isNotBlank()}.groupingBy{it}.eachCount().entries.sortedByDescending{it.value}.take(15)
  if(freq.isEmpty()){Toast.makeText(this,"هنوز کالایی ثبت نشده",Toast.LENGTH_SHORT).show();return}
  AlertDialog.Builder(this).setTitle("کالاهای پرتکرار").setItems(freq.map{"${it.key}  (${it.value} بار)"}.toTypedArray()){_,which->
   items.add(Item(freq[which].key,1.0,"عدد",0,"سایر","[$current] "));save();render()
  }.setNegativeButton("بستن",null).show()
 }

 private fun exportCsv(){
  val sb=StringBuilder("\uFEFFنام کالا,تعداد,واحد,قیمت واحد,مبلغ,دسته بندی,خریداری شده\n")
  visible().forEach{(_,x)->sb.append("${x.name},${x.qty},${x.unit},${x.price},${x.qty*x.price},${x.category},${x.done}\n")}
  exportText=sb.toString()
  val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{type="text/csv";putExtra(Intent.EXTRA_TITLE,"shopping_${current}.csv")}
  startActivityForResult(intent,900)
 }
 private var exportText=""
 override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==900&&c==RESULT_OK&&d?.data!=null)contentResolver.openOutputStream(d.data!!)?.use{it.write(exportText.toByteArray(Charsets.UTF_8))}}

 private fun save(){
  prefs.edit().putString("lists",JSONArray(lists).toString()).putString("current",current).putString("items",JSONArray().apply{
   items.forEach{x->put(JSONObject().apply{put("name",x.name);put("qty",x.qty);put("unit",x.unit);put("price",x.price);put("category",x.category);put("note",x.note);put("done",x.done)})}
  }.toString()).apply()
 }

 private fun load(){
  try{
   prefs.getString("lists",null)?.let{val a=JSONArray(it);lists.clear();for(i in 0 until a.length())lists.add(a.getString(i))}
   if(lists.isEmpty())lists.add("خرید امروز")
   current=prefs.getString("current",lists.first())?:lists.first()
   prefs.getString("items",null)?.let{val a=JSONArray(it);for(i in 0 until a.length()){val o=a.getJSONObject(i);items.add(Item(o.getString("name"),o.optDouble("qty",1.0),o.optString("unit","عدد"),o.optLong("price",0),o.optString("category","سایر"),o.optString("note"),o.optBoolean("done")))}}}
  catch(_:Exception){}
 }
}
