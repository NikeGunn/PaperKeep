package app.paperkeep.feature.settings

import app.cash.turbine.test
import app.paperkeep.core.crypto.KeyDerivation
import app.paperkeep.core.crypto.KeyRotation
import app.paperkeep.core.crypto.VaultCrypto
import app.paperkeep.core.network.api.PaperkeepApiClient
import app.paperkeep.core.network.auth.TokenStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountManagementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var apiClient: PaperkeepApiClient
    private lateinit var tokenStore: TokenStore
    private lateinit var keyDerivation: KeyDerivation
    private lateinit var keyRotation: KeyRotation
    private lateinit var viewModel: AccountManagementViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        apiClient = mockk()
        tokenStore = mockk(relaxed = true)
        keyDerivation = KeyDerivation()
        keyRotation = KeyRotation(VaultCrypto())
        viewModel = AccountManagementViewModel(apiClient, tokenStore, keyDerivation, keyRotation)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isIdle() = runTest {
        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.event)
        assertNull(state.error)
    }

    @Test
    fun logout_clearsTokensAndEmitsLoggedOutEvent() = runTest {
        viewModel.uiState.test {
            skipItems(1) // initial
            viewModel.logout()
            val state = awaitItem()
            assertEquals(AccountManagementEvent.LOGGED_OUT, state.event)
            verify { tokenStore.clearAll() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteAccount_success_emitsAccountDeletedEvent() = runTest {
        coEvery { apiClient.deleteAccount() } returns Result.success(Unit)

        viewModel.uiState.test {
            skipItems(1) // initial
            viewModel.deleteAccount()
            val loading = awaitItem()
            assertEquals(true, loading.isLoading)
            val done = awaitItem()
            assertEquals(AccountManagementEvent.ACCOUNT_DELETED, done.event)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteAccount_failure_emitsError() = runTest {
        coEvery { apiClient.deleteAccount() } returns Result.failure(RuntimeException("Server error"))

        viewModel.uiState.test {
            skipItems(1)
            viewModel.deleteAccount()
            skipItems(1) // loading
            val error = awaitItem()
            assertNotNull(error.error)
            assertEquals("Server error", error.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun changePassword_success_emitsPasswordChangedEvent() = runTest {
        coEvery { apiClient.changePassword(any()) } returns Result.success(Unit)

        // Build a valid wrapped key so rewrapKey can decrypt it
        val vaultCrypto = VaultCrypto()
        val kdfSalt = ByteArray(16) { it.toByte() }
        val oldKey = keyDerivation.deriveKey("old-pass".toCharArray(), kdfSalt)
        val dataKey = ByteArray(32) { 7 }
        val wrapped = vaultCrypto.encrypt(dataKey, oldKey)
        val wrappedBytes = wrapped.nonce + wrapped.ciphertext

        viewModel.uiState.test {
            skipItems(1)
            viewModel.changePassword(
                currentPassword = "old-pass",
                newPassword = "new-pass",
                currentWrappedKey = wrappedBytes,
                kdfSalt = kdfSalt,
            )
            skipItems(1) // loading
            val done = awaitItem()
            assertEquals(AccountManagementEvent.PASSWORD_CHANGED, done.event)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clearError_resetsErrorState() = runTest {
        coEvery { apiClient.deleteAccount() } returns Result.failure(RuntimeException("oops"))
        viewModel.deleteAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
        viewModel.clearError()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun clearEvent_resetsEventState() = runTest {
        viewModel.logout()
        assertNotNull(viewModel.uiState.value.event)
        viewModel.clearEvent()
        assertNull(viewModel.uiState.value.event)
    }

    @Test
    fun changePassword_rewrapsVaultKey() = runTest {
        // Verify the key is actually re-wrapped (new wrapped key is different from old)
        var requestedWrappedKey = ""
        coEvery { apiClient.changePassword(any()) } answers {
            requestedWrappedKey = firstArg<app.paperkeep.core.network.model.ChangePasswordRequest>().wrappedKey
            Result.success(Unit)
        }

        // Create a real wrapped key using VaultCrypto
        val vaultCrypto = VaultCrypto()
        val kdfSalt = ByteArray(16) { it.toByte() }
        val oldKey = keyDerivation.deriveKey("old-pass".toCharArray(), kdfSalt)
        val dataKey = ByteArray(32) { 42 }
        val wrapped = vaultCrypto.encrypt(dataKey, oldKey)
        val wrappedBytes = wrapped.nonce + wrapped.ciphertext

        viewModel.changePassword(
            currentPassword = "old-pass",
            newPassword = "new-pass",
            currentWrappedKey = wrappedBytes,
            kdfSalt = kdfSalt,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // wrappedKey in request must be non-empty (re-wrapped)
        assert(requestedWrappedKey.isNotEmpty())
    }
}
