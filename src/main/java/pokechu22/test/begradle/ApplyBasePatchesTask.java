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
import difflib.PatchFailedException;

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
	public void doTask() throws InvalidUserDataException, IOException {
		File origJar = getOrigJar();
		List<String> baseClasses = getBaseClasses();

		try (JarFile jar = new JarFile(origJar)) {
			// TODO: Do I want to back these up in some way?
			getLogger().lifecycle("Removing old base sources...");
			FileUtils.cleanDirectory(getPatchedSource());

			for (String className : baseClasses) {
				File sourceFile = getPatchedSource(className);
				File patchFile = getPatch(className);

				List<String> newContent;

				List<String> origContent;
				try (InputStream input = jar.getInputStream(getJarEntry(className, jar))) {
					origContent = IOUtils.readLines(input, "UTF-8");
				}

				if (patchFile.exists()) {
					getLogger().lifecycle("Applying patch for {} to {} from {}",
							className, sourceFile, patchFile);

					List<String> diff = FileUtils.readLines(patchFile, "UTF-8");
					Patch<String> patch = DiffUtils.parseUnifiedDiff(diff);

					try {
						newContent = DiffUtils.patch(origContent, patch);
					} catch (PatchFailedException e) {
						getLogger().warn("Pating failed for " + className + "!", e);
						getLogger().warn("Saving unpatched version instead...");
						newContent = origContent;
					}
				} else {
					getLogger().lifecycle("Copying original {} as no patch exists",
							className);

					newContent = origContent;
				}

				FileUtils.writeLines(sourceFile, newContent);
			}
		}
	}
}
