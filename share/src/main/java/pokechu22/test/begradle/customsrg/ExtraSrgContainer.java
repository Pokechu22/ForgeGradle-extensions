package pokechu22.test.begradle.customsrg;

import groovy.lang.Closure;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.gradle.util.ConfigureUtil;

/**
 * A collection of additional SRG files (and other data).
 */
public final class ExtraSrgContainer {
	/**
	 * Called as soon as a value is set.  A rather silly thing needed for FG 2
	 * (which needs this called as soon as possible; it can't wait until after
	 * configuration has finished as dependencies will ask for it before then)
	 */
	private final Runnable forceLocalCache;

	ExtraSrgContainer() {
		this(() -> {});
	}

	ExtraSrgContainer(Runnable forceLocalCache) {
		this.forceLocalCache = forceLocalCache;
	}

	private final List<File> srgs = new LinkedList<>();
	private final List<File> methods = new LinkedList<>();
	private final List<File> fields = new LinkedList<>();
	private final List<File> params = new LinkedList<>();
	private final Map<String, File> patches = new HashMap<>();

	/**
	 * Gets a list of all extra SRGs.
	 *
	 * @return The extra SRGs.
	 */
	public List<File> getSrgs() {
		return srgs;
	}

	/**
	 * Gets a list of all extra method CSVs.
	 *
	 * @return The extra method CSVs.
	 */
	public List<File> getMethods() {
		return methods;
	}

	/**
	 * Gets a list of all extra field CSVs.
	 *
	 * @return The extra field CSVs.
	 */
	public List<File> getFields() {
		return fields;
	}

	/**
	 * Gets a list of all extra parameter CSVs.
	 *
	 * @return The extra parameter CSVs.
	 */
	public List<File> getParams() {
		return params;
	}

	/**
	 * Gets a list of all extra patches.
	 *
	 * @return The extra patches.
	 */
	public Map<String, File> getPatches() {
		return patches;
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

		String[] names = new String[srgs.size() + methods.size() + fields.size() + params.size()];
		int i = 0;
		for (File file : srgs) {
			names[i++] = "s_" + FilenameUtils.getBaseName(file.getAbsolutePath());
		}
		for (File file : methods) {
			names[i++] = "m_" + FilenameUtils.getBaseName(file.getAbsolutePath());
		}
		for (File file : fields) {
			names[i++] = "f_" + FilenameUtils.getBaseName(file.getAbsolutePath());
		}
		for (File file : params) {
			names[i++] = "p_" + FilenameUtils.getBaseName(file.getAbsolutePath());
		}
		Arrays.sort(names);
		StringBuilder sb = new StringBuilder();
		for (String s : names) {
			sb.append('_').append(s);
		}
		// Prevent excessively long file names by taking a hash (and not even
		// using the full hash in that case)
		return "custom-" + DigestUtils.shaHex(sb.toString()).substring(0, 16);
	}

	/**
	 * @return True if there are any custom SRGs, methods, fields, or patches
	 */
	public boolean hasAny() {
		return !this.srgs.isEmpty() || !this.methods.isEmpty() ||
				!this.fields.isEmpty() || !this.params.isEmpty() || !this.patches.isEmpty();
	} 

	/**
	 * @deprecated use {@link #addSrg(File)}
	 * @param file The extra SRG to add.
	 */
	@Deprecated
	public void add(File file) {
		addSrg(file);
	}

	/**
	 * Adds a custom SRG file, remapping classes.
	 * @param file The new SRG.
	 */
	public void addSrg(File file) {
		this.srgs.add(file);
		forceLocalCache.run();
	}

	/**
	 * Adds a custom methods CSV file, remapping methods.
	 * @param file The new methods CSV.
	 */
	public void addMethods(File file) {
		this.methods.add(file);
		forceLocalCache.run();
	}

	/**
	 * Adds a custom fields CSV file, remapping fields.
	 * @param file The new fields CSV.
	 */
	public void addFields(File file) {
		this.fields.add(file);
		forceLocalCache.run();
	}

	/**
	 * Adds a custom parameters CSV file, remapping parameters.
	 * @param file The new fields CSV.
	 */
	public void addParams(File file) {
		this.params.add(file);
		forceLocalCache.run();
	}

	/**
	 * Adds a custom MCP patch.  NOT intended for use other than to fix decompile errors.
	 * @param inFile The file to patch, including the <code>.java</code> extension.
	 * @param patch The patch to use
	 */
	public void addPatch(String inFile, File patch) {
		this.patches.put(inFile, patch);
		forceLocalCache.run();
	}

	/**
	 * Handles configuration of an ExtraSrgContainer.
	 */
	public class ConfigurationDelegate {
		protected ConfigurationDelegate() {}

		/**
		 * Configures the extra SRGs.
		 * @param configureClosure closure to configure with.
		 * @return The same extra SRG container (for chaining, perhaps)
		 */
		public ExtraSrgContainer extraSrgs(Closure<?> configureClosure) {
			return ConfigureUtil.configure(configureClosure, ExtraSrgContainer.this);
		}
	}
}
