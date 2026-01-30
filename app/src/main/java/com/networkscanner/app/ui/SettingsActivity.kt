package com.networkscanner.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.networkscanner.app.BuildConfig
import com.networkscanner.app.R
import com.networkscanner.app.databinding.ActivitySettingsBinding
import com.networkscanner.app.theme.ThemeManager

/**
 * Settings screen using PreferenceFragmentCompat for a native settings experience.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply theme
        ThemeManager.initialize(this)

        // Edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.settings_title)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Settings fragment containing all preferences.
     */
    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            setupPreferences()
        }

        private fun setupPreferences() {
            setupThemePreference()
            setupDynamicColorsPreference()
            setupClearCachePreference()
            setupClearHistoryPreference()
            setupVersionPreference()
            setupAboutPreference()
            setupPrivacyPreference()
        }

        private fun setupThemePreference() {
            findPreference<ListPreference>("theme_mode")?.apply {
                // Set initial value from ThemeManager
                val currentMode = ThemeManager.getThemeMode(requireContext())
                value = currentMode.value.toString()

                setOnPreferenceChangeListener { _, newValue ->
                    val modeValue = (newValue as String).toInt()
                    val mode = ThemeManager.ThemeMode.fromValue(modeValue)
                    ThemeManager.setThemeMode(requireContext(), mode)
                    true
                }
            }
        }

        private fun setupDynamicColorsPreference() {
            findPreference<SwitchPreferenceCompat>("dynamic_colors")?.apply {
                // Only show on Android 12+
                isVisible = ThemeManager.supportsDynamicColors()

                if (isVisible) {
                    isChecked = ThemeManager.isDynamicColorsEnabled(requireContext())

                    setOnPreferenceChangeListener { _, newValue ->
                        val enabled = newValue as Boolean
                        ThemeManager.setDynamicColorsEnabled(requireContext(), enabled)

                        // Show restart hint with Material 3 expressive dialog
                        showExpressiveDialog(
                            iconRes = R.drawable.ic_palette,
                            title = getString(R.string.dialog_theme_restart_title),
                            message = getString(R.string.dialog_theme_restart_message),
                            positiveText = getString(android.R.string.ok)
                        )

                        true
                    }
                }
            }
        }

        private fun setupClearCachePreference() {
            findPreference<Preference>("clear_cache")?.setOnPreferenceClickListener {
                showExpressiveConfirmDialog(
                    iconRes = R.drawable.ic_delete,
                    title = getString(R.string.dialog_clear_cache_title),
                    message = getString(R.string.dialog_clear_cache_message),
                    positiveText = getString(R.string.action_clear),
                    negativeText = getString(android.R.string.cancel),
                    onConfirm = { clearCache() }
                )
                true
            }
        }

        private fun setupClearHistoryPreference() {
            findPreference<Preference>("clear_device_history")?.setOnPreferenceClickListener {
                showExpressiveConfirmDialog(
                    iconRes = R.drawable.ic_history,
                    title = getString(R.string.dialog_clear_history_title),
                    message = getString(R.string.dialog_clear_history_message),
                    positiveText = getString(R.string.action_clear),
                    negativeText = getString(android.R.string.cancel),
                    onConfirm = { clearDeviceHistory() }
                )
                true
            }
        }

        private fun setupVersionPreference() {
            findPreference<Preference>("app_version")?.apply {
                summary = buildString {
                    append(BuildConfig.VERSION_NAME)
                    append(" (")
                    append(BuildConfig.VERSION_CODE)
                    append(")")
                    if (BuildConfig.DEBUG) {
                        append(" - Debug")
                    }
                }
            }
        }

        private fun setupAboutPreference() {
            findPreference<Preference>("about")?.setOnPreferenceClickListener {
                showExpressiveDialog(
                    iconRes = R.drawable.ic_radar,
                    title = getString(R.string.dialog_about_title),
                    message = getString(R.string.dialog_about_message),
                    positiveText = getString(android.R.string.ok)
                )
                true
            }
        }

        private fun setupPrivacyPreference() {
            findPreference<Preference>("privacy_policy")?.setOnPreferenceClickListener {
                showExpressiveDialog(
                    iconRes = R.drawable.ic_privacy,
                    title = getString(R.string.dialog_privacy_title),
                    message = getString(R.string.dialog_privacy_message),
                    positiveText = getString(android.R.string.ok)
                )
                true
            }
        }

        /**
         * Show an expressive Material 3 dialog with custom header
         */
        private fun showExpressiveDialog(
            iconRes: Int,
            title: String,
            message: String,
            positiveText: String
        ) {
            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_expressive, null)

            dialogView.findViewById<ImageView>(R.id.dialogIcon).setImageResource(iconRes)
            dialogView.findViewById<TextView>(R.id.dialogTitle).text = title
            dialogView.findViewById<TextView>(R.id.dialogMessage).text = message

            MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton(positiveText, null)
                .show()
        }

        /**
         * Show an expressive Material 3 confirmation dialog
         */
        private fun showExpressiveConfirmDialog(
            iconRes: Int,
            title: String,
            message: String,
            positiveText: String,
            negativeText: String,
            onConfirm: () -> Unit
        ) {
            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_expressive, null)

            dialogView.findViewById<ImageView>(R.id.dialogIcon).setImageResource(iconRes)
            dialogView.findViewById<TextView>(R.id.dialogTitle).text = title
            dialogView.findViewById<TextView>(R.id.dialogMessage).text = message

            MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton(positiveText) { _, _ -> onConfirm() }
                .setNegativeButton(negativeText, null)
                .show()
        }

        private fun clearCache() {
            try {
                // Clear app cache directory
                requireContext().cacheDir.deleteRecursively()
                requireContext().cacheDir.mkdirs()

                showSnackbar(getString(R.string.cache_cleared))
            } catch (e: Exception) {
                showSnackbar("Failed to clear cache: ${e.message}")
            }
        }

        private fun clearDeviceHistory() {
            try {
                // Clear shared preferences for device history
                val prefs = requireContext().getSharedPreferences("device_history", android.content.Context.MODE_PRIVATE)
                prefs.edit().clear().apply()

                // Clear any other device-related data
                val scanPrefs = requireContext().getSharedPreferences("scan_cache", android.content.Context.MODE_PRIVATE)
                scanPrefs.edit().clear().apply()

                showSnackbar(getString(R.string.history_cleared))
            } catch (e: Exception) {
                showSnackbar("Failed to clear history: ${e.message}")
            }
        }

        private fun showSnackbar(message: String) {
            view?.let {
                Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DYNAMIC_COLORS = "dynamic_colors"
        const val KEY_AUTO_SCAN = "auto_scan_on_start"
        const val KEY_SCAN_TIMEOUT = "scan_timeout"
    }
}
