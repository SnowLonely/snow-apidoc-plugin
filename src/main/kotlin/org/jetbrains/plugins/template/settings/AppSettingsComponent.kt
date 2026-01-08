package org.jetbrains.plugins.template.settings

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class AppSettingsComponent {
    val panel: JPanel
    private val apiBaseUrlText = JBTextField()
    private val apiTokenText = JBTextField()

    init {
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("服务器地址: "), apiBaseUrlText, 1, false)
            .addLabeledComponent(JBLabel("项目Token: "), apiTokenText, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    val preferredFocusedComponent: JComponent
        get() = apiBaseUrlText

    var apiBaseUrl: String
        get() = apiBaseUrlText.text
        set(newText) {
            apiBaseUrlText.text = newText
        }

    var apiToken: String
        get() = apiTokenText.text
        set(newText) {
            apiTokenText.text = newText
        }
}
