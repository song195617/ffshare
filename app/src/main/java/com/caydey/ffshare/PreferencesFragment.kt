package com.caydey.ffshare

import android.os.Bundle
import androidx.preference.*

class PreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        dynamicallyShowCustomName()
        dynamicallyAddCustomParamTooltips()
    }
    private fun dynamicallyAddCustomParamTooltips() {
        val customParamKeys = arrayOf("pref_custom_video_params", "pref_custom_audio_params", "pref_custom_image_params")
        for (customParamKey in customParamKeys) {
            val element = findPreference<EditTextPreference>(customParamKey)
            element?.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
        }
    }
    private fun dynamicallyShowCustomName() {
        val customMediaNamePreference = findPreference<EditTextPreference>("pref_compressed_media_custom_name")
        val prefixPreference = findPreference<EditTextPreference>("pref_original_name_prefix")
        val suffixPreference = findPreference<EditTextPreference>("pref_original_name_suffix")
        val compressedMediaNamePreference = findPreference<ListPreference>("pref_compressed_media_name")

        fun updateVisibility(value: String?) {
            customMediaNamePreference?.isVisible = (value == "CUSTOM")
            prefixPreference?.isVisible = (value == "ORIGINAL")
            suffixPreference?.isVisible = (value == "ORIGINAL")
        }

        compressedMediaNamePreference?.setOnPreferenceChangeListener { _, value ->
            updateVisibility(value as? String)
            true
        }
        // trigger update for initial load
        updateVisibility(compressedMediaNamePreference?.value)
    }
}