package pokechu22.test.begradle;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public class GenerateBasePatchesTask extends DefaultTask {
	private Object patches;
	private Object patchedSource;
	private Object origJar;
	private Callable<List<String>> baseClasses;

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
	public void doTask() {
		
	}
}
