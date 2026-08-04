package com.stoganet.tv.data.auth

import androidx.datastore.core.Serializer
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.stoganet.tv.proto.Tokens
import java.io.InputStream
import java.io.OutputStream

internal class TestTokensSerializer : Serializer<Tokens> {

    private val aead: Aead = run {
        AeadConfig.register()
        KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM")).getPrimitive(Aead::class.java)
    }

    override val defaultValue: Tokens = Tokens.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): Tokens {
        val encrypted = input.readBytes()
        if (encrypted.isEmpty()) return defaultValue
        return Tokens.parseFrom(aead.decrypt(encrypted, ASSOCIATED_DATA))
    }

    override suspend fun writeTo(t: Tokens, output: OutputStream) {
        output.write(aead.encrypt(t.toByteArray(), ASSOCIATED_DATA))
    }

    private companion object {
        val ASSOCIATED_DATA = "stoganet-tokens-v1".toByteArray()
    }
}
