package tests

import io.fluidsonic.dataloader.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*


// https://github.com/graphql/dataloader/blob/015a94c8db0b4974838a312634c12a2c5eb3d056/src/__tests__/dataloader.test.js
class DataLoaderTests {

	@Test
	fun testAcceptsACustomCacheMapImplementation() = test {
		val cacheMap = SimpleMap<Deferred<Result<String>>>()
		val (loader, calls) = identityLoader(DataLoader.options(cacheMap = cacheMap))

		// load()
		val (valueA, valueB) = awaitAll(
			async { loader.load("a") },
			async { loader.load("b") },
		)
		assertEquals(actual = valueA, expected = "a")
		assertEquals(actual = valueB, expected = "b")
		assertEquals(actual = calls, expected = listOf(listOf("a", "b")))

		val (valueC, valueB2) = awaitAll(
			async { loader.load("c") },
			async { loader.load("b") },
		)
		assertEquals(actual = valueC, expected = "c")
		assertEquals(actual = valueB2, expected = "b")
		assertEquals(actual = calls, expected = listOf(listOf("a", "b"), listOf("c")))
		assertEquals(actual = cacheMap.values.keys, expected = setOf("a", "b", "c"))

		// clear()
		loader.clear("b")
		val valueB3 = loader.load("b")
		assertEquals(actual = valueB3, expected = "b")
		assertEquals(actual = calls, expected = listOf(listOf("a", "b"), listOf("c"), listOf("b")))
		assertEquals(actual = cacheMap.values.keys, expected = setOf("a", "b", "c"))

		// clearAll()
		loader.clearAll()
		assertTrue(cacheMap.values.isEmpty())
	}


	@Test
	fun testAcceptsObjectsWithAComplexKey() = test {
		val calls = mutableListOf<List<ComplexKey>>()
		val loader = DataLoader(DataLoader.options(cacheKeyFunction = ComplexKey.keyFunction)) { keys ->
			calls += keys
			keys.map { Result.success(it) }
		}

		val key1 = ComplexKey("id", 123)
		val key2 = ComplexKey("id", 123)
		val value1 = loader.load(key1)
		val value2 = loader.load(key2)

		assertSame(actual = value1, expected = key1)
		assertSame(actual = value2, expected = key1)
		assertEquals(actual = calls, expected = listOf(listOf(key1)))
	}


	@Test
	fun testAllowsForcefullyPrimingTheCache() = test {
		val (loader, calls) = identityLoader<String>()

		loader.prime("A", "X")

		val (a, b) = awaitAll(
			async { loader.load("A") },
			async { loader.load("B") },
		)
		assertEquals(actual = a, expected = "X")
		assertEquals(actual = b, expected = "B")

		loader.clear("A")
		loader.clear("B")
		loader.prime("A", "Y")
		loader.prime("B", "Y")

		val (a2, b2) = awaitAll(
			async { loader.load("A") },
			async { loader.load("B") },
		)
		assertEquals(actual = a2, expected = "Y")
		assertEquals(actual = b2, expected = "Y")

		assertEquals(actual = calls, expected = listOf(listOf("B")))
	}


	@Test
	fun testAllowsPrimingTheCache() = test {
		val (loader, calls) = identityLoader<String>()

		loader.prime("A", "A")

		val (a, b) = awaitAll(
			async { loader.load("A") },
			async { loader.load("B") },
		)
		assertEquals(actual = a, expected = "A")
		assertEquals(actual = b, expected = "B")
		assertEquals(actual = calls, expected = listOf(listOf("B")))
	}


	@Test
	fun testAllowsPrimingTheCacheWithAResult() = test {
		val (loader, calls) = identityLoader<String>()

		loader.prime("A", Result.success("A"))

		val (a, b) = awaitAll(
			async { loader.load("A") },
			async { loader.load("B") },
		)
		assertEquals(actual = a, expected = "A")
		assertEquals(actual = b, expected = "B")
		assertEquals(actual = calls, expected = listOf(listOf("B")))
	}


	@Test
	fun testAllowsPrimingTheCacheWithAnObjectKey() = test {
		val (loader, calls) = identityLoader(cacheKeyFunction = ComplexKey.keyFunction)

		val key1 = ComplexKey("id", 123)
		val key2 = ComplexKey("id", 123)

		loader.prime(key1, key1)

		val value1 = loader.load(key1)
		val value2 = loader.load(key2)

		assertSame(actual = value1, expected = key1)
		assertSame(actual = value2, expected = key1)
		assertEquals(actual = calls, expected = emptyList())
	}


	@Test
	fun testBatchesCachedRequests() = test { // FIXME run once with asyncTest once with runTest?
		val calls = mutableListOf<List<Int>>()
		val batch = CompletableDeferred<Unit>()

		val loader = DataLoader<Int, Int> { keys ->
			calls += keys
			batch.await()

			keys.map { Result.success(it) }
		}

		loader.prime(1, 1)

		val deferred1 = async { loader.load(1) }
		val deferred2 = async { loader.load(2) }

		delay(5.milliseconds)
		batch.complete(Unit)

		val (value1, value2) = withTimeout(100.milliseconds) {
			awaitAll(deferred1, deferred2)
		}

		assertEquals(actual = value1, expected = 1)
		assertEquals(actual = value2, expected = 2)
		assertEquals(actual = calls, expected = listOf(listOf(2)))
	}


	@Test
	fun testBatchesLoadsOccuringWithinChildCoroutines() = test {
		val (loader, calls) = identityLoader<String>()

		val a = async { loader.load("A") }
		launch {
			val b = async { loader.load("B") }
			launch {
				val c = async { loader.load("C") }
				launch {
					val d = async { loader.load("D") }

					awaitAll(a, b, c, d)
					assertEquals(actual = calls, expected = listOf(listOf("A", "B", "C", "D")))
				}
			}
		}
	}


	@Test
	fun testBatchesMultipleRequests() = test {
		val (loader, calls) = identityLoader<Int>()

		val signal = CompletableDeferred<Unit>()
		launch {
			delay(10.milliseconds)
			signal.complete(Unit)
		}

		val (value1, value2) = awaitAll(
			async { signal.await(); loader.load(1) },
			async { signal.await(); loader.load(2) },
		)
		assertEquals(actual = value1, expected = 1)
		assertEquals(actual = value2, expected = 2)
		assertEquals(actual = calls, expected = listOf(listOf(1, 2)))
	}


	@Test
	fun testBatchesMultipleRequestsWithMaximumBatchSizes() = test {
		val (loader, calls) = identityLoader<Int>(DataLoader.options(maximumBatchSize = 2))

		val signal = CompletableDeferred<Unit>()
		launch {
			delay(10.milliseconds)
			signal.complete(Unit)
		}

		val (value1, value2, value3) = awaitAll(
			async { signal.await(); loader.load(1) },
			async { signal.await(); loader.load(2) },
			async { signal.await(); loader.load(3) },
		)
		assertEquals(actual = value1, expected = 1)
		assertEquals(actual = value2, expected = 2)
		assertEquals(actual = value3, expected = 3)
		assertEquals(actual = calls, expected = listOf(listOf(1, 2), listOf(3)))
	}


	@Test
	fun testBuildsAReallyReallySimpleDataLoader() = test {
		val (loader) = identityLoader<Int>()
		assertEquals(actual = loader.load(1), expected = 1)
	}


	@Test
	fun testCacheMapMayBeSetToNullToDisableCache() = test {
		val (loader, calls) = identityLoader<String>(DataLoader.options(cacheMap = null))

		awaitAll(
			async { loader.load("A") },
			async { loader.load("A") },
		)
		assertEquals(actual = calls, expected = listOf(listOf("A", "A")))
	}


	@Test
	fun testCachesFailedFetches() = test {
		val calls = mutableListOf<List<Int>>()
		val loader = DataLoader<Int, Int> { keys ->
			calls += keys
			keys.map { Result.failure(Error("Error: $it")) }
		}

		val error = assertFailsWith<Error> { loader.load(1) }
		assertEquals(actual = error.message, expected = "Error: 1")

		val error2 = assertFailsWith<Error> { loader.load(1) }
		assertEquals(actual = error2.message, expected = "Error: 1")

		assertEquals(actual = calls, expected = listOf(listOf(1)))
	}


	@Test
	fun testCachesRepeatedRequests() = test {
		val (loader, calls) = identityLoader<String>()

		val (a, b) = awaitAll(
			async { loader.load("A") },
			async { loader.load("B") },
		)
		assertEquals(actual = a, expected = "A")
		assertEquals(actual = b, expected = "B")
		assertEquals(actual = calls, expected = listOf(listOf("A", "B")))

		val (a2, c) = awaitAll(
			async { loader.load("A") },
			async { loader.load("C") },
		)
		assertEquals(actual = a2, expected = "A")
		assertEquals(actual = c, expected = "C")
		assertEquals(actual = calls, expected = listOf(listOf("A", "B"), listOf("C")))

		val (a3, b2, c2) = awaitAll(
			async { loader.load("A") },
			async { loader.load("B") },
			async { loader.load("C") },
		)
		assertEquals(actual = a3, expected = "A")
		assertEquals(actual = b2, expected = "B")
		assertEquals(actual = c2, expected = "C")
		assertEquals(actual = calls, expected = listOf(listOf("A", "B"), listOf("C")))
	}


	@Test
	fun testCanCallALoaderFromALoader() = test {
		val (deepLoader, deepLoadCalls) = identityLoader<List<String>>()

		val aLoadCalls = mutableListOf<List<String>>()
		val aLoader = DataLoader { keys: List<String> ->
			aLoadCalls += keys
			deepLoader.load(keys).map { Result.success(it) }
		}

		val bLoadCalls = mutableListOf<List<String>>()
		val bLoader = DataLoader { keys: List<String> ->
			bLoadCalls += keys
			deepLoader.load(keys).map { Result.success(it) }
		}

		val (a1, b1, a2, b2) = awaitAll(
			async { aLoader.load("A1") },
			async { bLoader.load("B1") },
			async { aLoader.load("A2") },
			async { bLoader.load("B2") },
		)

		assertEquals(actual = a1, expected = "A1")
		assertEquals(actual = b1, expected = "B1")
		assertEquals(actual = a2, expected = "A2")
		assertEquals(actual = b2, expected = "B2")
		assertEquals(actual = aLoadCalls, expected = listOf(listOf("A1", "A2")))
		assertEquals(actual = bLoadCalls, expected = listOf(listOf("B1", "B2")))
		assertEquals(actual = deepLoadCalls, expected = listOf(listOf(listOf("A1", "A2"), listOf("B1", "B2"))))
	}


	@Test
	fun testCanClearValuesFromCacheAfterErrors() = test {
		val calls = mutableListOf<List<Int>>()
		val loader = DataLoader<Int, Int> { keys ->
			calls += keys
			keys.map { Result.failure(Error("Error: $it")) }
		}

		val error = assertFailsWith<Error> { loader.load(1) }
		assertEquals(actual = error.message, expected = "Error: 1")
		loader.clear(1)

		val error2 = assertFailsWith<Error> { loader.load(1) }
		assertEquals(actual = error2.message, expected = "Error: 1")
		loader.clear(1)

		assertEquals(actual = calls, expected = listOf(listOf(1), listOf(1)))
	}


	@Test
	fun testCanRepresentFailuresAndSuccessesSimultaneously() = test {
		val calls = mutableListOf<List<Int>>()
		val loader = DataLoader<Int, Int> { keys ->
			calls += keys

			keys.map { key ->
				when {
					key % 2 == 0 -> Result.success(key)
					else -> Result.failure(Error("Odd: $key"))
				}
			}
		}

		val (result1, result2) = awaitAll(
			async { runCatching { loader.load(1) } },
			async { runCatching { loader.load(2) } },
		)

		val error = assertFailsWith<Error> { result1.getOrThrow() }
		assertEquals(actual = error.message, expected = "Odd: 1")
		assertEquals(actual = result2, expected = Result.success(2))
		assertEquals(actual = calls, expected = listOf(listOf(1, 2)))
	}


	@Test
	fun testClearsAllValuesInLoader() = test {
		val (loader, calls) = identityLoader<String>()

		val (a, b) = awaitAll(
			async { loader.load("A") },
			async { loader.load("B") },
		)
		assertEquals(actual = a, expected = "A")
		assertEquals(actual = b, expected = "B")
		assertEquals(actual = calls, expected = listOf(listOf("A", "B")))

		loader.clearAll()

		val (a2, b2) = awaitAll(
			async { loader.load("A") },
			async { loader.load("B") },
		)
		assertEquals(actual = a2, expected = "A")
		assertEquals(actual = b2, expected = "B")
		assertEquals(actual = calls, expected = listOf(listOf("A", "B"), listOf("A", "B")))
	}


	@Test
	fun testClearsObjectsWithComplexKey() = test {
		val calls = mutableListOf<List<ComplexKey>>()
		val loader = DataLoader(DataLoader.options(cacheKeyFunction = ComplexKey.keyFunction)) { keys ->
			calls += keys
			keys.map { Result.success(it) }
		}

		val key1 = ComplexKey("id", 123)
		val key2 = ComplexKey("id", 123)
		val value1 = loader.load(key1)
		loader.clear(key2)
		val value2 = loader.load(key1)

		assertSame(actual = value1, expected = key1)
		assertSame(actual = value2, expected = key1)
		assertEquals(actual = calls, expected = listOf(listOf(key1), listOf(key1)))
	}


	@Test
	fun testClearsSingleValueInLoader() = test {
		val (loader, calls) = identityLoader<String>()

		val (a, b) = awaitAll(
			async { loader.load("A") },
			async { loader.load("B") },
		)
		assertEquals(actual = a, expected = "A")
		assertEquals(actual = b, expected = "B")
		assertEquals(actual = calls, expected = listOf(listOf("A", "B")))

		loader.clear("A")

		val (a2, b2) = awaitAll(
			async { loader.load("A") },
			async { loader.load("B") },
		)
		assertEquals(actual = a2, expected = "A")
		assertEquals(actual = b2, expected = "B")
		assertEquals(actual = calls, expected = listOf(listOf("A", "B"), listOf("A")))
	}


	@Test
	fun testCoalescesIdenticalRequests() = test {
		val (loader, calls) = identityLoader<Int>()

		val value1 = loader.load(1)
		val value1b = loader.load(1)
		assertEquals(actual = value1, expected = 1)
		assertEquals(actual = value1b, expected = 1)
		assertEquals(actual = calls, expected = listOf(listOf(1)))
	}


	@Test
	fun testCoalescesIdenticalRequestsAcrossSizedBatches() = test {
		val (loader, calls) = identityLoader<Int>(DataLoader.options(maximumBatchSize = 2))

		val signal = CompletableDeferred<Unit>()
		launch {
			delay(10.milliseconds)
			signal.complete(Unit)
		}

		val (value1, value2, value1b, value3) = awaitAll(
			async { signal.await(); loader.load(1) },
			async { signal.await(); loader.load(2) },
			async { signal.await(); loader.load(1) },
			async { signal.await(); loader.load(3) },
		)
		assertEquals(actual = value1, expected = 1)
		assertEquals(actual = value2, expected = 2)
		assertEquals(actual = value1b, expected = 1)
		assertEquals(actual = value3, expected = 3)
		assertEquals(actual = calls, expected = listOf(listOf(1, 2), listOf(3)))
	}


	@Test
	fun testComplexCacheBehaviorViaClearAll() = test {
		val calls = mutableListOf<List<String>>()
		val loader = DataLoader { keys: List<String> ->
			clearAll()

			calls += keys
			keys.map { Result.success(it) }
		}

		val values = awaitAll(
			async { loader.load("A") },
			async { loader.load("B") },
			async { loader.load("A") },
		)
		assertEquals(actual = values, expected = listOf("A", "B", "A"))

		val values2 = awaitAll(
			async { loader.load("A") },
			async { loader.load("B") },
			async { loader.load("A") },
		)
		assertEquals(actual = values2, expected = listOf("A", "B", "A"))

		assertEquals(actual = calls, expected = listOf(listOf("A", "B"), listOf("A", "B")))
	}


	@Test
	fun testCustomBatchSchedulerIsProvidedLoader() = test {
		var that: DataLoader<*, *>? = null
		val (loader) = identityLoader<String>(DataLoader.options(
			batchScheduleFunction = { scope, callback ->
				that = this
				scope.launch {
					callback()
				}
			}
		))

		loader.load("A")

		assertSame(actual = that, expected = loader)
	}


	@Test
	fun testDoesNotPrimeKeysThatAlreadyExist() = test {
		val (loader, calls) = identityLoader<String>()

		loader.prime("A", "X")

		val (a, b) = awaitAll(
			async { loader.load("A") },
			async { loader.load("B") },
		)
		assertEquals(actual = a, expected = "X")
		assertEquals(actual = b, expected = "B")

		loader.prime("A", "Y")
		loader.prime("B", "Y")

		val (a2, b2) = awaitAll(
			async { loader.load("A") },
			async { loader.load("B") },
		)
		assertEquals(actual = a2, expected = "X")
		assertEquals(actual = b2, expected = "B")

		assertEquals(actual = calls, expected = listOf(listOf("B")))
	}


	@Test
	fun testHandlesPrimingTheCacheWithAnError() = test {
		val (loader, calls) = identityLoader<Int>()

		loader.prime(1, Result.failure(Error("Error: 1")))

		val error = assertFailsWith<Error> { loader.load(1) }
		assertEquals(actual = error.message, expected = "Error: 1")
		assertEquals(actual = calls, expected = emptyList())
	}


	@Test
	fun testKeysAreRepeatedInBatchWhenCacheDisabled() = test {
		val (loader, calls) = identityLoader<String>(DataLoader.options(cache = false))

		loader.prime("A", "A")

		val (a, c, d, other) = awaitAll(
			async { loader.load("A") },
			async { loader.load("C") },
			async { loader.load("D") },
			async { loader.loadMany(listOf("C", "D", "A", "A", "B")) },
		)
		assertEquals(actual = a, expected = "A")
		assertEquals(actual = c, expected = "C")
		assertEquals(actual = d, expected = "D")
		assertEquals(actual = other, expected = listOf("C", "D", "A", "A", "B").map { Result.success(it) })
		assertEquals(actual = calls, expected = listOf(listOf("A", "C", "D", "C", "D", "A", "A", "B")))
	}


	@Test
	fun testMaximumBatchSizeRespectsCachedResults() = test {
		val (loader, calls) = identityLoader<Int>(DataLoader.options(maximumBatchSize = 1))
		loader.prime(1, 1)

		val signal = CompletableDeferred<Unit>()
		launch {
			delay(10.milliseconds)
			signal.complete(Unit)
		}

		val (value1, value2) = awaitAll(
			async { signal.await(); loader.load(1) },
			async { signal.await(); loader.load(2) },
		)
		assertEquals(actual = value1, expected = 1)
		assertEquals(actual = value2, expected = 2)
		assertEquals(actual = calls, expected = listOf(listOf(2)))
	}


	@Test
	fun testMayDisableBatching() = test {
		val (loader, calls) = identityLoader<Int>(DataLoader.options(batch = false))

		val (value1, value2) = awaitAll(
			async { loader.load(1) },
			async { loader.load(2) },
		)
		assertEquals(actual = value1, expected = 1)
		assertEquals(actual = value2, expected = 2)
		assertEquals(actual = calls, expected = listOf(listOf(1), listOf(2)))
	}


	@Test
	fun testMayDisableCaching() = test {
		val (loader, calls) = identityLoader<String>(DataLoader.options(cache = false))

		val (a, b) = awaitAll(
			async { loader.load("A") },
			async { loader.load("B") },
		)
		assertEquals(actual = a, expected = "A")
		assertEquals(actual = b, expected = "B")
		assertEquals(actual = calls, expected = listOf(listOf("A", "B")))

		val (a2, c) = awaitAll(
			async { loader.load("A") },
			async { loader.load("C") },
		)
		assertEquals(actual = a2, expected = "A")
		assertEquals(actual = c, expected = "C")
		assertEquals(actual = calls, expected = listOf(listOf("A", "B"), listOf("A", "C")))

		val (a3, b2, c2) = awaitAll(
			async { loader.load("A") },
			async { loader.load("B") },
			async { loader.load("C") },
		)
		assertEquals(actual = a3, expected = "A")
		assertEquals(actual = b2, expected = "B")
		assertEquals(actual = c2, expected = "C")
		assertEquals(actual = calls, expected = listOf(listOf("A", "B"), listOf("A", "C"), listOf("A", "B", "C")))
	}


	@Test
	fun testPropagatesErrorToAllLoads() = test {
		val calls = mutableListOf<List<Int>>()
		val loader = DataLoader<Int, Int> { keys ->
			calls += keys
			throw Error("I am a terrible loader")
		}

		val (result1, result2) = awaitAll(
			async { runCatching { loader.load(1) } },
			async { runCatching { loader.load(2) } },
		)

		val error = assertFailsWith<Error> { result1.getOrThrow() }
		assertEquals(actual = error.message, expected = "I am a terrible loader")

		val error2 = assertFailsWith<Error> { result2.getOrThrow() }
		assertSame(actual = error2, expected = error)

		assertEquals(actual = calls, expected = listOf(listOf(1, 2)))
	}


	@Test
	fun testReferencesTheLoaderAsThisInTheBatchFunction() = test {
		var that: DataLoader<Int, Int>? = null
		val (loader) = identityLoader(DataLoader.options(cacheKeyFunction = { key: Int ->
			that = this
			key
		}))
		loader.load(1)

		assertSame(actual = that, expected = loader)
	}


	@Test
	fun testReferencesTheLoaderAsThisInTheCacheKeyBatchFunction() = test {
		var that: DataLoader<Int, Int>? = null
		val loader = DataLoader { keys ->
			that = this
			keys.map { Result.success(it) }
		}
		loader.load(1)

		assertSame(actual = that, expected = loader)
	}


	@Test
	fun testResolvesToErrorToIndicateFailure() = test {
		val calls = mutableListOf<List<Int>>()
		val loader = DataLoader<Int, Int> { keys ->
			calls += keys

			keys.map { key ->
				when {
					key % 2 == 0 -> Result.success(key)
					else -> Result.failure(Error("Odd: $key"))
				}
			}
		}

		val error = assertFailsWith<Error> { loader.load(1) }
		assertEquals(actual = error.message, expected = "Odd: 1")

		val value2 = loader.load(2)
		assertEquals(actual = value2, expected = 2)

		assertEquals(actual = calls, expected = listOf(listOf(1), listOf(2)))
	}


	@Test
	fun testSupportsManualDispatch() = test {
		val callbacks = mutableListOf<suspend () -> Unit>()

		suspend fun dispatch() {
			yield()
			callbacks.forEach { it() }
			callbacks.clear()
		}

		@Suppress("UNUSED_PARAMETER", "UnusedReceiverParameter")
		fun DataLoader<*, *>.schedule(scope: CoroutineScope, callback: suspend () -> Unit) {
			callbacks += callback
		}

		val (loader, calls) = identityLoader<String>(DataLoader.options(
			batchScheduleFunction = DataLoader<*, *>::schedule,
		))

		val deferred1 = async { loader.load("A") }
		val deferred2 = async { loader.load("B") }
		dispatch()
		val deferred3 = async { loader.load("A") }
		val deferred4 = async { loader.load("C") }
		dispatch()
		val d = launch { loader.load("D") } // never dispatched
		yield()

		awaitAll(deferred1, deferred2, deferred3, deferred4)

		assertEquals(actual = calls, expected = listOf(listOf("A", "B"), listOf("C")))

		d.cancel()
	}


	@Test
	fun testSupportsLoadingMultipleKeysInOneCall() = test {
		val (loader) = identityLoader<Int>()
		assertEquals(actual = loader.loadMany(listOf(1, 2)), expected = listOf(Result.success(1), Result.success(2)))
		assertEquals(actual = loader.loadMany(emptyList()), expected = emptyList())
	}


	@Test
	fun testSupportsLoadingMultipleKeysInOneCallWithErrors() = test {
		val loader = DataLoader { keys: List<String> ->
			keys.map { key ->
				when (key) {
					"bad" -> Result.failure(Error("Bad Key"))
					else -> Result.success(key)
				}
			}
		}
		val (result1, result2, result3) = loader.loadMany(listOf("a", "b", "bad"))

		assertEquals(actual = result1, expected = Result.success("a"))
		assertEquals(actual = result2, expected = Result.success("b"))

		val error3 = assertFailsWith<Error> { result3.getOrThrow() }
		assertEquals(actual = error3.message, expected = "Bad Key")
	}


	private data class ComplexKey(
		val a: String,
		val b: Int,
	) {

		companion object {

			val keyFunction: DataLoader<*, *>.(ComplexKey) -> String = { key ->
				"${key.a}:${key.b}"
			}
		}
	}


	private class SimpleMap<Value : Any> : DataLoaderCacheMap<String, Value> {

		val values = mutableMapOf<String, Value>()


		override fun clear() {
			values.clear()
		}


		override fun delete(key: String) {
			values.remove(key)
		}


		override fun get(key: String) =
			values[key]


		override fun set(key: String, value: Value) {
			values[key] = value
		}
	}
}
