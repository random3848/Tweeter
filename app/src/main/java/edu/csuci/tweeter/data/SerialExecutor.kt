package edu.csuci.tweeter.data

import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor

/**
 * Returns an [Executor] that runs its tasks on this dispatcher one at a time, in the order they
 * were submitted, so a slow task can't be overtaken by one submitted after it.
 */
internal fun CoroutineDispatcher.serialExecutor(): Executor = limitedParallelism(1).asExecutor()
