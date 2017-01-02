package pokechu22.test.begradle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import difflib.DiffUtils;
import difflib.Patch;

public class GenerateBasePatchesTask extends AbstractPatchingTask {
	// Add annotations to the right methods
	@OutputDirectory
	public File getPatches() {
		return super.getPatches();
	}
	@InputDirectory
	public File getPatchedSource() {
		return super.getPatchedSource();
	}

	@TaskAction
	public void doTask() throws InvalidUserDataException, IOException {
		File origJar = getOrigJar();
		List<String> baseClasses = getBaseClasses();

		try (JarFile jar = new JarFile(origJar)) {
			// TODO: Do I want to back these up in some way?
			getLogger().lifecycle("Removing old patches...");
			FileUtils.cleanDirectory(getPatches());

			for (String className : baseClasses) {
				File sourceFile = getPatchedSource(className);
				File patchFile = getPatch(className);
				// For the unified diff
				String filename = className.replace('.', '/') + ".java";

				getLogger().lifecycle("Generating patch for {} from {} to {}",
						className, sourceFile, patchFile);

				List<String> origContent;
				try (InputStream input = jar.getInputStream(getJarEntry(className, jar))) {
					origContent = IOUtils.readLines(input, "UTF-8");
				}

				List<String> newContent = FileUtils.readLines(sourceFile, "UTF-8");

				Patch<String> patch = DiffUtils.diff(origContent, newContent);
				List<String> diff = DiffUtils.generateUnifiedDiff(filename,
						filename, origContent, patch, 3);

				FileUtils.writeLines(patchFile, diff);
			}
		}
	}
}
