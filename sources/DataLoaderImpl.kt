package io.fluidsonic.dataloader

import kotlin.jvm.*
import kotlinx.coroutines.*


// FIXME races!
internal class DataLoaderImpl<in Key, Value, CacheKey>(
	load: suspend DataLoader<Key, Value>.(keys: List<Key>) -> List<Result<Value>>,
	options: DataLoaderOptions<Key, Value, CacheKey>,
) : DataLoader<Key, Value> {

	private val batchLoadFunction = load
	private val batchScheduleFunction = options.batchScheduleFunction
	private val cacheKeyFunction: DataLoader<Key, Value>.(Key) -> CacheKey = options.cacheKeyFunction
	private val cacheMap: DataLoaderCacheMap<CacheKey, Deferred<Result<Value>>>? = options.cacheMap?.takeIf { options.cache }
	private var currentBatch: Batch? = null
	private val maximumBatchSize = if (options.batch) options.maximumBatchSize else 1


	override suspend fun clear(key: Key) {
		cacheMap?.delete(cacheKeyFunction(key))
	}


	override suspend fun clearAll() {
		cacheMap?.clear()
	}


	private fun ensureBatch(scope: CoroutineScope): Batch {
		currentBatch
			?.takeIf { it.acceptsMore() }
			?.let { return it }

		val batch = Batch()
		currentBatch = batch

		batchScheduleFunction(scope) {
			batch.dispatch()
		}

		return batch
	}


	override suspend fun load(key: Key) =
		loadResult(key).getOrThrow()


	override suspend fun loadMany(keys: Iterable<Key>): List<Result<Value>> =
		coroutineScope {
			keys
				.map { async { loadResult(it) } }
				.awaitAll()
		}


	// FIXME race conditions
	private suspend fun loadResult(key: Key): Result<Value> {
		val batch = ensureBatch(CoroutineScope(currentCoroutineContext()))

		val deferred = CompletableDeferred<Result<Value>>()
		val cacheKey = cacheKeyFunction(key)

		when (val cachedDeferred = cacheMap?.get(cacheKey)) {
			null -> {
				batch.add(key, deferred)

				cacheMap?.set(cacheKey, deferred)
			}

			else -> {
				batch.addCacheHit {
					cachedDeferred.invokeOnCompletion {
						deferred.completeWith(runCatching { cachedDeferred.getCompleted() })
					}
				}
			}
		}

		return deferred.await()
	}


	override suspend fun prime(key: Key, value: Result<Value>) {
		cacheMap ?: return

		val cacheKey = cacheKeyFunction(key)
		if (cacheMap[cacheKey] != null)
			return

		cacheMap[cacheKey] = CompletableDeferred(value)
	}


	private inner class Batch(
		private var cacheHits: MutableList<() -> Unit>? = null,
		private val callbacks: MutableList<CompletableDeferred<Result<Value>>> = mutableListOf(), // FIXME rename
		private var hasDispatched: Boolean = false,
		private val keys: MutableList<Key> = mutableListOf(),
	) {

		fun acceptsMore() =
			!hasDispatched && keys.size < maximumBatchSize && ((cacheHits?.size ?: 0) < maximumBatchSize)


		fun add(key: Key, deferred: CompletableDeferred<Result<Value>>) {
			callbacks += deferred
			keys += key
		}


		fun addCacheHit(hit: () -> Unit) {
			val cacheHits = cacheHits
				?: run { mutableListOf<() -> Unit>().also { cacheHits = it } }

			cacheHits += hit
		}


		suspend fun dispatch() {
			hasDispatched = true

			if (keys.isEmpty())
				return resolveCacheHits()

			try {
				val values = batchLoadFunction(keys)

				check(values.size == keys.size) {
					"DataLoader 'load' function returned a different number of values than it received keys." +
						"\n\nKeys:\n" +
						keys.joinToString() +
						"\n\nValues:\n" +
						values.joinToString()
				}

				resolveCacheHits()

				callbacks.forEachIndexed { index, deferred ->
					deferred.complete(values[index])
				}
			}
			catch (error: Throwable) {
				failedDispatch(error)
			}
		}


		suspend fun failedDispatch(error: Throwable) {
			resolveCacheHits()

			val result = Result.failure<Value>(error)
			keys.forEachIndexed { index, key ->
				clear(key)
				callbacks[index].complete(result)
			}
		}


		fun resolveCacheHits() {
			cacheHits?.forEach { it() }
		}
	}


	companion object {

		// FIXME dispatcher?
		val defaultBatchScheduleFunction: DataLoader<*, *>.(CoroutineScope, suspend () -> Unit) -> Unit = { scope, callback ->

			// FIXME enqueue at end of tick
			scope.launch {
				// TODO The more yield() we add the more layers of coroutines we can support in one batch.
				yield()
				yield()
				yield()
				yield()
				yield()
				yield()
				callback()
			}
		}
	}
}


@JvmName("DataLoaderWithCustomCache")
public fun <Key, Value, CacheKey> DataLoader(
	options: DataLoaderOptions<Key, Value, CacheKey>,
	@BuilderInference load: suspend DataLoader<Key, Value>.(keys: List<Key>) -> List<Result<Value>>,
): DataLoader<Key, Value> =
	DataLoaderImpl(load = load, options = options)


public fun <Key, Value> DataLoader(
	options: DataLoaderOptions<Key, Value, Key> = DataLoader.options(),
	@BuilderInference load: suspend DataLoader<Key, Value>.(keys: List<Key>) -> List<Result<Value>>,
): DataLoader<Key, Value> =
	DataLoaderImpl(load = load, options = options)
