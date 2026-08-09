package com.stoganet.core.data.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.stoganet.core.proto.SubtitlePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class SubtitlePreferenceStore(private val dataStore: DataStore<SubtitlePreference>) {

    val preferredLanguage: Flow<String?> =
        dataStore.data.map { it.preferredLanguage.takeIf { lang -> lang.isNotEmpty() } }

    suspend fun current(): String? = preferredLanguage.first()

    suspend fun savePreferredLanguage(language: String) {
        dataStore.updateData {
            SubtitlePreference.newBuilder().setPreferredLanguage(language).build()
        }
    }

    companion object {
        fun create(context: Context): SubtitlePreferenceStore {
            val ds = DataStoreFactory.create(
                serializer = SubtitlePreferenceSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { SubtitlePreference.getDefaultInstance() },
                produceFile = { File(context.filesDir, "stoganet-subtitle-preference.pb") },
            )
            return SubtitlePreferenceStore(ds)
        }
    }
}

private object SubtitlePreferenceSerializer : Serializer<SubtitlePreference> {
    override val defaultValue: SubtitlePreference = SubtitlePreference.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): SubtitlePreference = SubtitlePreference.parseFrom(input)

    override suspend fun writeTo(t: SubtitlePreference, output: OutputStream) {
        t.writeTo(output)
    }
}
