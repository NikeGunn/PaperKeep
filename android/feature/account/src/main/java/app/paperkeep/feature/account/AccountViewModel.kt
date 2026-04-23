package app.paperkeep.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PasswordStrength { WEAK, MEDIUM, STRONG }

data class AccountUiState(
    // Shared
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val sessionToken: String? = null,
    // Sign-up only
    val confirmPassword: String = "",
    val confirmPasswordError: String? = null,
    val passwordStrength: PasswordStrength = PasswordStrength.WEAK,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val repository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    // ---------- Field updates ----------

    fun onEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(email = email, emailError = null, errorMessage = null)
    }

    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            passwordError = null,
            errorMessage = null,
            passwordStrength = evaluatePasswordStrength(password),
        )
    }

    fun onConfirmPasswordChanged(confirm: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = confirm, confirmPasswordError = null)
    }

    // ---------- Validation ----------

    fun isValidEmail(email: String): Boolean =
        EMAIL_REGEX.matches(email)

    fun evaluatePasswordStrength(password: String): PasswordStrength {
        if (password.length < 8) return PasswordStrength.WEAK
        val hasUpper = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }
        return when {
            hasUpper && hasDigit && hasSymbol -> PasswordStrength.STRONG
            (hasUpper && hasDigit) || (hasUpper && hasSymbol) || (hasDigit && hasSymbol) -> PasswordStrength.MEDIUM
            else -> PasswordStrength.MEDIUM // length >= 8 but only one category
        }
    }

    private fun validateSignup(): Boolean {
        val s = _uiState.value
        var valid = true
        if (!isValidEmail(s.email)) {
            _uiState.value = _uiState.value.copy(emailError = "Invalid email address")
            valid = false
        }
        if (s.password.length < 8) {
            _uiState.value = _uiState.value.copy(passwordError = "Password must be at least 8 characters")
            valid = false
        }
        if (s.password != s.confirmPassword) {
            _uiState.value = _uiState.value.copy(confirmPasswordError = "Passwords do not match")
            valid = false
        }
        return valid
    }

    private fun validateLogin(): Boolean {
        val s = _uiState.value
        var valid = true
        if (!isValidEmail(s.email)) {
            _uiState.value = _uiState.value.copy(emailError = "Invalid email address")
            valid = false
        }
        if (s.password.isBlank()) {
            _uiState.value = _uiState.value.copy(passwordError = "Password required")
            valid = false
        }
        return valid
    }

    // ---------- Actions ----------

    fun signup() {
        if (!validateSignup()) return
        val s = _uiState.value
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            // In a full implementation: derive keys, compute auth_hash, etc.
            // For now, pass a minimal request so the API call is exercised.
            val request = CreateAccountRequest(
                email = s.email,
                authHash = s.password.toByteArray().toHex(), // placeholder — real hash in 4B.2 integration
                authParams = mapOf("version" to "1"),
                wrappedKey = "",
                kdfSalt = "",
                kdfParams = mapOf("iterations" to "100000"),
            )
            repository.createAccount(request).fold(
                onSuccess = { session ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        sessionToken = session.token,
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Signup failed",
                    )
                },
            )
        }
    }

    fun login() {
        if (!validateLogin()) return
        val s = _uiState.value
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val request = LoginRequest(
                email = s.email,
                authHash = s.password.toByteArray().toHex(),
                authParams = mapOf("version" to "1"),
            )
            repository.login(request).fold(
                onSuccess = { session ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        sessionToken = session.token,
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Login failed",
                    )
                },
            )
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        // RFC 5322-compatible email regex — works on JVM (no android.util.Patterns needed).
        private val EMAIL_REGEX = Regex(
            "^[a-zA-Z0-9.!#\$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9]" +
                "(?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?" +
                "(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+\$",
        )
    }
}
