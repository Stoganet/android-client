package com.stoganet.tv.data.auth

import android.content.Context
import androidx.datastore.core.Serializer
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.stoganet.tv.proto.Tokens
import java.io.InputStream
import java.io.OutputStream

class TokensSerializer(context: Context) : Serializer<Tokens> {

    private val aead: Aead = run {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_PREFS_NAME, MASTER_KEY_PREFS_NAME)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(Aead::class.java)
    }

    override val defaultValue: Tokens = Tokens.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): Tokens {
        val encrypted = input.readBytes()
        if (encrypted.isEmpty()) return defaultValue
        val plain = aead.decrypt(encrypted, ASSOCIATED_DATA)
        return Tokens.parseFrom(plain)
    }

    override suspend fun writeTo(t: Tokens, output: OutputStream) {
        val encrypted = aead.encrypt(t.toByteArray(), ASSOCIATED_DATA)
        output.write(encrypted)
    }

    private companion object {
        const val KEYSET_PREFS_NAME = "stoganet_token_keyset"
        const val MASTER_KEY_PREFS_NAME = "stoganet_master_key_prefs"
        const val MASTER_KEY_URI = "android-keystore://stoganet_master_key"
        val ASSOCIATED_DATA = "stoganet-tokens-v1".toByteArray()
    }
}
