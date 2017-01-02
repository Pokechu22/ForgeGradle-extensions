package pokechu22.test.begradle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import difflib.DiffUtils;
import difflib.Patch;
import difflib.PatchFailedException;

public class ApplyBasePatchesTask extends DefaultTask {
	private Object patches;
	private Object patchedSource;
	private Object origJar;
	private Callable<List<String>> baseClasses;

	public ApplyBasePatchesTask() {
		this.onlyIf(new Spec<Task>() {
			@Override
			public boolean isSatisfiedBy(Task element) {
				// If there are no base classes, do nothing.
				return !((ApplyBasePatchesTask) element).getBaseClasses().isEmpty();
			}
		});
	}

	/**
	 * Sets the path to the folder to put patches.
	 */
	public void setPatches(Object folder) {
		this.patches = folder;
	}
	@OutputDirectory
	public File getPatches() {
		return getProject().file(this.patches);
	}
	/**
	 * Sets the path to the folder containing modified source.
	 */
	public void setPatchedSource(Object folder) {
		this.patchedSource = folder;
	}
	/**
	 * Gets the folder where modified source is.
	 */
	@InputDirectory
	public File getPatchedSource() {
		return getProject().file(patchedSource);
	}
	/**
	 * Sets the path to the unmodified, original deobfuscated source jar.
	 */
	public void setOrigJar(Object value) {
		this.origJar = value;
	}
	/**
	 * Gets the jar containing the unmodified, original deobfuscated source.
	 */
	@InputFile
	public File getOrigJar() {
		 return getProject().file(this.origJar);
	}
	/**
	 * Sets the list of modified base classes.
	 */
	public void setBaseClasses(Callable<List<String>> value) {
		this.baseClasses = value;
	}
	/**
	 * Gets the list of modified base classes.
	 */
	@Input
	public List<String> getBaseClasses() {
		try {
			return this.baseClasses.call();
		} catch (Exception e) {
			throw new RuntimeException("Exception while getting baseClasses", e);
		}
	}

	@TaskAction
	public void doTask() throws InvalidUserDataException, IOException {
		List<String> baseClasses = getBaseClasses();
		File inDir = getPatches();
		if (inDir.exists() && !inDir.isDirectory()) {
			throw new InvalidUserDataException(
					"Patches folder must be a directory!  (was: " + inDir + ")");
		}
		File outDir = getPatchedSource();
		if (outDir.exists() && !outDir.isDirectory()) {
			throw new InvalidUserDataException(
					"Modified base source folder must be a directory!  (was: "
							+ outDir + ")");
		}

		File origJar = getOrigJar();

		try (JarFile jar = new JarFile(origJar)) {
			Map<String, String> classPaths = new HashMap<>();
			// Convert class names into file paths.
			// We store the names in a map rather than computing them later so that
			// we can be sure all of the names are valid beforehand.
			for (String className : baseClasses) {
				classPaths.put(className, getPathForClass(className, jar));
			}

			// TODO: Do I want to back these up in some way?
			getLogger().lifecycle("Removing old base sources...");
			FileUtils.cleanDirectory(outDir);

			for (String className : baseClasses) {
				String classPath = classPaths.get(className);
				String patchName = className + ".patch";

				// Build the path to the modified class
				File patchedClassFile = outDir;
				for (String fragment : classPath.split("/")) {
					patchedClassFile = new File(patchedClassFile, fragment);
				}
				patchedClassFile.getParentFile().mkdirs();

				File patchFile = new File(inDir, patchName);

				List<String> newContent;

				List<String> origContent;
				try (InputStream input = jar.getInputStream(jar.getJarEntry(classPath))) {
					origContent = IOUtils.readLines(input, "UTF-8");
				}

				if (patchFile.exists()) {
					getLogger().lifecycle("Applying patch for {} as {}", className, patchName);

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
					getLogger().lifecycle("Copying {} as no patch exists",
							className, patchName);

					newContent = origContent;
				}

				FileUtils.writeLines(patchedClassFile, newContent);
			}
		}
	}

	/**
	 * Converts a class name into a path in the jar file, checking for potential errors.
	 * 
	 * @param className The user-inputted name of the class
	 * @param jar The jar to look in
	 * @return A path within the jar file. 
	 * @throws InvalidUserDataException on a bad path
	 * @throws InvalidUserDataException on a nonexistant file
	 */
	private String getPathForClass(String className, JarFile jar)
			throws InvalidUserDataException {
		if (className.contains("\\") || className.contains("/")) {
			// Enforce java-style names, mainly for convinence (and to get rid of
			// path char issues)
			throw new InvalidUserDataException(
					"Class names should be java-style names (com.example.Test)"
							+ ", not paths (com/example/Test.java)!  (Got: "
							+ className + ")");
		}
		String classPath = className.replace('.', '/') + ".java";
		// Make sure there is a file with that name.
		JarEntry entry = jar.getJarEntry(classPath);
		if (entry == null) {
			throw new InvalidUserDataException("Cannot find class '"
					+ className + "' in jar!  (Tried to find to '" + classPath
					+ "' in '" + getOrigJar() + "')");
		}
		return classPath;
	}
}
