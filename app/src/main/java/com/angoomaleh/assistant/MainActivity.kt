package com.angoomaleh.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var status: TextView
    private var recognizer: SpeechRecognizer? = null
    private val requestAudio = 41
    private val requestContact = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        val title = TextView(this).apply {
            text = "🍇 انگور ماله\nدستیار صوتی فارسی"
            textSize = 26f
        }
        status = TextView(this).apply {
            text = "برای شروع، دکمه شنیدن فرمان را بزنید."
            textSize = 18f
            setPadding(0, 32, 0, 32)
        }
        val listen = Button(this).apply {
            text = "🎙 شنیدن فرمان"
            setOnClickListener { startListening() }
        }
        val gallery = Button(this).apply {
            text = "باز کردن گالری"
            setOnClickListener { openGallery() }
        }
        val camera = Button(this).apply {
            text = "باز کردن دوربین"
            setOnClickListener { openCamera() }
        }
        layout.addView(title); layout.addView(status); layout.addView(listen)
        layout.addView(gallery); layout.addView(camera)
        setContentView(layout)

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "سرویس تشخیص گفتار روی این گوشی در دسترس نیست."
        }
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestAudio)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "تشخیص گفتار در دستگاه فعال نیست."
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { status.text = "گوش می‌دهم..." }
                override fun onBeginningOfSpeech() { status.text = "در حال شنیدن..." }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status.text = "در حال پردازش..." }
                override fun onError(error: Int) { status.text = "گفتار تشخیص داده نشد؛ دوباره تلاش کنید." }
                override fun onResults(results: Bundle?) {
                    val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val command = words?.firstOrNull().orEmpty()
                    status.text = "شنیدم: $command"
                    handleCommand(command)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "فرمان خود را بگویید")
            }
            sr.startListening(intent)
        }
    }

    private fun handleCommand(raw: String) {
        val c = raw.lowercase(Locale("fa", "IR"))
        when {
            c.contains("گالری") || c.contains("عکس") -> openGallery()
            c.contains("دوربین") || c.contains("عکس بگیر") -> openCamera()
            c.contains("تماس") || c.contains("زنگ") -> {
                status.text = "برای تماس، مخاطب را انتخاب کنید."
                startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI), requestContact)
            }
            c.contains("هی انگور ماله") -> status.text = "جانم! فرمان بعدی را بگویید."
            else -> status.text = "این فرمان را هنوز پشتیبانی نمی‌کنم."
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_VIEW).apply { type = "image/*" }
        runCatching { startActivity(intent) }.onFailure { Toast.makeText(this, "گالری پیدا نشد", Toast.LENGTH_SHORT).show() }
    }

    private fun openCamera() {
        runCatching { startActivity(Intent("android.media.action.IMAGE_CAPTURE")) }
            .onFailure { Toast.makeText(this, "دوربین پیدا نشد", Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestContact && resultCode == RESULT_OK && data?.data != null) {
            val uri: Uri = data.data!!
            val cursor = contentResolver.query(uri, arrayOf(ContactsContract.Contacts._ID), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getString(0)
                    val phoneCursor = contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
                        arrayOf(id), null
                    )
                    phoneCursor?.use { p ->
                        if (p.moveToFirst()) {
                            val number = p.getString(0)
                            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
                        } else status.text = "برای این مخاطب شماره‌ای ثبت نشده است."
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        super.onDestroy()
    }
}
