package tests

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*


fun test(block: suspend CoroutineScope.() -> Unit) {
	runTest(testBody = block)

	return runBlockingTest(block)
}


expect fun runBlockingTest(test: suspend CoroutineScope.() -> Unit)
