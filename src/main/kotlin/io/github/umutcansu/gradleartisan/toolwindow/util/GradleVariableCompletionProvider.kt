package io.github.umutcansu.gradleartisan.toolwindow.util

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.textCompletion.TextCompletionProvider
import io.github.umutcansu.gradleartisan.services.GradleTaskRepository

class GradleVariableCompletionProvider(private val taskRepository: GradleTaskRepository,) : TextCompletionProvider {
    override fun getPrefix(text: String, offset: Int): String {
        val start = text.lastIndexOf('$', offset - 1)
        if (start < 0) return ""
        val prefix = text.substring(start + 1, offset)
        return if (text[start] == '$' && (prefix.all { it.isLetterOrDigit() || it == '_' })) prefix else ""
    }

    override fun fillCompletionVariants(parameters: CompletionParameters, prefix: String, result: CompletionResultSet) {
        val docText = parameters.editor.document.text
        val offset = parameters.offset
        val before = if (offset > 0) docText[offset - 1] else ' '
        if (before == '$') {
            val matcher = result.withPrefixMatcher(prefix)
            matcher.addAllElements(
                taskRepository.getAllGradleExtProperties().keys.map { LookupElementBuilder.create(it).withIcon(MyIcons.Property) }
            )
        }
    }

    override fun acceptChar(c: Char): CharFilter.Result? =
        if (c == '$' || c.isLetterOrDigit() || c == '_') CharFilter.Result.ADD_TO_PREFIX else null

    override fun getAdvertisement(): String? = null
    override fun applyPrefixMatcher(result: CompletionResultSet, prefix: String): CompletionResultSet = result.withPrefixMatcher(prefix)

}