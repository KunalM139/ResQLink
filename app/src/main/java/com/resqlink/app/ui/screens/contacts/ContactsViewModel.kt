package com.resqlink.app.ui.screens.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resqlink.app.data.model.Contact
import com.resqlink.app.data.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ContactsUiState(
    val contacts: List<Contact> = emptyList(),
    val showAddDialog: Boolean = false,
    val editingContact: Contact? = null
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactRepository: ContactRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    init {
        loadContacts()
    }

    private fun loadContacts() {
        viewModelScope.launch {
            contactRepository.getAllContacts().collect { contacts ->
                _uiState.update { it.copy(contacts = contacts) }
            }
        }
    }

    fun addContact(name: String, phone: String) {
        viewModelScope.launch {
            val contact = Contact(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                phone = phone.trim(),
                isSelected = true
            )
            contactRepository.addContact(contact)
            _uiState.update { it.copy(showAddDialog = false) }
        }
    }

    fun addContacts(phoneContacts: List<PhoneContact>) {
        viewModelScope.launch {
            phoneContacts.forEach { pc ->
                val contact = Contact(
                    id = UUID.randomUUID().toString(),
                    name = pc.name.trim(),
                    phone = pc.phone.trim(),
                    isSelected = true
                )
                contactRepository.addContact(contact)
            }
        }
    }

    fun toggleContactSelection(contact: Contact) {
        viewModelScope.launch {
            contactRepository.updateContact(contact.copy(isSelected = !contact.isSelected))
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            contactRepository.deleteContact(contact)
        }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true) }
    }

    fun hideAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }
}
