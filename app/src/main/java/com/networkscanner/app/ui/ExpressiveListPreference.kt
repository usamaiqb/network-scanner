package com.networkscanner.app.ui

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference
import com.networkscanner.app.R

class ExpressiveListPreference(context: Context, attrs: AttributeSet?) : ListPreference(context, attrs) {

    var dialogIconResId: Int = 0
    var dialogMessage: String? = null

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ExpressiveListPreference)
        dialogIconResId = a.getResourceId(R.styleable.ExpressiveListPreference_dialogIcon, 0)
        dialogMessage = a.getString(R.styleable.ExpressiveListPreference_dialogMessage)
        a.recycle()
    }
}
