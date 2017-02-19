package pokechu22.test.begradle.baseedit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.gradle.api.tasks.SourceSet;
import org.gradle.util.ConfigureUtil;

import groovy.lang.Closure;

/**
 * A "virtual directory" handler which manages settings for the base classes,
 * and injects them into the project's various source sets.
 * 
 * @see org.gradle.api.plugins.antlr.AntlrSourceVirtualDirectory
 *      AntlrSourceVirtualDirectory
 * @see org.gradle.api.plugins.antlr.internal.AntlrSourceVirtualDirectoryImpl
 *      AntlrSourceVirtualDirectoryImpl
 */
public class BaseClassesVirtualDirectory {
	public static final String NAME = "base";

	BaseClassesVirtualDirectory(SourceSet sourceSet) {
		String sourceSetName = sourceSet.getName();
		this.patches = "src/" + sourceSetName + "/base-patches";
		this.patchedSource = "src/" + sourceSetName + "/base";
		this.baseClasses = new ArrayList<>();
	}

	private Object patches;
	private Object patchedSource;
	private List<String> baseClasses;

	public void setPatches(Object folder) {
		this.patches = folder;
	}

	public Object getPatches() {
		return patches;
	}

	/**
	 * Gets a {@link Callable} that returns the value of {@link #getPatches}.
	 * 
	 * @return A callable that expands to {@link #getPatches}
	 */
	Callable<Object> getPatchesCallable() {
		return new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				return getPatches();
			}
		};
	}

	public void setPatchedSource(Object folder) {
		this.patchedSource = folder;
	}

	public Object getPatchedSource() {
		return patchedSource;
	}

	/**
	 * Gets a {@link Callable} that returns the value of {@link #getPatchedSource}.
	 * 
	 * @return A callable that expands to {@link #getPatchedSource}
	 */
	Callable<Object> getPatchedSourceCallable() {
		return new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				return getPatchedSource();
			}
		};
	}

	public void setBaseClasses(List<String> value) {
		this.baseClasses = value;
	}

	public List<String> getBaseClasses() {
		return baseClasses;
	}

	/**
	 * Gets a {@link Callable} that returns the value of {@link #getBaseClasses}.
	 * 
	 * @return A callable that expands to {@link #getBaseClasses}
	 */
	Callable<List<String>> getBaseClassesCallable() {
		return new Callable<List<String>>() {
			@Override
			public List<String> call() throws Exception {
				return getBaseClasses();
			}
		};
	}

	/**
	 * Configures the base patch settings.
	 * 
	 * @param configureClosure
	 *            A closure to configure the base patch settings with.
	 * @return {@code this}
	 */
	public BaseClassesVirtualDirectory base(Closure<?> configureClosure) {
		ConfigureUtil.configure(configureClosure, this);
		return this;
	}
}
