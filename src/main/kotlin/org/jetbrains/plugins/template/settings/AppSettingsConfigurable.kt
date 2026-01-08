package org.jetbrains.plugins.template.settings

import com.intellij.openapi.options.Configurable
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

class AppSettingsConfigurable : Configurable {
    private var mySettingsComponent: AppSettingsComponent? = null

    @Nls(capitalization = Nls.Capitalization.Title)
    override fun getDisplayName(): String {
        return "Snow ApiDoc Settings"
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return mySettingsComponent?.preferredFocusedComponent
    }

    override fun createComponent(): JComponent? {
        mySettingsComponent = AppSettingsComponent()
        return mySettingsComponent?.panel
    }

    override fun isModified(): Boolean {
        val settings = AppSettingsState.instance
        var modified = mySettingsComponent?.apiBaseUrl != settings.apiBaseUrl
        modified = modified or (mySettingsComponent?.apiToken != settings.apiToken)
        return modified
    }

    override fun apply() {
        val settings = AppSettingsState.instance
        settings.apiBaseUrl = mySettingsComponent?.apiBaseUrl ?: ""
        settings.apiToken = mySettingsComponent?.apiToken ?: ""
    }

    override fun reset() {
        val settings = AppSettingsState.instance
        mySettingsComponent?.apiBaseUrl = settings.apiBaseUrl
        mySettingsComponent?.apiToken = settings.apiToken
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
    }
}
