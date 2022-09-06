package io.fluidsonic.dataloader

import kotlinx.coroutines.*


public data class DataLoaderOptions<Key, Value, CacheKey> internal constructor(
	val batch: Boolean,
	val batchScheduleFunction: DataLoader<Key, Value>. (scope: CoroutineScope, callback: suspend () -> Unit) -> Unit,
	val cache: Boolean,
	val cacheKeyFunction: DataLoader<Key, Value>.(key: Key) -> CacheKey,
	val cacheMap: DataLoaderCacheMap<CacheKey, Deferred<Result<Value>>>?,
	val maximumBatchSize: Int,
) {

	init {
		require(maximumBatchSize > 0) { "'maximumBatchSize' must be positive: $maximumBatchSize" }
	}


	public companion object
}


public fun <Key, Value> DataLoader.Companion.options(
	batch: Boolean = true,
	batchScheduleFunction: DataLoader<Key, Value>.(scope: CoroutineScope, callback: suspend () -> Unit) -> Unit = DataLoaderImpl.defaultBatchScheduleFunction,
	cache: Boolean = true,
	cacheMap: DataLoaderCacheMap<Key, Deferred<Result<Value>>>? = DataLoader.cacheMap(),
	maximumBatchSize: Int = Int.MAX_VALUE,
): DataLoaderOptions<Key, Value, Key> =
	options(
		batch = batch,
		batchScheduleFunction = batchScheduleFunction,
		cache = cache,
		cacheKeyFunction = { it },
		cacheMap = cacheMap,
		maximumBatchSize = maximumBatchSize,
	)


public fun <Key, Value, CacheKey> DataLoader.Companion.options(
	batch: Boolean = true,
	batchScheduleFunction: DataLoader<Key, Value>. (scope: CoroutineScope, callback: suspend () -> Unit) -> Unit = DataLoaderImpl.defaultBatchScheduleFunction,
	cache: Boolean = true,
	cacheKeyFunction: DataLoader<Key, Value>.(key: Key) -> CacheKey,
	cacheMap: DataLoaderCacheMap<CacheKey, Deferred<Result<Value>>>? = DataLoader.cacheMap(),
	maximumBatchSize: Int = Int.MAX_VALUE,
): DataLoaderOptions<Key, Value, CacheKey> =
	DataLoaderOptions(
		batch = batch,
		batchScheduleFunction = batchScheduleFunction,
		cache = cache,
		cacheKeyFunction = cacheKeyFunction,
		cacheMap = cacheMap,
		maximumBatchSize = maximumBatchSize,
	)
