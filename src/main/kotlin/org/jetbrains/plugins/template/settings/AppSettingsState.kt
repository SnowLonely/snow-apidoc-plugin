package org.jetbrains.plugins.template.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "org.jetbrains.plugins.template.settings.AppSettingsState",
    storages = [Storage("SnowApiDocSettings.xml")]
)
class AppSettingsState : PersistentStateComponent<AppSettingsState> {
    var apiBaseUrl: String = "http://localhost:8080"
    var apiToken: String = ""

    override fun getState(): AppSettingsState = this

    override fun loadState(state: AppSettingsState) {
        this.apiBaseUrl = state.apiBaseUrl
        this.apiToken = state.apiToken
    }

    companion object {
        val instance: AppSettingsState
            get() = ApplicationManager.getApplication().getService(AppSettingsState::class.java)
    }
}
