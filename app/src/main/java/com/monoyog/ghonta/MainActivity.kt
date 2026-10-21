package com.monoyog.ghonta

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView
    private lateinit var status: TextView
    private lateinit var go: Button
    private lateinit var mins: EditText
    private lateinit var msg: EditText
    private lateinit var newTask: EditText
    private lateinit var list: LinearLayout
    private var tasks = mutableListOf<Task>()
    private var testTts: TextToSpeech? = null

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()

    private fun match() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun tv(s: String, size: Float, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    private fun section(s: String): TextView {
        val t = tv(s, 15f, true)
        t.setPadding(0, dp(24), 0, dp(6))
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        tasks = Store.tasks(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(18), dp(28), dp(18), dp(36))

        root.addView(tv("মনোযোগের ঘণ্টা", 26f, true), match())

        clock = tv("", 52f, true)
        clock.gravity = Gravity.CENTER
        clock.setPadding(0, dp(20), 0, 0)
        root.addView(clock, match())

        status = tv("", 15f)
        status.gravity = Gravity.CENTER
        root.addView(status, match())

        go = Button(this)
        go.textSize = 18f
        go.setOnClickListener { toggle() }
        val goLp = match()
        goLp.topMargin = dp(12)
        root.addView(go, goLp)

        // কত মিনিট পর পর
        root.addView(section("কত মিনিট পর পর বলবে"), match())
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        mins = EditText(this)
        mins.inputType = InputType.TYPE_CLASS_NUMBER
        mins.setText(Store.minutes(this).toString())
        for (m in listOf(5, 10, 15, 30)) {
            val b = Button(this)
            b.text = Store.bn(m)
            b.setOnClickListener {
                mins.setText(m.toString())
                saveInputs()
            }
            row.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(row, match())
        val minRow = LinearLayout(this)
        minRow.orientation = LinearLayout.HORIZONTAL
        minRow.gravity = Gravity.CENTER_VERTICAL
        minRow.addView(tv("নিজের সময়:  ", 16f))
        minRow.addView(mins, LinearLayout.LayoutParams(dp(90), ViewGroup.LayoutParams.WRAP_CONTENT))
        minRow.addView(tv("  মিনিট", 16f))
        root.addView(minRow, match())

        // কী বলবে
        root.addView(section("কী বলবে"), match())
        msg = EditText(this)
        msg.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        msg.minLines = 4
        msg.gravity = Gravity.TOP
        msg.setText(Store.message(this))
        root.addView(msg, match())

        // আজকের কাজ
        root.addView(section("আজকের কাজ"), match())
        val addRow = LinearLayout(this)
        addRow.orientation = LinearLayout.HORIZONTAL
        newTask = EditText(this)
        newTask.hint = "একটা কাজ লিখুন"
        newTask.inputType = InputType.TYPE_CLASS_TEXT
        addRow.addView(newTask, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val add = Button(this)
        add.text = "যোগ"
        add.setOnClickListener { addTask() }
        addRow.addView(add)
        root.addView(addRow, match())

        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        root.addView(list, match())

        // পরীক্ষা
        val test = Button(this)
        test.text = "এখনই শুনুন"
        test.setOnClickListener { testVoice() }
        val testLp = match()
        testLp.topMargin = dp(24)
        root.addView(test, testLp)

        val note = tv(
            "চলার সময় ব্যাটারি একটু বেশি খরচ হয়। স্ক্রিন বন্ধ থাকলেও ঘণ্টা বাজবে। " +
                "হোম স্ক্রিনে উইজেট যোগ করতে ফাঁকা জায়গায় চেপে ধরে Widgets থেকে মনোযোগের ঘণ্টা বেছে নিন।",
            13f
        )
        note.setPadding(0, dp(16), 0, 0)
        root.addView(note, match())

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
        renderTasks()
    }

    private val ui = object : Runnable {
        override fun run() {
            paint()
            handler.postDelayed(this, 500)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(ui)
    }

    override fun onPause() {
        handler.removeCallbacks(ui)
        saveInputs()
        super.onPause()
    }

    override fun onDestroy() {
        testTts?.shutdown()
        super.onDestroy()
    }

    private fun saveInputs() {
        Store.setMessage(this, msg.text.toString())
        Store.setMinutes(this, mins.text.toString().toIntOrNull() ?: 5)
    }

    private fun paint() {
        val run = Store.running(this)
        go.text = if (run) "থামান" else "শুরু করুন"
        if (run) {
            val left = ((Store.nextAt(this) - System.currentTimeMillis()) / 1000).coerceAtLeast(0L)
            val s = (left % 60).toInt()
            clock.text = Store.bn((left / 60).toInt()) + ":" + (if (s < 10) "০" else "") + Store.bn(s)
            status.text = "চলছে। পরের ঘণ্টা বাকি"
        } else {
            clock.text = Store.bn(Store.minutes(this)) + ":০০"
            status.text = "বন্ধ আছে"
        }
    }

    private fun toggle() {
        saveInputs()
        val i = Intent(this, SpeakService::class.java)
        if (Store.running(this)) {
            i.action = SpeakService.ACTION_STOP
            startService(i)
        } else {
            i.action = SpeakService.ACTION_START
            startForegroundService(i)
        }
        handler.postDelayed({ paint() }, 400)
    }

    private fun addTask() {
        val s = newTask.text.toString().trim()
        if (s.isEmpty()) return
        tasks.add(Task(s, false))
        newTask.setText("")
        Store.saveTasks(this, tasks)
        renderTasks()
    }

    private fun renderTasks() {
        list.removeAllViews()
        for (t in tasks) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            val cb = CheckBox(this)
            cb.text = t.text
            cb.textSize = 17f
            cb.isChecked = t.done
            cb.setOnCheckedChangeListener { _, checked ->
                t.done = checked
                Store.saveTasks(this, tasks)
            }
            val del = Button(this)
            del.text = "×"
            del.setOnClickListener {
                tasks.remove(t)
                Store.saveTasks(this, tasks)
                renderTasks()
            }
            r.addView(cb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(del)
            list.addView(r, match())
        }
    }

    private fun testVoice() {
        saveInputs()
        val existing = testTts
        if (existing != null) {
            speakTest(existing)
            return
        }
        testTts = TextToSpeech(this) { st ->
            val t = testTts
            if (st == TextToSpeech.SUCCESS && t != null) {
                speakTest(t)
            } else {
                Toast.makeText(this, "ভয়েস ইঞ্জিন চালু হয়নি", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun speakTest(t: TextToSpeech) {
        var r = t.setLanguage(Locale.forLanguageTag("bn-BD"))
        if (r < 0) r = t.setLanguage(Locale.forLanguageTag("bn-IN"))
        if (r < 0) {
            Toast.makeText(
                this,
                "এই ফোনে বাংলা ভয়েস নেই। Settings > Text-to-speech এ গিয়ে বাংলা ডাউনলোড করুন।",
                Toast.LENGTH_LONG
            ).show()
        }
        t.setSpeechRate(0.9f)
        t.speak(Store.speech(this), TextToSpeech.QUEUE_FLUSH, null, "test")
    }
}
