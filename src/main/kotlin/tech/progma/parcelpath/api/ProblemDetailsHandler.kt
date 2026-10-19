package tech.progma.parcelpath.api

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import tech.progma.parcelpath.service.IdempotencyKeyReusedException
import tech.progma.parcelpath.service.InvalidTransitionException
import tech.progma.parcelpath.service.ShipmentNotFoundException
import java.net.URI

/** Every error leaves the API as RFC 7807 `application/problem+json`. */
@RestControllerAdvice
class ProblemDetailsHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(ShipmentNotFoundException::class)
    fun notFound(e: ShipmentNotFoundException) =
        problem(HttpStatus.NOT_FOUND, "shipment-not-found", "Shipment not found", e.message)

    @ExceptionHandler(InvalidTransitionException::class)
    fun invalidTransition(e: InvalidTransitionException) =
        problem(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-transition", "Invalid state transition", e.message).apply {
            setProperty("currentStatus", e.rejection.from)
            setProperty("event", e.rejection.event)
        }

    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun versionConflict(e: ObjectOptimisticLockingFailureException) =
        problem(
            HttpStatus.CONFLICT,
            "version-conflict",
            "Concurrent modification",
            "the shipment was changed by another request; reload it and retry",
        )

    @ExceptionHandler(IdempotencyKeyReusedException::class)
    fun keyReused(e: IdempotencyKeyReusedException) =
        problem(HttpStatus.UNPROCESSABLE_ENTITY, "idempotency-key-reused", "Idempotency key reused", e.message)

    @ExceptionHandler(IllegalArgumentException::class)
    fun badArgument(e: IllegalArgumentException) =
        problem(HttpStatus.BAD_REQUEST, "bad-request", "Bad request", e.message)

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val body =
            problem(
                HttpStatus.BAD_REQUEST,
                "validation-failed",
                "Validation failed",
                "the request body has invalid fields",
            ).apply {
                setProperty(
                    "errors",
                    ex.bindingResult.fieldErrors.map {
                        mapOf(
                            "field" to it.field,
                            "message" to (it.defaultMessage ?: "is invalid"),
                        )
                    },
                )
            }
        return ResponseEntity.badRequest().body(body)
    }

    override fun handleHandlerMethodValidationException(
        ex: HandlerMethodValidationException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val body =
            problem(
                HttpStatus.BAD_REQUEST,
                "validation-failed",
                "Validation failed",
                "the request has invalid parameters",
            ).apply {
                setProperty(
                    "errors",
                    ex.parameterValidationResults.flatMap { result ->
                        result.resolvableErrors.map {
                            mapOf(
                                "field" to result.methodParameter.parameterName,
                                "message" to (it.defaultMessage ?: "is invalid"),
                            )
                        }
                    },
                )
            }
        return ResponseEntity.badRequest().body(body)
    }

    private fun problem(
        status: HttpStatus,
        type: String,
        title: String,
        detail: String?,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail ?: title).apply {
            this.type = URI.create("/problems/$type")
            this.title = title
        }
}
