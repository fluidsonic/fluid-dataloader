package io.fluidsonic.dataloader


public interface DataLoaderCacheMap<in Key, Value : Any> {

	public fun clear()
	public fun delete(key: Key)
	public operator fun get(key: Key): Value?
	public operator fun set(key: Key, value: Value)

	public companion object
}
