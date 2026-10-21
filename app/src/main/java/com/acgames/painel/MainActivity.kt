package com.acgames.painel

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class MainActivity : AppCompatActivity() {

    companion object {
        const val URL = "https://acgamespay.shardweb.app/admin"
        const val HOST = "acgamespay.shardweb.app"
        const val RELOCK_AFTER_MS = 60_000L // volta a pedir a senha se ficar mais que isso fora do app
        const val MAX_FAILS = 5
        const val LOCKOUT_MS = 30_000L
        const val MIN_LEN = 4
    }

    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private lateinit var errorBox: View
    private lateinit var lockBox: View
    private lateinit var lockTitle: TextView
    private lateinit var lockSub: TextView
    private lateinit var lockMsg: TextView
    private lateinit var lockBtn: Button
    private lateinit var pass: EditText
    private lateinit var pass2: EditText

    private val prefs by lazy { getSharedPreferences("lock", Context.MODE_PRIVATE) }
    private var unlocked = false
    private var leftAt = 0L

    private val isSetup get() = prefs.getString("hash", null) == null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        web = findViewById(R.id.web)
        progress = findViewById(R.id.progress)
        errorBox = findViewById(R.id.errorBox)
        lockBox = findViewById(R.id.lockBox)
        lockTitle = findViewById(R.id.lockTitle)
        lockSub = findViewById(R.id.lockSub)
        lockMsg = findViewById(R.id.lockMsg)
        lockBtn = findViewById(R.id.lockBtn)
        pass = findViewById(R.id.pass)
        pass2 = findViewById(R.id.pass2)

        setupWebView()
        setupLock()
        showLock()
        web.loadUrl(URL)

        findViewById<Button>(R.id.retry).setOnClickListener {
            errorBox.visibility = View.GONE
            web.loadUrl(URL)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (unlocked && web.canGoBack()) web.goBack() else moveTaskToBack(true)
            }
        })
    }

    override fun onStart() {
        super.onStart()
        if (unlocked && leftAt > 0 && SystemClock.elapsedRealtime() - leftAt > RELOCK_AFTER_MS) showLock()
    }

    override fun onStop() {
        super.onStop()
        leftAt = SystemClock.elapsedRealtime()
    }

    // ---------------- WebView ----------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true // o painel guarda a chave de acesso no sessionStorage
            mediaPlaybackRequiresUserGesture = false // deixa o "som" de nova mensagem tocar
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == HOST) return false
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(request.url.toString())))
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progress.visibility = View.GONE
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    progress.visibility = View.GONE
                    errorBox.visibility = View.VISIBLE
                }
            }
        }
    }

    // ---------------- Senha do app ----------------

    private fun setupLock() {
        lockBtn.setOnClickListener { submit() }
        val onAction = TextView.OnEditorActionListener { v, _, _ ->
            if (isSetup && v === pass) pass2.requestFocus() else submit()
            true
        }
        pass.setOnEditorActionListener(onAction)
        pass2.setOnEditorActionListener(onAction)
    }

    private fun showLock() {
        unlocked = false
        web.visibility = View.INVISIBLE
        lockBox.visibility = View.VISIBLE
        pass.text.clear()
        pass2.text.clear()
        lockMsg.text = ""
        if (isSetup) {
            lockSub.text = "Crie uma senha para abrir o painel"
            pass2.visibility = View.VISIBLE
            pass.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
            lockBtn.text = "Salvar senha"
        } else {
            lockSub.text = "Digite a senha"
            pass2.visibility = View.GONE
            pass.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            lockBtn.text = "Entrar"
        }
        pass.requestFocus()
    }

    private fun unlock() {
        unlocked = true
        lockBox.visibility = View.GONE
        web.visibility = View.VISIBLE
        pass.text.clear()
        pass2.text.clear()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(lockBox.windowToken, 0)
    }

    private fun submit() {
        val p = pass.text.toString()
        if (isSetup) {
            if (p.length < MIN_LEN) { lockMsg.text = "Use pelo menos $MIN_LEN caracteres."; return }
            if (p != pass2.text.toString()) { lockMsg.text = "As senhas não são iguais."; return }
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            prefs.edit()
                .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString("hash", Base64.encodeToString(hash(p, salt), Base64.NO_WRAP))
                .putInt("fails", 0)
                .apply()
            unlock()
            return
        }

        val wait = prefs.getLong("lockUntil", 0) - System.currentTimeMillis()
        if (wait > 0) { lockMsg.text = "Muitas tentativas. Espere ${wait / 1000 + 1}s."; return }

        val salt = Base64.decode(prefs.getString("salt", "")!!, Base64.NO_WRAP)
        val saved = Base64.decode(prefs.getString("hash", "")!!, Base64.NO_WRAP)
        if (MessageDigest.isEqual(saved, hash(p, salt))) {
            prefs.edit().putInt("fails", 0).apply()
            unlock()
        } else {
            val fails = prefs.getInt("fails", 0) + 1
            if (fails >= MAX_FAILS) {
                prefs.edit().putInt("fails", 0).putLong("lockUntil", System.currentTimeMillis() + LOCKOUT_MS).apply()
                lockMsg.text = "Muitas tentativas. Espere ${LOCKOUT_MS / 1000}s."
            } else {
                prefs.edit().putInt("fails", fails).apply()
                lockMsg.text = "Senha incorreta."
            }
            pass.text.clear()
        }
    }

    private fun hash(password: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, 120_000, 256))
            .encoded
}
