package pokechu22.test.begradle.customsrg;

import groovy.lang.Closure;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.gradle.util.ConfigureUtil;

/**
 * A collection of additional SRG files.
 */
public final class ExtraSrgContainer implements Iterable<File> {
	private final CustomSrgInjectPlugin plugin;

	ExtraSrgContainer(CustomSrgInjectPlugin plugin) {
		this.plugin = plugin; 
	}

	private List<File> srgs = new LinkedList<>();

	/**
	 * Returns an iterator over the list of SRGs in this container.
	 *
	 * @see List#iterator()
	 */
	@Override
	public Iterator<File> iterator() {
		return this.srgs.iterator();
	}

	/**
	 * Gets a specifying string for this container, designed to allow for useful
	 * caching without overwriting.  The specifier is designed to be mostly
	 * unique, but that is not guaranteed.
	 *
	 * @return The specifier, or null if no extra SRGs
	 */
	public String getSpecifier() {
		if (!hasAny()) {
			return null;
		}

		String[] names = new String[srgs.size()];
		int i = 0;
		for (File file : srgs) {
			names[i++] = FilenameUtils.getBaseName(file.getAbsolutePath());
		}
		Arrays.sort(names);
		StringBuilder sb = new StringBuilder("custom");
		for (String s : names) {
			sb.append('_').append(s);
		}
		return sb.toString();
	}

	public boolean hasAny() {
		return !this.srgs.isEmpty();
	} 

	public void add(File file) {
		this.srgs.add(file);
		// Ugly, but needed so that it's enabled _before_ the dependencies ask for it.
		plugin.forceLocalCache();
	}

	/**
	 * Handles configuration of an ExtraSrgContainer.
	 */
	public class ConfigurationDelegate {
		protected ConfigurationDelegate() {}

		/**
		 * Configures the extra SRGs.
		 * @param configureClosure closure to configure with.
		 */
		public ExtraSrgContainer extraSrgs(Closure<?> configureClosure) {
			return ConfigureUtil.configure(configureClosure, ExtraSrgContainer.this);
		}
	}
}
