package com.acgames.painel

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Guarda a chave do painel criptografada (Android Keystore), só para o serviço de notificações usar. */
object SecureStore {
    private const val ALIAS = "painel_key"
    private const val PREF = "secure"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return g.generateKey()
    }

    fun put(ctx: Context, value: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val data = c.iv + c.doFinal(value.toByteArray(Charsets.UTF_8)) // 12 bytes de IV + conteúdo
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("k", Base64.encodeToString(data, Base64.NO_WRAP)).apply()
    }

    fun get(ctx: Context): String? = try {
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("k", null)
        if (raw == null) null else {
            val data = Base64.decode(raw, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, 12))
            String(c.doFinal(data, 12, data.size - 12), Charsets.UTF_8)
        }
    } catch (e: Exception) { null }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
        ctx.getSharedPreferences("notify", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
