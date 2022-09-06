package tests

import kotlinx.coroutines.*


actual fun runBlockingTest(test: suspend CoroutineScope.() -> Unit): dynamic =
	GlobalScope.promise(block = test)
