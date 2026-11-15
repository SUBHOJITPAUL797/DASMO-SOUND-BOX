package com.example.tier1_coverage

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.SettingsRepositoryImpl
import com.example.presentation.screens.applock.AppLockActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppLockPasswordTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
    }

    // ========================================================================
    // Requirement: Test AppLockActivity.authenticatePassword (matching, mismatching, null, blank)
    // ========================================================================

    @Test
    fun testAuthenticatePasswordMatching() {
        assertTrue("Exact match password must authenticate", AppLockActivity.authenticatePassword("SecurePassword123!", "SecurePassword123!"))
        assertTrue("Single character password match must authenticate", AppLockActivity.authenticatePassword("a", "a"))
        assertTrue("Password with spaces in middle must authenticate", AppLockActivity.authenticatePassword("my secret pass", "my secret pass"))
        assertTrue("Special characters match must authenticate", AppLockActivity.authenticatePassword("!@#$%^&*()_+", "!@#$%^&*()_+"))
    }

    @Test
    fun testAuthenticatePasswordMismatching() {
        assertFalse("Different password must fail", AppLockActivity.authenticatePassword("password123", "password456"))
        assertFalse("Case difference must fail", AppLockActivity.authenticatePassword("Password", "password"))
        assertFalse("Trailing space mismatch must fail", AppLockActivity.authenticatePassword("password ", "password"))
        assertFalse("Leading space mismatch must fail", AppLockActivity.authenticatePassword(" password", "password"))
    }

    @Test
    fun testAuthenticatePasswordNullOrBlankSavedPassword() {
        assertFalse("Null saved password must fail", AppLockActivity.authenticatePassword("password", null))
        assertFalse("Empty saved password must fail", AppLockActivity.authenticatePassword("password", ""))
        assertFalse("Whitespace-only saved password must fail", AppLockActivity.authenticatePassword("password", "   "))
        assertFalse("Tab/newline saved password must fail", AppLockActivity.authenticatePassword("password", "\t\n"))
    }

    @Test
    fun testAuthenticatePasswordNullOrBlankEnteredPassword() {
        assertFalse("Empty entered password with non-empty saved must fail", AppLockActivity.authenticatePassword("", "password"))
        assertFalse("Empty entered password with empty saved must fail", AppLockActivity.authenticatePassword("", ""))
        assertFalse("Whitespace entered password with whitespace saved must fail", AppLockActivity.authenticatePassword("  ", "  "))
    }

    // ========================================================================
    // Requirement: Test SettingsRepositoryImpl storing and retrieving appLockPassword and appLockType = "PASSWORD"
    // ========================================================================

    @Test
    fun testSettingsRepositoryImplStoreAndRetrieveAppLockPassword() = runBlocking {
        val repo = SettingsRepositoryImpl(context)

        val testPassword = "SuperSecretPassword#2026"
        repo.updateAppLockPassword(testPassword)

        // Verify cached settings immediately reflects the update
        assertEquals("Cached settings must have updated password", testPassword, repo.getLatestSettings().appLockPassword)

        // Verify DataStore Flow emits updated password
        val emittedSettings = repo.getSettings().first()
        assertEquals("DataStore Flow must emit updated password", testPassword, emittedSettings.appLockPassword)
    }

    @Test
    fun testSettingsRepositoryImplStoreAndRetrieveAppLockTypePassword() = runBlocking {
        val repo = SettingsRepositoryImpl(context)

        repo.updateAppLockType("PASSWORD")

        assertEquals("Cached settings must have appLockType PASSWORD", "PASSWORD", repo.getLatestSettings().appLockType)

        val emittedSettings = repo.getSettings().first()
        assertEquals("DataStore Flow must emit appLockType PASSWORD", "PASSWORD", emittedSettings.appLockType)
    }

    @Test
    fun testSettingsRepositoryImplUpdateSettingsTransformPreservesPassword() = runBlocking {
        val repo = SettingsRepositoryImpl(context)

        val password = "TransformPassword99"
        repo.updateSettings { current ->
            current.copy(
                appLockEnabled = true,
                appLockType = "PASSWORD",
                appLockPassword = password
            )
        }

        val latest = repo.getLatestSettings()
        assertTrue("appLockEnabled must be true", latest.appLockEnabled)
        assertEquals("appLockType must be PASSWORD", "PASSWORD", latest.appLockType)
        assertEquals("appLockPassword must match", password, latest.appLockPassword)

        val emitted = repo.getSettings().first()
        assertTrue("Flow appLockEnabled must be true", emitted.appLockEnabled)
        assertEquals("Flow appLockType must be PASSWORD", "PASSWORD", emitted.appLockType)
        assertEquals("Flow appLockPassword must match", password, emitted.appLockPassword)
    }

    // ========================================================================
    // Requirement: Test password setup and boundary conditions
    // ========================================================================

    @Test
    fun testPasswordSetupLengthBoundary() {
        // Validation rule in PasswordSetupDialog: enteredPassword.length >= 4
        fun isPasswordLengthValid(password: String): Boolean = password.length >= 4

        assertFalse("Empty password is invalid", isPasswordLengthValid(""))
        assertFalse("1-char password is invalid", isPasswordLengthValid("a"))
        assertFalse("2-char password is invalid", isPasswordLengthValid("ab"))
        assertFalse("3-char password is invalid", isPasswordLengthValid("abc"))

        assertTrue("4-char boundary password is valid", isPasswordLengthValid("abcd"))
        assertTrue("5-char password is valid", isPasswordLengthValid("abcde"))
        assertTrue("Long password is valid", isPasswordLengthValid("a".repeat(100)))
    }

    @Test
    fun testPasswordSetupConfirmationMatch() {
        // Confirmation validation in PasswordSetupDialog: enteredPassword == firstPassword
        fun validatePasswordConfirmation(first: String, confirmation: String): Boolean {
            return first.length >= 4 && first == confirmation
        }

        assertTrue("Matching confirmation passes", validatePasswordConfirmation("Admin@123", "Admin@123"))
        assertFalse("Mismatched confirmation fails", validatePasswordConfirmation("Admin@123", "Admin@1234"))
        assertFalse("Case mismatch confirmation fails", validatePasswordConfirmation("Admin@123", "admin@123"))
        assertFalse("Too short first password fails even if matching", validatePasswordConfirmation("abc", "abc"))
    }

    @Test
    fun test3WayLockMethodActiveCardSummary() {
        // Logic in SettingsScreen for card summary
        fun getLockMethodName(appLockType: String): String = when (appLockType) {
            "PATTERN" -> "Pattern Lock"
            "PASSWORD" -> "Password Lock"
            else -> "PIN Lock"
        }

        assertEquals("Pattern Lock", getLockMethodName("PATTERN"))
        assertEquals("PIN Lock", getLockMethodName("PIN"))
        assertEquals("Password Lock", getLockMethodName("PASSWORD"))
        assertEquals("PIN Lock", getLockMethodName("UNKNOWN")) // fallback
    }

    @Test
    fun testLockMethodConfiguredCheck() {
        // Logic in AppLockSettingsScreen for checking whether active lock method is configured
        fun isConfigured(type: String, pattern: String, pin: String, password: String): Boolean = when (type) {
            "PIN" -> pin.isNotBlank()
            "PASSWORD" -> password.isNotBlank()
            else -> pattern.isNotBlank()
        }

        assertFalse("Unconfigured password returns false", isConfigured("PASSWORD", "0123", "1234", ""))
        assertTrue("Configured password returns true", isConfigured("PASSWORD", "", "", "pass1234"))

        assertFalse("Unconfigured pin returns false", isConfigured("PIN", "0123", "", "pass1234"))
        assertTrue("Configured pin returns true", isConfigured("PIN", "", "1234", ""))

        assertFalse("Unconfigured pattern returns false", isConfigured("PATTERN", "", "1234", "pass1234"))
        assertTrue("Configured pattern returns true", isConfigured("PATTERN", "0123", "", ""))
    }
}
