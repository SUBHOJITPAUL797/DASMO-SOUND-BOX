package com.example.util

object AdminAuthManager {
    const val ADMIN_EMAIL = "subhojitpaul26042004@gmail.com"

    fun isAdmin(email: String?): Boolean {
        if (email.isNullOrBlank()) return true // Default active app owner email
        return email.trim().equals(ADMIN_EMAIL, ignoreCase = true)
    }
}
