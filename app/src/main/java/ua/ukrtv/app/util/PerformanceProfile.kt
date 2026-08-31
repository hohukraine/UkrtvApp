package ua.ukrtv.app.util

enum class PerformanceProfile(val label: String, val description: String) {
    PERFORMANCE("Швидкість", "Мінімум анімацій, малі картки, максимальна швидкість"),
    VISUAL("Вигляд", "Максимальна якість: динамічні фони, анімації, великі картки")
}
