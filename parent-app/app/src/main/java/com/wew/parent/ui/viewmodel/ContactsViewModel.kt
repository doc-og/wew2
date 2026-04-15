package com.wew.parent.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wew.parent.data.model.Contact
import com.wew.parent.data.repository.ParentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContactsUiState(
    val contacts: List<Contact> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedContact: Contact? = null,
    val showDetail: Boolean = false,
    val isSaving: Boolean = false
)

class ContactsViewModel(
    private val deviceId: String,
    private val repo: ParentRepository = ParentRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                repo.seedParentContact(deviceId)
                val contacts = repo.getContacts(deviceId)
                _uiState.update { it.copy(contacts = contacts, isLoading = false) }
            }.onFailure { e ->
                Log.e("ContactsVM", "load failed: ${e.message}")
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ── Selection / navigation ────────────────────────────────────────────────

    fun openNew() {
        _uiState.update {
            it.copy(
                selectedContact = Contact(deviceId = deviceId, status = "approved"),
                showDetail = true
            )
        }
    }

    fun openContact(contact: Contact) {
        _uiState.update { it.copy(selectedContact = contact, showDetail = true) }
    }

    fun closeDetail() {
        _uiState.update { it.copy(showDetail = false, selectedContact = null) }
    }

    // ── Save / status ─────────────────────────────────────────────────────────

    fun saveContact(contact: Contact) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                // name = firstName (+ lastName if present) for display fallback
                val name = listOfNotNull(contact.firstName?.trim(), contact.lastName?.trim())
                    .joinToString(" ")
                    .ifBlank { contact.name }
                val toSave = contact.copy(
                    name = name,
                    isAuthorized = contact.status == "approved"
                )
                repo.upsertContact(toSave)
                load()
            }.onFailure { e ->
                Log.e("ContactsVM", "saveContact failed: ${e.message}")
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
            _uiState.update { it.copy(isSaving = false, showDetail = false, selectedContact = null) }
        }
    }

    fun setStatus(contact: Contact, status: String) {
        viewModelScope.launch {
            runCatching {
                val id = contact.id ?: return@launch
                repo.updateContactStatus(id, status)
                load()
            }.onFailure { Log.e("ContactsVM", "setStatus failed: ${it.message}") }
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            runCatching {
                val id = contact.id ?: return@launch
                repo.deleteContact(id)
                load()
            }.onFailure { Log.e("ContactsVM", "delete failed: ${it.message}") }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    // ── Factory ───────────────────────────────────────────────────────────────

    companion object {
        fun factory(deviceId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ContactsViewModel(deviceId) as T
        }
    }
}
