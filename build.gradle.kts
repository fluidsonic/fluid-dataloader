import io.fluidsonic.gradle.*

plugins {
	id("io.fluidsonic.gradle") version "1.3.1"
}

fluidLibrary(name = "dataloader", version = "0.2.0")

fluidLibraryModule(description = "Kotlin version of Facebook's DataLoader library using coroutines") {
	language {
		withExperimentalApi("kotlinx.coroutines.ExperimentalCoroutinesApi")
	}

	targets {
		common {
			dependencies {
				api(kotlinx("coroutines-core", "1.7.1"))
			}

			testDependencies {
				api(kotlinx("coroutines-test", "1.7.1"))
			}
		}

		darwin()
		js()
		jvm()
	}
}
