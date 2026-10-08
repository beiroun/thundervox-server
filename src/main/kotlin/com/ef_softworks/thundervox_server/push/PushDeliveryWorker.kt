// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.push

import com.ef_softworks.thundervox_server.util.logError
import com.ef_softworks.thundervox_server.util.logInfo
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Delivers live pushes off the core's request thread: the core gets its 202 before any network work starts.
 *
 * A small bounded pool. A full queue means the operator's backend is not answering and pushes are piling up - the
 * push is recorded as FAILED right away rather than waiting its turn minutes later, when the call is long over.
 */
@Component
class PushDeliveryWorker(
    private val pushDeliveryClient: PushDeliveryClient,
    private val pushDeliveryLog: PushDeliveryLog,
) {

    private val threadNumber = AtomicInteger()
    private val executor = ThreadPoolExecutor(
        THREADS, THREADS, 60L, TimeUnit.SECONDS, ArrayBlockingQueue(QUEUE_CAPACITY),
        { runnable -> Thread(runnable, "push-delivery-${threadNumber.incrementAndGet()}").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )

    /** Queues the push; false when the queue is full (already recorded as FAILED). */
    fun submit(target: PushTarget, payload: WakePushPayload): Boolean {
        try {
            executor.execute { deliverAndRecord(target, payload) }
            return true
        } catch (ex: RejectedExecutionException) {
            // Data-loss point: the callee will not be woken for this call
            logError("Push delivery queue is full ($QUEUE_CAPACITY): call_id=${payload.callId} callee=${payload.calleeNumber} not sent")
            pushDeliveryLog.record(
                PushDeliveryKind.LIVE, payload, target.url,
                PushDeliveryResult(PushDeliveryOutcome.FAILED, null, 0, 0, null, "queue_full")
            )
            return false
        }
    }

    private fun deliverAndRecord(target: PushTarget, payload: WakePushPayload) {
        val result = try {
            pushDeliveryClient.deliver(target, payload)
        } catch (ex: Exception) {
            logError("Push delivery crashed: call_id=${payload.callId}", ex)
            PushDeliveryResult(PushDeliveryOutcome.FAILED, null, 0, 0, null, (ex.message ?: ex.javaClass.simpleName).take(PushDeliveryClient.EXCERPT_LENGTH))
        }
        try {
            pushDeliveryLog.record(PushDeliveryKind.LIVE, payload, target.url, result)
        } catch (ex: Exception) {
            logError("Push delivery could not be recorded: call_id=${payload.callId} outcome=${result.outcome}", ex)
        }
        if (result.outcome == PushDeliveryOutcome.DELIVERED) {
            logInfo("Push delivered: call_id=${payload.callId} callee=${payload.calleeNumber} status=${result.httpStatus} in ${result.durationMs} ms")
        }
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }

    companion object {
        private const val THREADS = 2
        private const val QUEUE_CAPACITY = 100
    }
}
