package com.resqlink.app.data.repository

import com.resqlink.app.data.local.dao.ContactDao
import com.resqlink.app.data.local.entity.ContactEntity
import com.resqlink.app.data.model.Contact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    private val contactDao: ContactDao
) {

    fun getAllContacts(): Flow<List<Contact>> {
        return contactDao.getAllContacts().map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun getSelectedContacts(): List<Contact> {
        return contactDao.getSelectedContacts().map { it.toModel() }
    }

    suspend fun addContact(contact: Contact) {
        contactDao.insert(contact.toEntity())
    }

    suspend fun updateContact(contact: Contact) {
        contactDao.update(contact.toEntity())
    }

    suspend fun deleteContact(contact: Contact) {
        contactDao.delete(contact.toEntity())
    }

    private fun ContactEntity.toModel() = Contact(
        id = id, name = name, phone = phone, isSelected = isSelected
    )

    private fun Contact.toEntity() = ContactEntity(
        id = id, name = name, phone = phone, isSelected = isSelected
    )
}
