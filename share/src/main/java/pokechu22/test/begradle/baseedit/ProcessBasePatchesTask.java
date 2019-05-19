package pokechu22.test.begradle.baseedit;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskValidationException;

/**
 * Task that does general processing on base patches, both applying and
 * generating them. It also validates the base folders.
 */
public class ProcessBasePatchesTask extends AbstractPatchingTask {
	@Override
	@InputDirectory
	@OutputDirectory
	@SkipWhenEmpty
	@Optional
	public File getPatches() {
		return super.getPatches();
	}
	@Override
	@InputDirectory
	@OutputDirectory
	@SkipWhenEmpty
	@Optional
	public File getPatchedSource() {
		return super.getPatchedSource();
	}

	@TaskAction
	public void doTask() throws IOException {
		List<String> baseClasses = getBaseClasses();
		List<InvalidUserDataException> problems = new ArrayList<>();

		// Check the for undeclared patches
		List<String> unusedPatches = new ArrayList<>(baseClasses);
		File patches = getPatches();
		if (patches.exists()) {
			for (File file : patches.listFiles()) {
				if (file.isDirectory()) {
					problems.add(err("Directory '" + file + "' in patches directory!"));
					continue;
				}
				String filename = file.getName();
				if (!filename.endsWith(".patch")) {
					problems.add(err("Non-patch file '" + file + "' in patches directory!"));
					continue;
				}
				String className = filename.substring(0, filename.length() - ".patch".length());
				if (!baseClasses.contains(className)) {
					problems.add(err("Patch '" + file
							+ "' is not in the declared base class list!"));
					continue;
				}
	
				unusedPatches.remove(className);
			}
		}
		getLogger().debug("Unused patches: " + unusedPatches);

		List<String> unusedClasses = new ArrayList<>(baseClasses);
		recursivelyCheckBaseSrcFolder(getPatchedSource(), "", baseClasses, unusedClasses, problems);
		getLogger().debug("Unused classes: " + unusedClasses);

		if (!problems.isEmpty()) {
			throw new TaskValidationException(
					"There are problems with the base patch configuration!",
					problems);
		}

		// OK, now we look through the list of unused classes and patches
		Set<String> unused = new HashSet<>();
		unused.addAll(unusedPatches);
		unused.addAll(unusedClasses);

		// Now apply/generate the patches.
		File jarFile = getOrigJar();
		try (JarFile jar = new JarFile(jarFile)) {
			// Work through cases where we are missing one or the other.
			for (String className : unused) {
				if (unusedPatches.contains(className)) {
					if (unusedClasses.contains(className)) {
						// No patch, no class - copy the file and make a blank patch.
						File sourceFile = getPatchedSource(className);
						getLogger().lifecycle("Copying original {} to {} as no patch exists",
								className, sourceFile);
						try (InputStream input = jar.getInputStream(getJarEntry(className, jar, false))) {
							FileUtils.writeLines(sourceFile, IOUtils.readLines(input, "UTF-8"));
						}
						genPatch(className, jar);
					} else {
						// We have a class, but no patch - generate the patch
						genPatch(className, jar);
					}
				} else {
					if (unusedClasses.contains(className)) {
						// We have a patch, but not a class - apply the patch
						applyPatch(className, jar);
					}
				}
			}

			// The remaining files have both a patch and source.
			// Decide what to do with them based on modified date.
			List<String> used = new ArrayList<>(baseClasses);
			used.removeAll(unused);
			long jarAge = jarFile.lastModified();
			getLogger().debug("Jar file age: " + jarAge);
			for (String className : used) {
				File source = getPatchedSource(className);
				File patch = getPatch(className);
				long sourceAge = source.lastModified();
				long patchAge = patch.lastModified();
				getLogger().debug("Class " + className + ": source age " + sourceAge + ", patch age " + patchAge);

				if (jarAge > sourceAge) {
					// If the jar is newer than the source, then mappings have probably been updated
					// and patches should be applied.
					getLogger().debug("Applying patch as the jar is newer than the source.");
					applyPatch(className, jar);
				} else if (patchAge > sourceAge) {
					// If the patch is newer than the source, then again we should apply patches.
					getLogger().debug("Applying patch as the patch is newer than the source.");
					applyPatch(className, jar);
				} else {
					// Otherwise, generate patches. This occurs both when the source is newer than
					// the patch, and as a default case when the ages are the same (or lastModified
					// returns 0).
					getLogger().debug("Making patch as the source is newer than the patch."); 
					genPatch(className, jar);
					// We don't want the patch to be newer than the source file as that'd cause it
					// to be applied next time, which is confusing
					patch.setLastModified(sourceAge);
				}
			}
		}
	}

	/**
	 * Recursively searches through the src/main/base (or test or similar) folder,
	 * looking for incorrectly placed files.
	 *
	 * @param from           The folder to start in
	 * @param classNameSoFar The name of the package, with a trailing dot as needed.
	 * @param baseClasses    A list of expected class names.
	 * @param unusedClasses  A list of class names that have NOT been found yet. As
	 *                       classes are found, they are removed from this list.
	 * @param problems       List of problems found during this process.
	 */
	private void recursivelyCheckBaseSrcFolder(File from, String classNameSoFar,
			List<String> baseClasses, List<String> unusedClasses,
			List<InvalidUserDataException> problems) {
		if (!from.exists()) {
			return;
		}
		for (File file : from.listFiles()) {
			if (file.isDirectory()) {
				recursivelyCheckBaseSrcFolder(file, classNameSoFar + file.getName() + ".", baseClasses,
						unusedClasses, problems);
			} else {
				String className = classNameSoFar + file.getName();
				if (!className.endsWith(".java")) {
					problems.add(err("Non-java file '" + file
							+ "' in patches directory!  "
							+ "(Base classes may only be java)"));
					continue;
				}
				className = className.substring(0, className.length() - ".java".length());
				if (!baseClasses.contains(className)) {
					problems.add(err("Class '" + className + "' (" + file
							+ ") is not in the declared base class list!"));
					return;
				} else {
					// The class is used
					unusedClasses.remove(className);
				}
			}
		}
	}

	/**
	 * Creates an exception (with a filled-in stacktrace)
	 * @param s The error and exception message
	 * @return An exception
	 */
	private InvalidUserDataException err(String s) {
		// Don't log the error as it's included in the exception info (and breaks formatting)
		// getLogger().error(s);
		InvalidUserDataException ex = new InvalidUserDataException(s);
		ex.fillInStackTrace();
		return ex;
	}
}
