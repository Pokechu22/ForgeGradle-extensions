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
 * Task that generates all base patches, overwriting any modified base source.
 */
public class GenerateBasePatchesTask extends AbstractPatchingTask {
	// Add annotations to the right methods
	@Override
	@OutputDirectory
	public File getPatches() {
		return super.getPatches();
	}
	@Override
	@InputDirectory
	public File getPatchedSource() {
		return super.getPatchedSource();
	}

	@TaskAction
	public void doTask() throws IOException {
		File origJar = getOrigJar();
		List<String> baseClasses = getBaseClasses();

		try (JarFile jar = new JarFile(origJar)) {
			// TODO: Do I want to back these up in some way?
			getLogger().lifecycle("Removing old patches...");
			FileUtils.cleanDirectory(getPatches());

			for (String className : baseClasses) {
				genPatch(className, jar);
			}
		}
	}
}
