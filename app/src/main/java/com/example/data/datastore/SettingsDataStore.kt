package com.example.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

object PreferencesKeys {
    val IS_ENABLED = booleanPreferencesKey("is_enabled")
    val LANGUAGE = stringPreferencesKey("language")
    val SPEECH_RATE = floatPreferencesKey("speech_rate")
    val VOICE_PITCH = floatPreferencesKey("voice_pitch")
    val VOICE_NAME = stringPreferencesKey("voice_name")
    val CHIME_ENABLED = booleanPreferencesKey("chime_enabled")
    val ANNOUNCE_PAYER_NAME = booleanPreferencesKey("announce_payer_name")
    val ANNOUNCEMENT_VOLUME = intPreferencesKey("announcement_volume")
    val DEDUP_WINDOW_SECONDS = intPreferencesKey("dedup_window")
    val SMS_DETECTION_ENABLED = booleanPreferencesKey("sms_detection")
    val NOTIF_DETECTION_ENABLED = booleanPreferencesKey("notif_detection")
    val CUSTOM_PREFIX_ENABLED = booleanPreferencesKey("prefix_enabled")
    val CUSTOM_PREFIX = stringPreferencesKey("custom_prefix")
    val CUSTOM_SUFFIX_ENABLED = booleanPreferencesKey("suffix_enabled")
    val CUSTOM_SUFFIX = stringPreferencesKey("custom_suffix")
    val CUSTOM_TEMPLATES = stringPreferencesKey("custom_templates")
    val ACTIVE_TEMPLATE_INDEX = intPreferencesKey("active_template")
    val THEME = stringPreferencesKey("theme")
    val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("onboarding_done")
    val SUBSCRIPTION_STATUS = stringPreferencesKey("subscription_status")
    val SUBSCRIPTION_EXPIRY_MS = longPreferencesKey("subscription_expiry")
    val LAST_SUBSCRIPTION_CHECK_MS = longPreferencesKey("last_sub_check")
    val DISABLED_APP_PACKAGES = stringSetPreferencesKey("disabled_packages")
    val CHIME_SOUND = stringPreferencesKey("chime_sound")
    val BILINGUAL_ENABLED = booleanPreferencesKey("bilingual_enabled")
    val FLASH_ALERT_ENABLED = booleanPreferencesKey("flash_alert_enabled")
    val MIN_AMOUNT_THRESHOLD = doublePreferencesKey("min_amount_threshold")
    val SHOP_UPI_ID = stringPreferencesKey("shop_upi_id")
    val SHOP_NAME = stringPreferencesKey("shop_name")
}
