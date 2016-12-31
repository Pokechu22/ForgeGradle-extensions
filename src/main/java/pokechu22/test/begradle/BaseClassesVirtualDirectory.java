package pokechu22.test.begradle;

import org.gradle.api.tasks.SourceSet;
import org.gradle.util.ConfigureUtil;

import groovy.lang.Closure;

/**
 * A "virtual directory" handler which injects base classes into the project's
 * various source sets.
 * 
 * @see org.gradle.api.plugins.antlr.AntlrSourceVirtualDirectory
 *      AntlrSourceVirtualDirectory
 * @see org.gradle.api.plugins.antlr.internal.AntlrSourceVirtualDirectoryImpl
 *      AntlrSourceVirtualDirectoryImpl
 */
public class BaseClassesVirtualDirectory {
	public static final String NAME = "base";

	BaseClassesVirtualDirectory(SourceSet sourceSet) {
		this.base = new BasePatchSettings(sourceSet.getName());
	}

	private final BasePatchSettings base;

	/**
	 * Gets the {@link BasePatchSettings} associated with this directory.
	 * 
	 * @return The base patch settings
	 */
	public BasePatchSettings getPatchSettings() {
		return base;
	}

	/**
	 * Configures the base patch settings.
	 * 
	 * @param configureClosure
	 *            A closure to configure the base patch settings with.
	 * @return {@code this}
	 */
	public BaseClassesVirtualDirectory base(Closure<?> configureClosure) {
		ConfigureUtil.configure(configureClosure, base);
		return this;
	}
}
