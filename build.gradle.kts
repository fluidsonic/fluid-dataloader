import io.fluidsonic.gradle.*
import org.jetbrains.kotlin.gradle.plugin.*

plugins {
	id("io.fluidsonic.gradle") version "1.2.1"
}

fluidLibrary(name = "dataloader", version = "0.1.0")

fluidLibraryModule(description = "Kotlin version of Facebook's DataLoader library using coroutines") {
	language {
		withExperimentalApi("kotlinx.coroutines.ExperimentalCoroutinesApi")
	}

	targets {
		common {
			dependencies {
				api(kotlinx("coroutines-core", "1.6.4"))
			}

			testDependencies {
				api(kotlinx("coroutines-test", "1.6.4"))
			}
		}

		darwin()
		js(KotlinJsCompilerType.BOTH)
		jvm()
	}
}
