package com.wew.launcher.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wew.launcher.data.model.ContactAuthRequest
import com.wew.launcher.data.model.WewContact
import com.wew.launcher.data.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContactsUiState(
    val contacts: List<WewContact> = emptyList(),
    val authRequests: Map<String, String> = emptyMap(), // contactId -> status
    val isLoading: Boolean = true,
    val showAddContactSheet: Boolean = false,
    val selectedContact: WewContact? = null,
    val deviceId: String = "",
    val error: String? = null,
    val successMessage: String? = null
)

class ContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = DeviceRepository(application)
    private val prefs = application.getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        val deviceId = prefs.getString("device_id", null) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, deviceId = deviceId) }
            val contacts = repo.getContacts(deviceId)
            val requests = repo.getAuthRequests(deviceId)
            val requestMap = requests.associate { it.contactId to it.status }
            _uiState.update {
                it.copy(contacts = contacts, authRequests = requestMap, isLoading = false)
            }
        }
    }

    fun onAddContact() {
        _uiState.update { it.copy(showAddContactSheet = true) }
    }

    fun onDismissAddContact() {
        _uiState.update { it.copy(showAddContactSheet = false) }
    }

    fun onContactSelected(contact: WewContact) {
        _uiState.update { it.copy(selectedContact = contact) }
    }

    fun onDismissContactDetail() {
        _uiState.update { it.copy(selectedContact = null) }
    }

    fun createContact(name: String, phone: String?, email: String?, address: String?, notes: String?) {
        val deviceId = _uiState.value.deviceId
        viewModelScope.launch {
            val contactId = repo.createContact(deviceId, name, phone, email, address, null, notes)
            if (contactId != null) {
                repo.requestContactAuthorization(deviceId, contactId)
                _uiState.update {
                    it.copy(
                        showAddContactSheet = false,
                        successMessage = "Contact added — waiting for parent approval"
                    )
                }
                load()
            } else {
                _uiState.update { it.copy(error = "Could not save contact. Please try again.") }
            }
        }
    }

    fun requestAuthorization(contact: WewContact) {
        val deviceId = _uiState.value.deviceId
        val contactId = contact.id ?: return
        viewModelScope.launch {
            repo.requestContactAuthorization(deviceId, contactId)
            _uiState.update { it.copy(successMessage = "Authorization request sent to parent") }
            load()
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
