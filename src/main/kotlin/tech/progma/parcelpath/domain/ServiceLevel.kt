package tech.progma.parcelpath.domain

enum class ServiceLevel(
    val domesticTransitDays: Int,
) {
    STANDARD(domesticTransitDays = 4),
    EXPRESS(domesticTransitDays = 1),
}
