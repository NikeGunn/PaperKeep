package app.paperkeep.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AccountScreen(
    viewModel: AccountViewModel = hiltViewModel(),
    onAuthenticated: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    // Navigate away when we have a token
    if (state.sessionToken != null) {
        onAuthenticated()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Paperkeep", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(24.dp))

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Sign Up") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Log In") })
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
            SignupContent(state = state, viewModel = viewModel)
        } else {
            LoginContent(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun SignupContent(state: AccountUiState, viewModel: AccountViewModel) {
    var showPassword by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    EmailField(
        email = state.email,
        error = state.emailError,
        onValueChange = viewModel::onEmailChanged,
    )

    Spacer(modifier = Modifier.height(8.dp))

    PasswordField(
        value = state.password,
        label = "Password",
        error = state.passwordError,
        show = showPassword,
        onToggle = { showPassword = !showPassword },
        onValueChange = viewModel::onPasswordChanged,
    )

    // Password strength indicator
    val strengthText = when (state.passwordStrength) {
        PasswordStrength.WEAK -> "Weak"
        PasswordStrength.MEDIUM -> "Medium"
        PasswordStrength.STRONG -> "Strong"
    }
    val strengthColor = when (state.passwordStrength) {
        PasswordStrength.WEAK -> Color.Red
        PasswordStrength.MEDIUM -> Color(0xFFFFA000)
        PasswordStrength.STRONG -> Color.Green
    }
    if (state.password.isNotEmpty()) {
        Text(
            text = "Password strength: $strengthText",
            color = strengthColor,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    PasswordField(
        value = state.confirmPassword,
        label = "Confirm Password",
        error = state.confirmPasswordError,
        show = showConfirm,
        onToggle = { showConfirm = !showConfirm },
        onValueChange = viewModel::onConfirmPasswordChanged,
    )

    Spacer(modifier = Modifier.height(16.dp))

    ActionButton(
        text = "Create Account",
        isLoading = state.isLoading,
        onClick = viewModel::signup,
    )

    state.errorMessage?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun LoginContent(state: AccountUiState, viewModel: AccountViewModel) {
    var showPassword by remember { mutableStateOf(false) }

    EmailField(
        email = state.email,
        error = state.emailError,
        onValueChange = viewModel::onEmailChanged,
    )

    Spacer(modifier = Modifier.height(8.dp))

    PasswordField(
        value = state.password,
        label = "Password",
        error = state.passwordError,
        show = showPassword,
        onToggle = { showPassword = !showPassword },
        onValueChange = viewModel::onPasswordChanged,
    )

    Spacer(modifier = Modifier.height(16.dp))

    ActionButton(
        text = "Log In",
        isLoading = state.isLoading,
        onClick = viewModel::login,
    )

    state.errorMessage?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun EmailField(email: String, error: String?, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = email,
        onValueChange = onValueChange,
        label = { Text("Email") },
        isError = error != null,
        supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(
    value: String,
    label: String,
    error: String?,
    show: Boolean,
    onToggle: () -> Unit,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (show) "Hide password" else "Show password",
                )
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ActionButton(text: String, isLoading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp))
        } else {
            Text(text)
        }
    }
}
