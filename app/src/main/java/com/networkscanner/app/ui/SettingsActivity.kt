package com.networkscanner.app.ui

import android.content.Intent
import android.net.Uri
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
import com.google.android.material.button.MaterialButton
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
            setupAutoScanPreference()
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
                    
                    // Recreate activity to apply theme immediately
                    requireActivity().recreate()
                    true
                }
            }
        }


        private fun setupAutoScanPreference() {
            findPreference<SwitchPreferenceCompat>("auto_scan_on_start")?.apply {
                // Already handled by PreferenceFragmentCompat, but we can add logic here if needed
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
                showAboutDialog()
                true
            }
        }

        private fun showAboutDialog() {
            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_about, null)

            dialogView.findViewById<ImageView>(R.id.dialogIcon).setImageResource(R.drawable.ic_radar)
            dialogView.findViewById<TextView>(R.id.dialogTitle).text = getString(R.string.dialog_about_title)
            dialogView.findViewById<TextView>(R.id.dialogMessage).text = getString(R.string.dialog_about_message)

            // Setup GitHub button
            dialogView.findViewById<MaterialButton>(R.id.githubButton).setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.github_url)))
                startActivity(intent)
            }

            MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton(getString(android.R.string.ok), null)
                .show()
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





        private fun showSnackbar(message: String) {
            view?.let {
                Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show()
            }
        }

        override fun onDisplayPreferenceDialog(preference: Preference) {
            if (preference is ExpressiveListPreference) {
                val dialogFragment = ExpressivePreferenceDialogFragment.newInstance(preference.key)
                dialogFragment.setTargetFragment(this, 0)
                dialogFragment.show(parentFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
            } else {
                super.onDisplayPreferenceDialog(preference)
            }
        }
    }

    companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_AUTO_SCAN = "auto_scan_on_start"

    }
}
