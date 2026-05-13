package com.resqlink.app.data.model

data class Contact(
    val id: String,
    val name: String,
    val phone: String,
    val isSelected: Boolean = true
)
