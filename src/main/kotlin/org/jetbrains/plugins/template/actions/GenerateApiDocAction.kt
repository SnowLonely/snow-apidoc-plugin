package org.jetbrains.plugins.template.actions

import com.google.gson.Gson
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.template.client.SnowApiClient
import org.jetbrains.plugins.template.model.*

class GenerateApiDocAction : AnAction() {
    private val snowApiClient = SnowApiClient()
    private val gson = Gson()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return

        val apiList = mutableListOf<ApiInfo>()

        for (file in virtualFiles) {
            processFileOrDirectory(file, project, apiList)
        }

        if (apiList.isEmpty()) {
            Messages.showInfoMessage(project, "未发现有效的 API 接口", "Snow-ApiDoc")
            return
        }

        // 调用客户端发送到后端
        val resultMessage = snowApiClient.sendApiDocs(apiList)
        
        Messages.showInfoMessage(project, resultMessage, "Snow-ApiDoc")
    }

    private fun processFileOrDirectory(file: com.intellij.openapi.vfs.VirtualFile, project: Project, apiList: MutableList<ApiInfo>) {
        if (file.isDirectory) {
            file.children.forEach { child ->
                processFileOrDirectory(child, project, apiList)
            }
        } else {
            val psiFile = PsiManager.getInstance(project).findFile(file)
            if (psiFile is PsiJavaFile) {
                processJavaFile(psiFile, apiList)
            }
        }
    }

    private fun processJavaFile(psiFile: PsiJavaFile, apiList: MutableList<ApiInfo>) {
        val classes = psiFile.classes
        for (psiClass in classes) {
            val classLevelPath = getPathFromAnnotation(psiClass)
            val classComment = psiClass.docComment?.let { parseDocComment(it) }
            
            for (method in psiClass.methods) {
                if (isApiMethod(method)) {
                    val methodPath = getPathFromAnnotation(method)
                    val fullPath = (classLevelPath + methodPath).replace("//", "/")
                    val httpMethod = getHttpMethod(method)
                    
                    // 3. 解析 RequestBody (多层级)
                    val processedTypes = mutableSetOf<String>()
                    val requestBody = getRequestBodySchema(method, processedTypes)

                    // 1. 解析 Headers (根据是否有明确的 Json 传输标识判定是否添加 Content-Type)
                    val isExplicitJson = isExplicitJsonRequest(method) || requestBody != null
                    val headers = getHeaders(method, isExplicitJson)

                    // 2. 解析 Query 参数 (非 @RequestBody 的参数)
                    val queryParams = getQueryParams(method)

                    // 4. 解析 Response (多层级)
                    processedTypes.clear()
                    val responseParams = method.returnType?.let { parseTypeToSchema(it, processedTypes) }
                    
                    apiList.add(ApiInfo(
                        methodName = method.name,
                        httpMethod = httpMethod,
                        path = fullPath,
                        headers = headers,
                        classComment = classComment,
                        methodComment = method.docComment?.let { parseDocComment(it) },
                        queryParams = queryParams,
                        requestBody = requestBody,
                        responseParams = responseParams
                    ))
                }
            }
        }
        println(gson.toJson(apiList))
    }

    private fun isApiMethod(method: PsiMethod): Boolean {
        val annotations = method.annotations
        val apiAnnotations = listOf(
            "RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping"
        )
        return annotations.any { ann -> apiAnnotations.any { it in (ann.qualifiedName ?: "") } }
    }

    private fun getPathFromAnnotation(member: PsiModifierListOwner): String {
        val annotations = member.annotations
        for (ann in annotations) {
            val qName = ann.qualifiedName ?: continue
            if ("Mapping" in qName) {
                val value = ann.findAttributeValue("value") ?: ann.findAttributeValue("path")
                if (value != null) {
                    return value.text.replace("\"", "")
                }
            }
        }
        return ""
    }

    private fun getHttpMethod(method: PsiMethod): String {
        val annotations = method.annotations
        for (ann in annotations) {
            val qName = ann.qualifiedName ?: continue
            when {
                "GetMapping" in qName -> return "GET"
                "PostMapping" in qName -> return "POST"
                "PutMapping" in qName -> return "PUT"
                "DeleteMapping" in qName -> return "DELETE"
                "PatchMapping" in qName -> return "PATCH"
                "RequestMapping" in qName -> {
                    val methodAttr = ann.findAttributeValue("method")
                    return methodAttr?.text?.replace("RequestMethod.", "") ?: "GET/POST"
                }
            }
        }
        return "UNKNOWN"
    }

    /**
     * 判断是否显式指定了 JSON 传输 (通过 Mapping 注解的 consumes 属性)
     */
    private fun isExplicitJsonRequest(method: PsiMethod): Boolean {
        val annotations = method.annotations
        for (ann in annotations) {
            val qName = ann.qualifiedName ?: continue
            if ("Mapping" in qName) {
                val consumesAttr = ann.findAttributeValue("consumes")
                if (consumesAttr != null && "application/json" in consumesAttr.text) {
                    return true
                }
            }
        }
        return false
    }

    private fun getHeaders(method: PsiMethod, isJsonRequest: Boolean): List<HeaderInfo> {
        val headers = mutableListOf<HeaderInfo>()
        
        // 从参数注解中获取 (e.g., @RequestHeader("token") String token)
        method.parameterList.parameters.forEach { param ->
            param.annotations.forEach { ann ->
                if (ann.qualifiedName?.endsWith("RequestHeader") == true) {
                    val valueAttr = ann.findAttributeValue("value") ?: ann.findAttributeValue("name")
                    val headerName = valueAttr?.text?.replace("\"", "") ?: param.name
                    val requiredAttr = ann.findAttributeValue("required")
                    val required = if (requiredAttr?.text == "false") "0" else "1"
                    val defaultValueAttr = ann.findAttributeValue("defaultValue")
                    val example = defaultValueAttr?.text?.replace("\"", "") ?: ""
                    
                    headers.add(HeaderInfo(
                        name = headerName,
                        required = required,
                        example = example,
                        desc = getParameterComment(method, param.name) ?: ""
                    ))
                }
            }
        }
        
        // 如果是 Json 请求且没有 Content-Type，默认加一个
        if (isJsonRequest && headers.none { it.name.equalsIgnoreCase("Content-Type") }) {
            headers.add(HeaderInfo(name = "Content-Type", value = "application/json", example = "application/json"))
        }
        
        return headers
    }

    private fun getQueryParams(method: PsiMethod): List<QueryParamInfo> {
        val queryParams = mutableListOf<QueryParamInfo>()
        method.parameterList.parameters.forEach { param ->
            // 排除带有 @RequestBody 的参数
            if (param.annotations.any { it.qualifiedName?.endsWith("RequestBody") == true }) {
                return@forEach
            }
            // 排除带有 @PathVariable 的参数
            if (param.annotations.any { it.qualifiedName?.endsWith("PathVariable") == true }) {
                return@forEach
            }
            // 排除 HttpServletRequest 等特殊类型
            val typeName = param.type.presentableText
            if (typeName == "HttpServletRequest" || typeName == "HttpServletResponse" || typeName == "BindingResult") {
                return@forEach
            }

            val ann = param.getAnnotation("org.springframework.web.bind.annotation.RequestParam")
            val name = ann?.findAttributeValue("value")?.text?.replace("\"", "")
                ?: ann?.findAttributeValue("name")?.text?.replace("\"", "")
                ?: param.name
            
            val requiredAttr = ann?.findAttributeValue("required")
            val required = if (requiredAttr?.text == "false") "0" else "1"
            
            val defaultValueAttr = ann?.findAttributeValue("defaultValue")
            val example = defaultValueAttr?.text?.replace("\"", "") ?: ""

            queryParams.add(QueryParamInfo(
                name = name,
                required = required,
                example = example,
                desc = getParameterComment(method, param.name) ?: ""
            ))
        }
        return queryParams
    }

    private fun getRequestBodySchema(method: PsiMethod, processedTypes: MutableSet<String>): SchemaNode? {
        val bodyParam = method.parameterList.parameters.firstOrNull { param ->
            param.annotations.any { it.qualifiedName?.endsWith("RequestBody") == true }
        }
        return bodyParam?.let { parseTypeToSchema(it.type, processedTypes) }
    }

    private fun parseTypeToSchema(psiType: PsiType, processedTypes: MutableSet<String>): SchemaNode {
        val typeText = psiType.presentableText
        
        return when {
            // 基本类型
            isPrimitive(psiType) -> SchemaNode(type = mapPrimitiveType(psiType))
            
            // 数组或集合
            psiType is PsiArrayType -> {
                SchemaNode(type = "array", items = parseTypeToSchema(psiType.componentType, processedTypes))
            }
            isCollection(psiType) -> {
                val iterableType = com.intellij.psi.util.PsiUtil.extractIterableTypeParameter(psiType, false)
                SchemaNode(type = "array", items = iterableType?.let { parseTypeToSchema(it, processedTypes) } ?: SchemaNode(type = "object"))
            }
            
            // 对象类型 (递归解析)
            psiType is PsiClassType -> {
                val psiClass = psiType.resolve()
                val qualifiedName = psiClass?.qualifiedName
                
                if (psiClass != null && qualifiedName != null && !qualifiedName.startsWith("java.lang")) {
                    // 防止递归死循环
                    if (processedTypes.contains(qualifiedName)) {
                        return SchemaNode(type = "object", description = "Recursive reference to $qualifiedName")
                    }
                    processedTypes.add(qualifiedName)
                    
                    val properties = mutableMapOf<String, SchemaNode>()
                    psiClass.allFields.forEach { field ->
                        if (!field.hasModifierProperty(PsiModifier.STATIC)) {
                            var fieldSchema = parseTypeToSchema(field.type, processedTypes)
                            
                            // 提取字段注释作为 description
                            val description = field.docComment?.let { parseDocComment(it) }
                            
                            // 尝试提取字段的默认值作为 example
                            val example = field.initializer?.text?.replace("\"", "") ?: ""
                            
                            properties[field.name] = fieldSchema.copy(
                                description = description,
                                example = example
                            )
                        }
                    }
                    SchemaNode(type = "object", properties = properties)
                } else {
                    SchemaNode(type = "object")
                }
            }
            else -> SchemaNode(type = "object")
        }
    }

    private fun isPrimitive(psiType: PsiType): Boolean {
        val text = psiType.presentableText.lowercase()
        return text in listOf("string", "int", "integer", "long", "boolean", "double", "float", "void") || psiType is PsiPrimitiveType
    }

    private fun mapPrimitiveType(psiType: PsiType): String {
        val text = psiType.presentableText.lowercase()
        return when {
            text.contains("int") || text.contains("long") -> "integer"
            text.contains("boolean") -> "boolean"
            text.contains("double") || text.contains("float") -> "number"
            else -> "string"
        }
    }

    private fun isCollection(psiType: PsiType): Boolean {
        val qualifiedName = (psiType as? PsiClassType)?.resolve()?.qualifiedName ?: ""
        return qualifiedName.startsWith("java.util.List") || 
               qualifiedName.startsWith("java.util.Set") || 
               qualifiedName.startsWith("java.util.Collection")
    }

    private fun String.equalsIgnoreCase(other: String): Boolean = this.equals(other, ignoreCase = true)

    /**
     * 获取参数的注释 (通过解析 @param 标签)
     */
    private fun getParameterComment(method: PsiMethod, paramName: String): String? {
        val docComment = method.docComment ?: return null
        return docComment.tags
            .filter { it.name == "param" && it.valueElement?.text == paramName }
            .map { it.dataElements.joinToString(" ") { el -> el.text }.trim() }
            .firstOrNull()?.replace(paramName, "")?.trim()
    }

    /**
     * 解析 Javadoc 注释的描述部分（去掉标签）
     */
    private fun parseDocComment(docComment: PsiDocComment): String {
        return docComment.descriptionElements
            .joinToString("") { it.text }
            .trim()
    }
}
