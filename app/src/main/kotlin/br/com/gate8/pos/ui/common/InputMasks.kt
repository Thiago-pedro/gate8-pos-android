package br.com.gate8.pos.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Máscara CPF: 000.000.000-00 (value = só dígitos, até 11). */
object CpfVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(11)
        val formatted = buildString {
            digits.forEachIndexed { i, c ->
                append(c)
                when (i) {
                    2, 5 -> append('.')
                    8 -> append('-')
                }
            }
        }
        return TransformedText(AnnotatedString(formatted), CpfOffsetMapping(digits.length))
    }

    private class CpfOffsetMapping(private val digitCount: Int) : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            val o = offset.coerceIn(0, digitCount)
            return when {
                o <= 3 -> o
                o <= 6 -> o + 1
                o <= 9 -> o + 2
                else -> o + 3
            }
        }

        override fun transformedToOriginal(offset: Int): Int {
            val t = offset.coerceAtLeast(0)
            return when {
                t <= 3 -> t.coerceAtMost(digitCount)
                t <= 7 -> (t - 1).coerceIn(0, digitCount)
                t <= 11 -> (t - 2).coerceIn(0, digitCount)
                else -> (t - 3).coerceIn(0, digitCount)
            }
        }
    }
}

/** Máscara celular: (00) 00000-0000 (value = só dígitos, até 11). */
object PhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(11)
        val formatted = buildString {
            digits.forEachIndexed { i, c ->
                when (i) {
                    0 -> append('(')
                    2 -> append(") ")
                    7 -> append('-')
                }
                append(c)
            }
        }
        return TransformedText(AnnotatedString(formatted), PhoneOffsetMapping(digits.length))
    }

    private class PhoneOffsetMapping(private val digitCount: Int) : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            val o = offset.coerceIn(0, digitCount)
            return when {
                o == 0 -> 0
                o <= 2 -> o + 1 // (
                o <= 7 -> o + 3 // ( )␠
                else -> o + 4 // ( )␠ -
            }
        }

        override fun transformedToOriginal(offset: Int): Int {
            val t = offset.coerceAtLeast(0)
            return when {
                t <= 1 -> 0
                t <= 3 -> (t - 1).coerceIn(0, digitCount)
                t <= 4 -> 2.coerceAtMost(digitCount) // space after )
                t <= 10 -> (t - 3).coerceIn(0, digitCount)
                else -> (t - 4).coerceIn(0, digitCount)
            }
        }
    }
}
