package com.networkscanner.app.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreferenceDialogFragmentCompat
import com.networkscanner.app.R

class ExpressivePreferenceDialogFragment : ListPreferenceDialogFragmentCompat() {

    companion object {
        fun newInstance(key: String): ExpressivePreferenceDialogFragment {
            val fragment = ExpressivePreferenceDialogFragment()
            val b = Bundle(1)
            b.putString(ARG_KEY, key)
            fragment.arguments = b
            return fragment
        }
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)

        val preference = preference
        if (preference is ExpressiveListPreference) {
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_expressive, null)

            val iconView = view.findViewById<ImageView>(R.id.dialogIcon)
            if (preference.dialogIconResId != 0) {
                iconView.setImageResource(preference.dialogIconResId)
            } else {
                iconView.visibility = View.GONE
            }

            val titleView = view.findViewById<TextView>(R.id.dialogTitle)
            titleView.text = preference.dialogTitle

            val messageView = view.findViewById<TextView>(R.id.dialogMessage)
            if (!preference.dialogMessage.isNullOrEmpty()) {
                messageView.text = preference.dialogMessage
            } else {
                messageView.visibility = View.GONE
            }

            // Set the custom view as the title (header)
            // The list content will automatically be added by the super class to the content area
            builder.setCustomTitle(view)
            
            // Clear default title and message to avoid duplicates
            builder.setTitle(null)
            builder.setMessage(null)
        }
    }
}
