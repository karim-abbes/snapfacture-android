package com.snapfacture.ui.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapfacture.data.preferences.SecurityPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecurityViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SecurityPreferences,
) : ViewModel() {

    val enabled: StateFlow<Boolean> =
        prefs.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** True when the device has at least one biometric or a screen lock configured. */
    val canEnable: Boolean = BiometricManager.from(context)
        .canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    fun setEnabled(value: Boolean) {
        viewModelScope.launch { prefs.setAppLockEnabled(value) }
    }
}
