package io.fluidsonic.dataloader


internal class DataLoaderCacheMapImpl<in Key, Value : Any> : DataLoaderCacheMap<Key, Value> {

	// FIXME concurrent?
	private val map: HashMap<Key, Value> = hashMapOf()


	override fun clear() {
		map.clear()
	}


	override fun delete(key: Key) {
		map.remove(key)
	}


	override fun get(key: Key) =
		map[key]


	override fun set(key: Key, value: Value) {
		map[key] = value
	}
}


public fun <Key, Value : Any> DataLoader.Companion.cacheMap(): DataLoaderCacheMap<Key, Value> =
	DataLoaderCacheMapImpl()
