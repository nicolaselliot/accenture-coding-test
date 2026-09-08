package dev.nicolas.githubsearch.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/**
 * Places a Compose resource bundle where an Android APK can carry it.
 *
 * Compose Resources are read from `assets` at runtime on Android, and AGP 9's
 * `com.android.kotlin.multiplatform.library` plugin has no assets pipeline at all — its variants
 * expose no assets source directory and its AAR has no `assets/` entry — so a shared module cannot
 * contribute them. The application module packages them instead, and this is the task whose output
 * directory it registers as a generated asset source.
 *
 * A directory output rather than a `Sync`, because `Sources.addGeneratedSourceDirectory` wires
 * itself to a `DirectoryProperty` and `Sync` exposes a plain `File`.
 */
@CacheableTask
public abstract class CopyComposeResourcesToAssetsTask : DefaultTask() {
    /**
     * The bundles to package, already laid out as `composeResources/<resource package>/…`.
     *
     * The layout is the producing module's business: the package segment comes from its
     * `packageOfResClass`, and the application has no business knowing what that is.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val bundles: ConfigurableFileCollection

    @get:OutputDirectory
    public abstract val outputDirectory: DirectoryProperty

    @get:Inject
    public abstract val fileSystem: FileSystemOperations

    @TaskAction
    public fun copy() {
        // sync, not copy: a resource deleted upstream has to disappear from the assets too, or a
        // stale bundle ships in the APK long after its source is gone.
        fileSystem.sync {
            from(bundles)
            into(outputDirectory)
        }
    }
}
