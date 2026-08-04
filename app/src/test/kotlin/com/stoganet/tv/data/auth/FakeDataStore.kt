package com.stoganet.tv.data.auth

import androidx.datastore.core.DataStore
import com.stoganet.tv.proto.Tokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeDataStore(initial: Tokens = Tokens.getDefaultInstance()) : DataStore<Tokens> {
    private val flow = MutableStateFlow(initial)
    override val data: Flow<Tokens> = flow
    override suspend fun updateData(transform: suspend (t: Tokens) -> Tokens): Tokens {
        val new = transform(flow.value)
        flow.value = new
        return new
    }
}
