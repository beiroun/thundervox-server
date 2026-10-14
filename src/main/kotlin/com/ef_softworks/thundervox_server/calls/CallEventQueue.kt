// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.calls

import com.ef_softworks.thundervox_server.util.logError
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Takes call events off the core's request thread: the core's SIP worker waits for the HTTP answer, so the journal
 * write happens here, afterwards. One thread on purpose - the events of a call are applied in the order they
 * arrived, which is what [CallLog] relies on.
 *
 * A full queue means the database is far behind; the event is dropped and logged rather than making the core wait
 * (a lost line in the call log is cheaper than a slowed-down call).
 */
@Component
class CallEventQueue(private val callLog: CallLog) {

    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(QUEUE_CAPACITY),
        { runnable -> Thread(runnable, "call-events").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )

    /** Queues the event with the time it arrived; false when the queue is full (the event is lost). */
    fun submit(event: CallEvent): Boolean {
        val receivedAt = LocalDateTime.now()
        return try {
            executor.execute { applySafely(event, receivedAt) }
            true
        } catch (ex: RejectedExecutionException) {
            // Data-loss point: the call log misses this event
            logError("Call event queue is full ($QUEUE_CAPACITY): ${event.kind} for ${event.sipCallId} dropped")
            false
        }
    }

    private fun applySafely(event: CallEvent, receivedAt: LocalDateTime) {
        try {
            callLog.apply(event, receivedAt)
        } catch (ex: Exception) {
            logError("Call event ${event.kind} for ${event.sipCallId} could not be recorded", ex)
        }
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }

    companion object {
        private const val QUEUE_CAPACITY = 1000
    }
}
