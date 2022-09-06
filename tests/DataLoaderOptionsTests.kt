package tests

import io.fluidsonic.dataloader.*
import kotlin.test.*


// https://github.com/graphql/dataloader/blob/015a94c8db0b4974838a312634c12a2c5eb3d056/src/__tests__/abuse.test.js
class DataLoaderOptionsTests {

	@Test
	fun testBatchFunctionMustPromiseAnArrayOfCorrectLength() = runBlockingTest {
		val loader = DataLoader<Int, Int> { emptyList() }
		val error = assertFails { loader.load(1) }
		assertEquals(
			actual = error.message,
			expected = """
							DataLoader 'load' function returned a different number of values than it received keys.

							Keys:
							1

							Values:

						""".trimIndent()
		)
	}


	@Test
	fun testRequiresAPositiveNumberForMaximumBatchSize() {
		val error = assertFails { DataLoader.options<Int, Int>(maximumBatchSize = 0) }
		assertEquals(actual = error.message, expected = "'maximumBatchSize' must be positive: 0")
	}
}
