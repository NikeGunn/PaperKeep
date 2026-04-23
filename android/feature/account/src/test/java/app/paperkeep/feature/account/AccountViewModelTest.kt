package app.paperkeep.feature.account

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: AccountRepository = mockk()
    private lateinit var viewModel: AccountViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AccountViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- Password strength ----

    @Test
    fun `password strength WEAK for short password`() {
        val strength = viewModel.evaluatePasswordStrength("abc")
        assertEquals(PasswordStrength.WEAK, strength)
    }

    @Test
    fun `password strength WEAK for exactly 7 chars`() {
        val strength = viewModel.evaluatePasswordStrength("Abcdef1")
        assertEquals(PasswordStrength.WEAK, strength)
    }

    @Test
    fun `password strength MEDIUM for Password1`() {
        val strength = viewModel.evaluatePasswordStrength("Password1")
        assertEquals(PasswordStrength.MEDIUM, strength)
    }

    @Test
    fun `password strength STRONG for P@ssw0rd!1`() {
        val strength = viewModel.evaluatePasswordStrength("P@ssw0rd!1")
        assertEquals(PasswordStrength.STRONG, strength)
    }

    @Test
    fun `password strength STRONG requires uppercase + digit + symbol`() {
        val strength = viewModel.evaluatePasswordStrength("Abc123!@#")
        assertEquals(PasswordStrength.STRONG, strength)
    }

    // ---- Email validation ----

    @Test
    fun `email validation rejects notanemail`() {
        assertFalse(viewModel.isValidEmail("notanemail"))
    }

    @Test
    fun `email validation rejects email without domain`() {
        assertFalse(viewModel.isValidEmail("user@"))
    }

    @Test
    fun `email validation accepts user@example_com`() {
        assertTrue(viewModel.isValidEmail("user@example.com"))
    }

    @Test
    fun `email validation accepts complex valid email`() {
        assertTrue(viewModel.isValidEmail("user.name+tag@sub.example.org"))
    }

    // ---- Form validation (signup) ----

    @Test
    fun `signup with invalid email sets emailError`() = runTest {
        viewModel.onEmailChanged("bad-email")
        viewModel.onPasswordChanged("P@ssw0rd!1")
        viewModel.onConfirmPasswordChanged("P@ssw0rd!1")
        viewModel.signup()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.emailError)
    }

    @Test
    fun `signup with mismatched passwords sets confirmPasswordError`() = runTest {
        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("P@ssw0rd!1")
        viewModel.onConfirmPasswordChanged("different")
        viewModel.signup()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.confirmPasswordError)
    }

    @Test
    fun `signup with short password sets passwordError`() = runTest {
        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("abc")
        viewModel.onConfirmPasswordChanged("abc")
        viewModel.signup()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.passwordError)
    }

    // ---- API call: createAccount ----

    @Test
    fun `signup calls POST accounts with correct email field`() = runTest {
        val capturedRequest = slot<CreateAccountRequest>()
        coEvery { repository.createAccount(capture(capturedRequest)) } returns
            Result.success(SessionResponse("tok_abc", "2026-01-01T00:00:00Z"))

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("P@ssw0rd!1")
        viewModel.onConfirmPasswordChanged("P@ssw0rd!1")
        viewModel.signup()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.createAccount(any()) }
        assertEquals("user@example.com", capturedRequest.captured.email)
    }

    @Test
    fun `signup sets session token on success`() = runTest {
        coEvery { repository.createAccount(any()) } returns
            Result.success(SessionResponse("my_token", "2026-01-01T00:00:00Z"))

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("P@ssw0rd!1")
        viewModel.onConfirmPasswordChanged("P@ssw0rd!1")
        viewModel.signup()
        advanceUntilIdle()

        assertEquals("my_token", viewModel.uiState.value.sessionToken)
    }

    @Test
    fun `signup sets errorMessage on failure`() = runTest {
        coEvery { repository.createAccount(any()) } returns
            Result.failure(Exception("Email already exists"))

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("P@ssw0rd!1")
        viewModel.onConfirmPasswordChanged("P@ssw0rd!1")
        viewModel.signup()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.sessionToken)
    }

    // ---- API call: login ----

    @Test
    fun `login calls POST sessions with correct email`() = runTest {
        val capturedRequest = slot<LoginRequest>()
        coEvery { repository.login(capture(capturedRequest)) } returns
            Result.success(SessionResponse("login_token", "2026-01-01T00:00:00Z"))

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("P@ssw0rd!1")
        viewModel.login()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.login(any()) }
        assertEquals("user@example.com", capturedRequest.captured.email)
    }

    @Test
    fun `login sets session token on success`() = runTest {
        coEvery { repository.login(any()) } returns
            Result.success(SessionResponse("session_abc", "2026-01-01T00:00:00Z"))

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("P@ssw0rd!1")
        viewModel.login()
        advanceUntilIdle()

        assertEquals("session_abc", viewModel.uiState.value.sessionToken)
    }

    @Test
    fun `login with invalid email does not call repository`() = runTest {
        viewModel.onEmailChanged("notvalid")
        viewModel.onPasswordChanged("password")
        viewModel.login()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.login(any()) }
    }

    @Test
    fun `login with empty password does not call repository`() = runTest {
        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("")
        viewModel.login()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.login(any()) }
    }

    // ---- Loading state ----

    @Test
    fun `isLoading becomes false after successful login`() = runTest {
        coEvery { repository.login(any()) } returns
            Result.success(SessionResponse("tok", "2026-01-01T00:00:00Z"))

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("P@ssw0rd!1")
        viewModel.login()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `isLoading becomes false after failed login`() = runTest {
        coEvery { repository.login(any()) } returns Result.failure(Exception("auth failed"))

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("P@ssw0rd!1")
        viewModel.login()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
    }
}
