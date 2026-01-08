package org.jetbrains.plugins.template.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.template.client.SnowApiClient
import org.jetbrains.plugins.template.model.ApiInfo
import org.jetbrains.plugins.template.model.ParameterInfo

class GenerateApiDocAction : AnAction() {
    private val snowApiClient = SnowApiClient()

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
            
            for (method in psiClass.methods) {
                if (isApiMethod(method)) {
                    val methodPath = getPathFromAnnotation(method)
                    val fullPath = (classLevelPath + methodPath).replace("//", "/")
                    val httpMethod = getHttpMethod(method)
                    val headers = getHeaders(method)
                    
                    val parameters = method.parameterList.parameters.map { param ->
                        val paramComment = getParameterComment(method, param.name)
                        ParameterInfo(param.name, param.type.presentableText, paramComment)
                    }
                    
                    val requestBody = getRequestBody(method)
                    
                    apiList.add(ApiInfo(
                        methodName = method.name,
                        httpMethod = httpMethod,
                        path = fullPath,
                        headers = headers,
                        comment = method.docComment?.let { parseDocComment(it) },
                        responseType = method.returnType?.presentableText,
                        parameters = parameters,
                        requestBody = requestBody
                    ))
                }
            }
        }
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

    private fun getHeaders(method: PsiMethod): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        
        // 从方法注解中获取 (e.g., @RequestMapping(headers = "key=value"))
        method.annotations.forEach { ann ->
            val qName = ann.qualifiedName ?: return@forEach
            if ("Mapping" in qName) {
                val headerAttr = ann.findAttributeValue("headers")
                if (headerAttr != null) {
                    headers["Mapping-Header"] = headerAttr.text.replace("\"", "")
                }
            }
        }
        
        // 从参数注解中获取 (e.g., @RequestHeader("token") String token)
        method.parameterList.parameters.forEach { param ->
            param.annotations.forEach { ann ->
                if (ann.qualifiedName?.endsWith("RequestHeader") == true) {
                    val value = ann.findAttributeValue("value") ?: ann.findAttributeValue("name")
                    val headerName = value?.text?.replace("\"", "") ?: param.name
                    headers[headerName] = "from parameter: ${param.name}"
                }
            }
        }
        
        return headers
    }

    private fun getParameterComment(method: PsiMethod, paramName: String): String? {
        val docComment = method.docComment ?: return null
        return docComment.tags
            .filter { it.name == "param" && it.valueElement?.text == paramName }
            .map { it.dataElements.joinToString(" ") { el -> el.text }.trim() }
            .firstOrNull()?.replace(paramName, "")?.trim()
    }

    private fun getRequestBody(method: PsiMethod): String? {
        val bodyParam = method.parameterList.parameters.firstOrNull { param ->
            param.annotations.any { it.qualifiedName?.endsWith("RequestBody") == true }
        }
        return bodyParam?.type?.presentableText
    }

    private fun parseDocComment(docComment: PsiDocComment): String {
        return docComment.descriptionElements
            .joinToString("") { it.text }
            .trim()
    }
}
