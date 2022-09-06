package tests

import io.fluidsonic.dataloader.*


fun <Value> identityLoader(
	options: DataLoaderOptions<Value, Value, Value> = DataLoader.options(),
): Pair<DataLoader<Value, Value>, MutableList<List<Value>>> {
	val calls: MutableList<List<Value>> = mutableListOf()
	val loader = DataLoader(options) { keys: List<Value> ->
		calls += keys
		keys.map { Result.success(it) }
	}

	return loader to calls
}


fun <Value, CacheKey> identityLoader(
	cacheKeyFunction: DataLoader<Value, Value>.(key: Value) -> CacheKey,
	options: DataLoaderOptions<Value, Value, CacheKey> = DataLoader.options(cacheKeyFunction = cacheKeyFunction),
): Pair<DataLoader<Value, Value>, MutableList<List<Value>>> {
	val calls: MutableList<List<Value>> = mutableListOf()
	val loader = DataLoader(options) { keys: List<Value> ->
		calls += keys
		keys.map { Result.success(it) }
	}

	return loader to calls
}
