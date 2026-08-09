package com.jetgo.tv.util

/**
 * Detecta contenido "para adultos" por palabras clave en el nombre de la categoría o del
 * título, ya que Xtream Codes no entrega una bandera oficial de "es contenido adulto".
 * Se usa para ocultarlo por completo cuando el control parental está activado.
 */
object AdultContentFilter {

    private val keywords = listOf(
        "adult", "adulto", "xxx", "+18", "18+", "porn", "porno",
        "erotic", "erótic", "playboy", "brazzers",
        "hustler", "vivid", "camsoda", "18 anos", "18 años"
    )

    fun isAdult(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.lowercase()
        return keywords.any { lower.contains(it) }
    }
}
