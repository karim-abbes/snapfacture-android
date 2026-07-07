package com.snapfacture.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapfacture.data.repository.AuditVerification
import com.snapfacture.data.repository.InvoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface VerificationUi {
    data object Idle : VerificationUi
    data object Running : VerificationUi
    data object Failed : VerificationUi
    data class Done(val result: AuditVerification) : VerificationUi
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val invoiceRepo: InvoiceRepository,
) : ViewModel() {

    private val _verification = MutableStateFlow<VerificationUi>(VerificationUi.Idle)
    val verification: StateFlow<VerificationUi> = _verification.asStateFlow()

    fun verify() {
        if (_verification.value == VerificationUi.Running) return
        _verification.value = VerificationUi.Running
        viewModelScope.launch {
            _verification.value = try {
                VerificationUi.Done(invoiceRepo.verifyAuditChain())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                VerificationUi.Failed
            }
        }
    }

    fun dismissVerification() {
        _verification.value = VerificationUi.Idle
    }
}
