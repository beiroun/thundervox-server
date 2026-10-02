// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.exception.handler

import com.ef_softworks.thundervox_server.api.BaseApiResponse
import com.ef_softworks.thundervox_server.api.FieldErrorDto
import com.ef_softworks.thundervox_server.exception.base.ApiException
import com.ef_softworks.thundervox_server.exception.base.InsufficientPrivilegesException
import com.ef_softworks.thundervox_server.exception.base.InternalInconsistencyException
import com.ef_softworks.thundervox_server.exception.base.InterruptionException
import com.ef_softworks.thundervox_server.exception.base.NotFoundException
import com.ef_softworks.thundervox_server.exception.base.UnsupportedOperationException
import com.ef_softworks.thundervox_server.exception.base.WrongTypeException
import com.ef_softworks.thundervox_server.util.logError
import com.ef_softworks.thundervox_server.util.logWarn
import jakarta.persistence.NonUniqueResultException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * Turns every exception into a [BaseApiResponse] with the status of its category, so the console and the
 * operator's backend always get a body. Framework errors are mapped explicitly: without these handlers the
 * generic fallback would report a bad request as 500.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val ownPackagePrefix = "com.ef_softworks"

    @ExceptionHandler(WrongTypeException::class)
    fun handleWrongType(ex: WrongTypeException): ResponseEntity<BaseApiResponse<Nothing?>> {
        logWarn("Wrong type: ${ex.message}")
        return respond(ex)
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ResponseEntity<BaseApiResponse<Nothing?>> {
        logWarn("Not found: ${ex.message}")
        return respond(ex)
    }

    @ExceptionHandler(InsufficientPrivilegesException::class)
    fun handleInsufficientPrivileges(ex: InsufficientPrivilegesException): ResponseEntity<BaseApiResponse<Nothing?>> {
        logWarn("Insufficient privileges: ${ex.message}")
        return respond(ex)
    }

    @ExceptionHandler(InterruptionException::class)
    fun handleInterruption(ex: InterruptionException): ResponseEntity<BaseApiResponse<Nothing?>> {
        // The detail is for the log only; the client sees the generic localized text of the category
        logWarn("Interrupted: ${ex.message}")
        return respond(ex.statusCode, ex.localMessage, ex.localMessage)
    }

    @ExceptionHandler(InternalInconsistencyException::class)
    fun handleInternalInconsistency(ex: InternalInconsistencyException): ResponseEntity<BaseApiResponse<Nothing?>> {
        logError("Internal inconsistency: ${ex.message}", ex)
        return respond(ex)
    }

    @ExceptionHandler(UnsupportedOperationException::class)
    fun handleUnsupportedOperation(ex: UnsupportedOperationException): ResponseEntity<BaseApiResponse<Nothing?>> {
        logError("Unsupported operation: ${ex.message}")
        return respond(ex)
    }

    @ExceptionHandler(ApiException::class)
    fun handleApi(ex: ApiException): ResponseEntity<BaseApiResponse<Nothing?>> {
        logError("API error: ${ex.message}", ex)
        return respond(ex)
    }

    // ---- Spring MVC: client-side mistakes stay 4xx ----

    @ExceptionHandler(
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class,
        HttpMessageNotReadableException::class,
        MethodArgumentNotValidException::class,
        HandlerMethodValidationException::class
    )
    fun handleBadRequest(ex: Exception): ResponseEntity<BaseApiResponse<Nothing?>> {
        logWarn("Bad request: ${ex.message}")
        return respond(400, ex.message ?: ex.javaClass.simpleName, "Некорректный запрос")
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(ex: HttpRequestMethodNotSupportedException): ResponseEntity<BaseApiResponse<Nothing?>> {
        logWarn("Method not supported: ${ex.message}")
        return respond(405, ex.message ?: ex.javaClass.simpleName, "Метод не поддерживается")
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleMediaTypeNotSupported(ex: HttpMediaTypeNotSupportedException): ResponseEntity<BaseApiResponse<Nothing?>> {
        logWarn("Media type not supported: ${ex.message}")
        return respond(415, ex.message ?: ex.javaClass.simpleName, "Формат запроса не поддерживается")
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException::class)
    fun handleMediaTypeNotAcceptable(ex: HttpMediaTypeNotAcceptableException): ResponseEntity<BaseApiResponse<Nothing?>> {
        logWarn("Media type not acceptable: ${ex.message}")
        return respond(406, ex.message ?: ex.javaClass.simpleName, "Запрошенный формат ответа недоступен")
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(ex: NoResourceFoundException): ResponseEntity<BaseApiResponse<Nothing?>> {
        logWarn("No resource: ${ex.message}")
        return respond(404, ex.message ?: ex.javaClass.simpleName, "Ресурс не найден")
    }

    // ---- Persistence and the last line of defence ----

    @ExceptionHandler(NonUniqueResultException::class)
    fun handleNonUniqueResult(ex: NonUniqueResultException): ResponseEntity<BaseApiResponse<Nothing?>> {
        logError("Non-unique result: ${ex.message} at ${firstOwnFrame(ex)}", ex)
        return respond(560, ex.message ?: ex.javaClass.simpleName, "Найдено несколько записей вместо одной")
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<BaseApiResponse<Nothing?>> {
        logError("Unexpected error: ${ex.message} at ${firstOwnFrame(ex)}", ex)
        return respond(500, ex.message ?: ex.javaClass.simpleName, "Внутренняя ошибка сервера")
    }

    private fun respond(ex: ApiException): ResponseEntity<BaseApiResponse<Nothing?>> =
        respond(ex.statusCode, ex.message ?: ex.javaClass.simpleName, ex.localMessage)

    private fun respond(statusCode: Int, message: String, localizedMessage: String): ResponseEntity<BaseApiResponse<Nothing?>> =
        ResponseEntity.status(statusCode).body(BaseApiResponse.fail(FieldErrorDto(message, localizedMessage)))

    // The first frame in our own code: points at the failing line without dumping the framework stack into the log line
    private fun firstOwnFrame(ex: Throwable): String =
        ex.stackTrace.firstOrNull { it.className.startsWith(ownPackagePrefix) }?.toString() ?: "<no own frame>"
}
