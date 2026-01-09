package org.jetbrains.plugins.template.model

/**
 * 接口信息封装类
 *
 * 用于存储从代码中解析出来的单个 API 接口的详细信息。
 */
data class ApiInfo(
    /** 方法名称 */
    val methodName: String,
    
    /** HTTP 请求方法 (GET, POST, PUT, DELETE 等) */
    val httpMethod: String,
    
    /** 接口请求路径 */
    val path: String,
    
    /** 请求头信息列表 */
    val headers: List<HeaderInfo> = emptyList(),
    
    /** 响应参数 (支持多层级结构) */
    val responseParams: SchemaNode? = null,
    
    /** 类（Controller文件）的注释说明 */
    val classComment: String? = null,
    
    /** 方法（接口）的注释说明 */
    val methodComment: String? = null,
    
    /** URL 路径请求参数列表 (Query Parameters) */
    val queryParams: List<QueryParamInfo> = emptyList(),
    
    /** 请求体结构 (Body, 支持多层级结构) */
    val requestBody: SchemaNode? = null
)

/**
 * 请求头信息封装类
 */
data class HeaderInfo(
    /** Header的Key */
    val name: String,
    /** Header的value */
    val value: String = "",
    /** 是否必须，0-非必须，1-必须 */
    val required: String = "1",
    /** 示例值 */
    val example: String = "",
    /** 说明 */
    val desc: String = ""
)

/**
 * URL 请求参数封装类 (Query Param)
 */
data class QueryParamInfo(
    /** 字段名 */
    val name: String,
    /** 是否必须。0-非必须；1-必须 */
    val required: String = "0",
    /** 示例、默认值 */
    val example: String = "",
    /** 注释，说明 */
    val desc: String = ""
)

/**
 * 参数结构节点类 (支持递归多层级，用于 JSON Body 和 Response)
 */
data class SchemaNode(
    /** 类型: object, array, string, integer, boolean, number 等 */
    val type: String,
    /** 说明，描述 */
    val description: String? = null,
    /** 子类属性 (当 type 为 object 时) */
    val properties: Map<String, SchemaNode>? = null,
    /** 数组元素类型 (当 type 为 array 时) */
    val items: SchemaNode? = null,
    /** 示例值 */
    val example: String = ""
)
