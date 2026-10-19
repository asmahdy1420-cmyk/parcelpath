package tech.progma.parcelpath.service

import org.springframework.stereotype.Component
import java.security.SecureRandom

/** Tracking numbers are public, so they are random (not guessable) and avoid look-alike characters. */
@Component
class TrackingNumberGenerator {
    private val random = SecureRandom()

    fun next(): String =
        buildString {
            append(PREFIX)
            repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }

    companion object {
        const val PREFIX = "PP"
        const val LENGTH = 12
        private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    }
}
