package tests

import kotlinx.coroutines.*


actual fun runBlockingTest(test: suspend CoroutineScope.() -> Unit) {
	runBlocking(block = test)
}
