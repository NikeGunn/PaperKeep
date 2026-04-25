package app.paperkeep.navigation

import androidx.lifecycle.ViewModel
import app.paperkeep.core.security.LockController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Thin ViewModel used only to inject [LockController] into the nav graph composable. */
@HiltViewModel
class LockNavViewModel @Inject constructor(
    val lockController: LockController,
) : ViewModel()
