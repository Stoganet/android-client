package com.stoganet.core.data.home

import com.stoganet.core.api.model.HomeResponse
import com.stoganet.core.data.net.StoganetApi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRepositoryTest {

    private val api = mockk<StoganetApi>()
    private val repository = HomeRepository(api)

    @Test
    fun `getHome returns success when api succeeds`() = runTest {
        coEvery { api.getHome() } returns HomeResponse(sections = emptyList())

        val result = repository.getHome()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `getHome returns failure when api throws`() = runTest {
        coEvery { api.getHome() } throws RuntimeException("network error")

        val result = repository.getHome()

        assertTrue(result.isFailure)
    }
}
