package org.jetbrains.plugins.template.client

import com.google.gson.Gson
import org.jetbrains.plugins.template.model.ApiInfo
import org.jetbrains.plugins.template.settings.AppSettingsState
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Snow-ApiDoc Client
 * 
 * 此类负责将解析后的 API 信息发送到后端服务。
 * 如果您需要适配自己的服务端接口，请着重修改 [sendApiDocs] 方法。
 */
class SnowApiClient {

    private val httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .build()

    private val gson = Gson()

    /**
     * 发送 API 文档到服务端
     * 
     * @param apiList 解析出的 API 列表
     * @return 发送结果消息
     */
    fun sendApiDocs(apiList: List<ApiInfo>): String {
        val settings = AppSettingsState.instance
        val baseUrl = settings.apiBaseUrl.removeSuffix("/")
        val token = settings.apiToken

        if (baseUrl.isBlank()) {
            return "错误: 请先在设置中配置 Snow-ApiDoc 服务端地址"
        }
        
        val targetUrl = "$baseUrl/tool/api/interface"
        
        try {
            // 将对象序列化为 JSON 字符串
            val jsonBody = gson.toJson(apiList)
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                // 设置请求头为 JSON 格式
                .header("Content-Type", "application/json")
                .header("X-Project-Token", token) 
                // 使用 POST 方法发送 JSON Body
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            return if (response.statusCode() in 200..299) {
                "成功: 已将 ${apiList.size} 条接口同步至 Snow-ApiDoc 服务端"
            } else {
                "失败: 服务端返回状态码 ${response.statusCode()}, 响应内容: ${response.body()}"
            }
        } catch (e: Exception) {
            return "发送失败: ${e.message}"
        }
    }
}
