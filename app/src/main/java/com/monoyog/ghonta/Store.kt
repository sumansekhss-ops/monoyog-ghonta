package com.monoyog.ghonta

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Task(var text: String, var done: Boolean)

object Store {
    const val DEFAULT_MSG =
        "ফোকাস মানে এখন যে কাজটা হাতে আছে, শুধু সেটাতেই মন রাখা। ফোন নয়, নোটিফিকেশন নয়। একটা লম্বা শ্বাস নিন, আর যা করছিলেন সেখানে ফিরে যান।"

    private val NUM = arrayOf("শূন্য", "এক", "দুই", "তিন", "চার", "পাঁচ", "ছয়", "সাত", "আট", "নয়", "দশ")

    private fun p(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("monoyog", Context.MODE_PRIVATE)

    fun minutes(c: Context): Int = p(c).getInt("minutes", 5)
    fun setMinutes(c: Context, m: Int) {
        p(c).edit().putInt("minutes", m.coerceIn(1, 180)).apply()
    }

    fun message(c: Context): String = p(c).getString("message", DEFAULT_MSG) ?: DEFAULT_MSG
    fun setMessage(c: Context, s: String) {
        p(c).edit().putString("message", s).apply()
    }

    fun running(c: Context): Boolean = p(c).getBoolean("running", false)
    fun nextAt(c: Context): Long = p(c).getLong("nextAt", 0L)
    fun setRun(c: Context, r: Boolean, next: Long) {
        p(c).edit().putBoolean("running", r).putLong("nextAt", next).apply()
    }

    fun tasks(c: Context): MutableList<Task> {
        val out = mutableListOf<Task>()
        val s = p(c).getString("tasks", null) ?: return out
        try {
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Task(o.getString("t"), o.getBoolean("d")))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun saveTasks(c: Context, list: List<Task>) {
        val a = JSONArray()
        for (t in list) a.put(JSONObject().put("t", t.text).put("d", t.done))
        p(c).edit().putString("tasks", a.toString()).apply()
    }

    fun bn(n: Int): String {
        val d = "০১২৩৪৫৬৭৮৯"
        return n.toString().map { d[it - '0'] }.joinToString("")
    }

    /** ঘণ্টা বাজলে যা বলা হবে: আপনার কথা, তারপর বাকি কাজ। */
    fun speech(c: Context): String {
        val out = ArrayList<String>()
        val m = message(c).trim()
        if (m.isNotEmpty()) out.add(m)
        val all = tasks(c)
        val pend = all.filter { !it.done }
        if (pend.isNotEmpty()) {
            out.add("আজকের বাকি কাজ " + bn(pend.size) + " টি।")
            pend.forEachIndexed { i, t ->
                val n = if (i + 1 <= 10) NUM[i + 1] else bn(i + 1)
                out.add(n + ", " + t.text.trimEnd('।', '.') + "।")
            }
        } else if (all.isNotEmpty()) {
            out.add("আজকের সব কাজ শেষ। দারুণ।")
        }
        return out.joinToString(" ")
    }
}
