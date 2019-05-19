package pokechu22.test.begradle.baseedit;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import difflib.DiffUtils;
import difflib.Patch;
import difflib.PatchFailedException;

/**
 * A task that works with patches (applying or generating).
 * 
 * Note that it is the client's responsibility to add appropriate Input/Output
 * annotations to patches/patched source.
 */
public abstract class AbstractPatchingTask extends DefaultTask {
	private Object patches;
	private Object patchedSource;
	private Object origJar;
	private Callable<List<String>> baseClasses;

	@TaskAction
	public void validate() {
		// Validate all of the path names
		File origJar = getOrigJar();
		if (!origJar.exists()) {
			throw new UncheckedIOException(new FileNotFoundException(
					"Cannot find unmodified Minecraft source jar - "
							+ "tried " + origJar));
		}

		try (JarFile jar = new JarFile(origJar)) {
			List<String> classes = this.getBaseClasses();
			for (String className : classes) {
				if (className.contains("\\") || className.contains("/")) {
					// Enforce java-style names, mainly for
					// convenience (and to get rid of path char
					// issues)
					throw new InvalidUserDataException(
							"Class names should be java-style names"
							+ "(eg com.example.Test), not paths "
							+ "(eg com/example/Test.java)!  "
							+ "(Got: " + className + ")");
				}

				JarEntry entry = getJarEntry(className, jar, true);
				if (entry == null) {
					throw new InvalidUserDataException(
							"There is no class named '" + className
							+ "' in the source jar (" + origJar + ")!");
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Sets the path to the folder that does/will contain patches.
	 * @param folder The folder that does/will contain patches.
	 */
	public void setPatches(Object folder) {
		this.patches = folder;
	}
	/**
	 * Gets the folder that does/will contain patches. 
	 * @return The folder that does/will contain patches.
	 */
	public File getPatches() {
		// NOTE: Calls to mkdirs are due to https://github.com/gradle/gradle/issues/2016:
		// there is no way to specify an InputDirectory that doesn't exist.
		// However, we only want to call it if there are actual patches;
		// no point in making a folder that won't be used.
		File patches = getProject().file(this.patches);
		if (!getBaseClasses().isEmpty() && !patches.exists()) {
			patches.mkdirs();
		}
		return patches;
	}
	/**
	 * Sets the folder that does/will contain modified source. 
	 * @param folder The folder that does/will contain modified source.
	 */
	public void setPatchedSource(Object folder) {
		this.patchedSource = folder;
	}
	/**
	 * Gets the folder that does/will contain modified source. 
	 * @return The folder that does/will contain modified source.
	 */
	public File getPatchedSource() {
		// NOTE: Calls to mkdirs are due to https://github.com/gradle/gradle/issues/2016:
		// there is no way to specify an InputDirectory that doesn't exist.
		// However, we only want to call it if there are actual patches;
		// no point in making a folder that won't be used.
		File patchedSource = getProject().file(this.patchedSource);
		if (!getBaseClasses().isEmpty() && !patchedSource.exists()) {
			patchedSource.mkdirs();
		}
		return patchedSource;
	}
	/**
	 * Sets the path to the unmodified, original deobfuscated source jar.
	 * @param value The path to the original, deobfuscated source jar.
	 */
	public void setOrigJar(Object value) {
		this.origJar = value;
	}
	/**
	 * Gets the jar containing the unmodified, original deobfuscated source.
	 * @return The path to the deobfuscated source jar.
	 */
	@InputFile
	public File getOrigJar() {
		 return getProject().file(this.origJar);
	}
	/**
	 * Sets the list of modified base classes.
	 * @param value A {@link Callable} that returns the list of base classes.
	 */
	public void setBaseClasses(Callable<List<String>> value) {
		this.baseClasses = value;
	}
	/**
	 * Gets the list of modified base classes.
	 * @return A list of base classes, in the normal java format.
	 */
	@Input
	public List<String> getBaseClasses() {
		try {
			return this.baseClasses.call();
		} catch (Exception e) {
			throw new RuntimeException("Exception while getting baseClasses", e);
		}
	}

	/**
	 * Converts a class name into the File that points to the patch for that class.
	 * 
	 * @param className
	 *            The user-inputed name of the class
	 * @return The File that refers to the patch for that class. May not exist.
	 */
	protected File getPatch(String className) {
		return new File(getPatches(), className + ".patch");
	}

	/**
	 * Converts a class name into the File that points to the modified source
	 * for that class.
	 * 
	 * @param className
	 *            The user-inputed name of the class
	 * @return The File that refers to the modified source for that class. May
	 *         not exist.
	 */
	protected File getPatchedSource(String className) {
		String[] parts = className.split("\\.");
		File file = getPatchedSource();
		// Build the subdirectories.
		for (int i = 0; i < parts.length - 1; i++) {
			file = new File(file, parts[i]);
		}
		// We need to add the '.java' only to the last item.
		file = new File(file, parts[parts.length - 1] + ".java");
		return file;
	}

	/**
	 * Converts a class name into a JarEntry.
	 * 
	 * @param className
	 *            The user-inputed name of the class
	 * @param jar
	 *            The jar to look in
	 * @param allowMissing
	 *            Allow returning null if the class does not exist.
	 * @return The JarEntry for that class.
	 */
	protected JarEntry getJarEntry(String className, JarFile jar, boolean allowMissing) {
		String path = className.replace('.', '/') + ".java";
		JarEntry entry = jar.getJarEntry(path);
		if (!allowMissing) {
			if (entry == null) {
				// Should not happen because this is validated beforehand
				throw new RuntimeException("Unexpected null jar entry for " + className + " (-> " + path + ") in " + jar + " -- this should not happen!");
			}
		}
		return entry;
	}

	/**
	 * Applies a patch to the class.
	 *
	 * @param className The name of the class to patch (java-style)
	 * @param jar The jar containing unmodified base sources.
	 * @throws IOException When an IO problem occurs
	 */
	protected void applyPatch(String className, JarFile jar) throws IOException {
		File sourceFile = getPatchedSource(className);
		File patchFile = getPatch(className);

		sourceFile.getParentFile().mkdirs();

		List<String> newContent;

		List<String> origContent;
		try (InputStream input = jar.getInputStream(getJarEntry(className, jar, false))) {
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
				getLogger().warn("WARNING: Patching failed for " + className + "!  " + e);
				getLogger().info("Exception: " + e);
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

	/**
	 * Makes a patch for the given class.
	 *
	 * @param className The name of the class to make a patch for (java-style)
	 * @param jar The jar containing unmodified base sources.
	 * @throws IOException When an IO problem occurs
	 */
	protected void genPatch(String className, JarFile jar) throws IOException {
		File sourceFile = getPatchedSource(className);
		File patchFile = getPatch(className);
		// For the unified diff
		String filename = className.replace('.', '/') + ".java";

		patchFile.getParentFile().mkdirs();

		getLogger().lifecycle("Generating patch for {} from {} to {}",
				className, sourceFile, patchFile);

		List<String> origContent;
		try (InputStream input = jar.getInputStream(getJarEntry(className, jar, false))) {
			origContent = IOUtils.readLines(input, "UTF-8");
		}

		List<String> newContent = FileUtils.readLines(sourceFile, "UTF-8");

		Patch<String> patch = DiffUtils.diff(origContent, newContent);
		List<String> diff = DiffUtils.generateUnifiedDiff(filename,
				filename, origContent, patch, 3);

		FileUtils.writeLines(patchFile, diff);
	}
}
