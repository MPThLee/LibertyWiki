// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.domain

import java.text.Normalizer

@JvmInline
value class PageTitle private constructor(val value: String) {
    val key: String get() = value.replace(' ', '_')

    companion object {
        private val separators = Regex("[ _]+")
        private val encoded = Regex("%[0-9a-fA-F]{2}|&(?:#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);")
        private val forbidden = "\\/:?#[]{}<>|"

        fun parse(input: String): PageTitle? {
            if (input.any { it.isISOControl() || it in forbidden } || !validUnicode(input)) return null
            val spaced = separators.replace(nfc(input), " ").trim(' ')
            if (spaced.isEmpty() || spaced == "." || spaced == ".." || "~~~" in spaced || encoded.containsMatchIn(spaced)) {
                return null
            }
            val first = spaced.codePointAt(0)
            val upper = String(Character.toChars(Character.toUpperCase(first)))
            val title = nfc(upper + spaced.substring(Character.charCount(first)))
            return if (title.toByteArray().size <= 255) PageTitle(title) else null
        }

        private fun nfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
    }
}

fun validUnicode(text: String): Boolean = text.codePoints().noneMatch { it in 0xD800..0xDFFF || it == 0 }
