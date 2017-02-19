package pokechu22.test.begradle.baseedit;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * Task that applies all base patches, overwriting any modified base source.
 */
public class ApplyBasePatchesTask extends AbstractPatchingTask {
	// Add annotations to the right methods
	@InputDirectory
	public File getPatches() {
		return super.getPatches();
	}
	@OutputDirectory
	public File getPatchedSource() {
		return super.getPatchedSource();
	}

	@TaskAction
	public void doTask() throws IOException {
		File origJar = getOrigJar();
		List<String> baseClasses = getBaseClasses();

		try (JarFile jar = new JarFile(origJar)) {
			// TODO: Do I want to back these up in some way?
			getLogger().lifecycle("Removing old base sources...");
			FileUtils.cleanDirectory(getPatchedSource());

			for (String className : baseClasses) {
				applyPatch(className, jar);
			}
		}
	}
}
