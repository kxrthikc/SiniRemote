package com.example.sinilinkcontrol

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("SiniSettings", Context.MODE_PRIVATE)

    var autoApplyEq: String
        get() = prefs.getString("autoApplyEq", "Disable") ?: "Disable"
        set(value) = prefs.edit().putString("autoApplyEq", value).apply()

    var defaultTheme: Int // 0=System, 1=Light, 2=Dark
        get() = prefs.getInt("defaultTheme", 0)
        set(value) = prefs.edit().putInt("defaultTheme", value).apply()

    var defaultColors: Int // 0=Material (OFF), 1=Dynamic (ON1), 2=Dynamic Full (ON2)
        get() = prefs.getInt("defaultColors", 0)
        set(value) = prefs.edit().putInt("defaultColors", value).apply()

    var defaultBackground: Int // 0=Solid, 1=Blurred
        get() = prefs.getInt("defaultBackground", 1)
        set(value) = prefs.edit().putInt("defaultBackground", value).apply()

    var timeoutDynamicTheme: Int // in seconds, 0 = Off, 7 = default
        get() = prefs.getInt("timeoutDynamicTheme", 7)
        set(value) = prefs.edit().putInt("timeoutDynamicTheme", value).apply()

    var timeoutMainControls: Int // in seconds, 0 = Off, 20 = default
        get() = prefs.getInt("timeoutMainControls", 20)
        set(value) = prefs.edit().putInt("timeoutMainControls", value).apply()

    var showLogs: Boolean
        get() = prefs.getBoolean("showLogs", false)
        set(value) = prefs.edit().putBoolean("showLogs", value).apply()

    var runInBackground: Boolean
        get() = prefs.getBoolean("runInBackground", true)
        set(value) = prefs.edit().putBoolean("runInBackground", value).apply()

    var showEqInFullScreen: Boolean
        get() = prefs.getBoolean("showEqInFullScreen", true)
        set(value) = prefs.edit().putBoolean("showEqInFullScreen", value).apply()

    var isMediaExpanded: Boolean
        get() = prefs.getBoolean("isMediaExpanded", true)
        set(value) = prefs.edit().putBoolean("isMediaExpanded", value).apply()

    var isInputCollapsed: Boolean
        get() = prefs.getBoolean("isInputCollapsed", false)
        set(value) = prefs.edit().putBoolean("isInputCollapsed", value).apply()

    var isEqVisible: Boolean
        get() = prefs.getBoolean("isEqVisible", true)
        set(value) = prefs.edit().putBoolean("isEqVisible", value).apply()
}
