package com.ohmybattery.invoicing.ui.invoices.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmybattery.invoicing.core.money.Money
import com.ohmybattery.invoicing.core.pdf.InvoicePdfGenerator
import com.ohmybattery.invoicing.data.local.entity.ProductEntity
import com.ohmybattery.invoicing.data.local.entity.ClientEntity
import com.ohmybattery.invoicing.data.local.entity.PaymentMethod
import com.ohmybattery.invoicing.data.preferences.CountryPreferences
import com.ohmybattery.invoicing.data.repository.ProductRepository
import com.ohmybattery.invoicing.data.repository.ClientRepository
import com.ohmybattery.invoicing.data.repository.CompanyRepository
import com.ohmybattery.invoicing.data.repository.DraftLine
import com.ohmybattery.invoicing.data.repository.InvoiceRepository
import com.ohmybattery.invoicing.data.repository.IssueInvoiceInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CartLine(
    val product: ProductEntity,
    val quantity: Int,
)

data class CreateUiState(
    val clientName: String = "",
    val clientPhone: String = "",
    val clientEmail: String = "",
    val clientAddress: String = "",
    val vehicleModel: String = "",
    val vehicleRegistration: String = "",
    val comment: String = "",
    val matchingClients: List<ClientEntity> = emptyList(),
    val selectedClient: ClientEntity? = null,
    val cart: List<CartLine> = emptyList(),
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val taxOptedOut: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val totalTtcCents: Long get() = cart.sumOf { it.product.priceTtcCents * it.quantity }
    val totalHtCents: Long get() =
        if (taxOptedOut) totalTtcCents
        else cart.sumOf {
            val ht = Money.htFromTtc(it.product.priceTtcCents, it.product.vatRatePermille)
            ht * it.quantity
        }
    val totalVatCents: Long get() = if (taxOptedOut) 0L else totalTtcCents - totalHtCents

    val hasInstallLine: Boolean get() = cart.any { it.product.withInstall }

    val canIssue: Boolean get() {
        if (isSaving) return false
        if (cart.isEmpty()) return false
        if (clientName.isBlank() && selectedClient == null) return false
        if (hasInstallLine) {
            if (clientPhone.isBlank()) return false
            if (clientAddress.isBlank()) return false
        }
        return true
    }
}

@HiltViewModel
class CreateInvoiceViewModel @Inject constructor(
    private val clientRepo: ClientRepository,
    productRepo: ProductRepository,
    private val companyRepo: CompanyRepository,
    private val invoiceRepo: InvoiceRepository,
    private val pdfGenerator: InvoicePdfGenerator,
    private val countryPrefs: CountryPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateUiState())
    val state: StateFlow<CreateUiState> = _state.asStateFlow()

    val catalog: StateFlow<List<ProductEntity>> =
        productRepo.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            countryPrefs.flow.collect { settings ->
                _state.update { it.copy(taxOptedOut = settings.taxOptedOut) }
            }
        }
    }

    fun onClientNameChange(name: String) {
        _state.update { it.copy(clientName = name, selectedClient = null) }
        viewModelScope.launch {
            _state.update { it.copy(matchingClients = clientRepo.search(name)) }
        }
    }

    fun selectClient(c: ClientEntity) {
        _state.update {
            it.copy(
                selectedClient = c,
                clientName = c.name,
                clientPhone = c.phone.orEmpty(),
                clientEmail = c.email.orEmpty(),
                clientAddress = c.addressLine.orEmpty(),
                matchingClients = emptyList(),
            )
        }
    }

    fun onPhoneChange(phone: String) = _state.update { it.copy(clientPhone = phone) }
    fun onEmailChange(email: String) = _state.update { it.copy(clientEmail = email.trim()) }
    fun onAddressChange(addr: String) = _state.update { it.copy(clientAddress = addr) }
    fun onVehicleModelChange(s: String) = _state.update { it.copy(vehicleModel = s) }
    fun onVehicleRegistrationChange(s: String) =
        _state.update { it.copy(vehicleRegistration = s.uppercase()) }
    fun onCommentChange(s: String) = _state.update { it.copy(comment = s) }

    fun addProduct(b: ProductEntity) {
        _state.update { st ->
            val idx = st.cart.indexOfFirst { it.product.id == b.id }
            val newCart = if (idx >= 0) {
                st.cart.toMutableList().also { it[idx] = it[idx].copy(quantity = it[idx].quantity + 1) }
            } else st.cart + CartLine(b, 1)
            st.copy(cart = newCart)
        }
    }

    fun decrement(b: ProductEntity) {
        _state.update { st ->
            val idx = st.cart.indexOfFirst { it.product.id == b.id }
            if (idx < 0) return@update st
            val current = st.cart[idx]
            val newCart = if (current.quantity <= 1) {
                st.cart.toMutableList().also { it.removeAt(idx) }
            } else {
                st.cart.toMutableList().also { it[idx] = current.copy(quantity = current.quantity - 1) }
            }
            st.copy(cart = newCart)
        }
    }

    fun setPaymentMethod(m: PaymentMethod) {
        _state.update { it.copy(paymentMethod = m) }
    }

    fun issue(onIssued: (Long) -> Unit) {
        val st = _state.value
        if (!st.canIssue) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val clientId = clientRepo.upsertByName(
                    name = st.selectedClient?.name ?: st.clientName,
                    phone = st.clientPhone,
                    email = st.clientEmail,
                    addressLine = st.clientAddress,
                )

                val now = System.currentTimeMillis()
                val lines = st.cart.map { line ->
                    DraftLine(
                        description = line.product.label,
                        extraNote = if (line.product.withInstall) {
                            line.product.serviceNote?.takeIf { it.isNotBlank() }
                        } else null,
                        quantity = line.quantity,
                        unitPriceTtcCents = line.product.priceTtcCents,
                        vatRatePermille = line.product.vatRatePermille,
                    )
                }
                val invoiceId = invoiceRepo.issue(
                    IssueInvoiceInput(
                        clientId = clientId,
                        lines = lines,
                        paymentMethod = st.paymentMethod,
                        issueDateMillis = now,
                        deliveryDateMillis = if (lines.any { it.extraNote != null }) now else null,
                        issuerName = "Jubs",
                        vehicleModel = st.vehicleModel.ifBlank { null },
                        vehicleRegistration = st.vehicleRegistration.ifBlank { null },
                        comment = st.comment.ifBlank { null },
                        taxOptedOut = st.taxOptedOut,
                    )
                )
                val company = companyRepo.get() ?: error("Company missing")
                val details = invoiceRepo.get(invoiceId) ?: error("Invoice missing")
                val countrySettings = countryPrefs.flow.first()
                val file = pdfGenerator.generate(
                    invoice = details,
                    company = company,
                    country = countrySettings.profile,
                    taxOptedOut = countrySettings.taxOptedOut,
                )
                invoiceRepo.attachPdf(invoiceId, file.absolutePath)
                _state.update { it.copy(isSaving = false) }
                onIssued(invoiceId)
            } catch (t: Throwable) {
                _state.update { it.copy(isSaving = false, error = t.message ?: "Erreur") }
            }
        }
    }
}
