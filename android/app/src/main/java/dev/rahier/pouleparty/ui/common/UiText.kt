package dev.rahier.pouleparty.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Text a view model hands to the screen: a resource to translate, or user content kept as typed. */
sealed interface UiText {
    data class Resource(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Verbatim(val value: String) : UiText

    fun resolve(context: Context): String = when (this) {
        is Verbatim -> value
        is Resource ->
            if (args.isEmpty()) context.getString(id)
            else context.getString(id, *args.map { if (it is UiText) it.resolve(context) else it }.toTypedArray())
    }
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Resource(id, args.toList())

@Composable
fun UiText.asString(): String = resolve(LocalContext.current)
