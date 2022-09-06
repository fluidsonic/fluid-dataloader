package io.fluidsonic.dataloader


public interface DataLoader<in Key, Value> {

	public suspend fun clear(key: Key) // FIXME suspend?
	public suspend fun clearAll() // FIXME suspend?
	public suspend fun load(key: Key): Value
	public suspend fun loadMany(keys: Iterable<Key>): List<Result<Value>>
	public suspend fun prime(key: Key, value: Result<Value>) // FIXME suspend?

	public companion object
}


// FIXME suspend?
public suspend fun <Key, Value> DataLoader<Key, Value>.prime(key: Key, value: Value) {
	prime(key, Result.success(value))
}
