package org.jetbrains.plugins.template.model

data class ApiInfo(
    val methodName: String,
    val httpMethod: String,
    val path: String,
    val headers: Map<String, String> = emptyMap(),
    val responseType: String? = null,
    val comment: String? = null,
    val parameters: List<ParameterInfo> = emptyList(),
    val requestBody: String? = null
)

data class ParameterInfo(
    val name: String,
    val type: String,
    val comment: String? = null
)
