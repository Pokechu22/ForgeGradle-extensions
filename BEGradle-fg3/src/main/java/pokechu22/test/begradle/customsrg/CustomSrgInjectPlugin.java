package pokechu22.test.begradle.customsrg;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.InvalidPluginException;
import org.gradle.api.plugins.PluginCollection;

import com.google.common.collect.Maps;

import net.minecraftforge.gradle.common.util.HashFunction;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.MinecraftExtension;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.userdev.UserDevPlugin;
import net.minecraftforge.srgutils.IMappingFile;

/**
 * A plugin that handles custom SRG tasks.
 */
public class CustomSrgInjectPlugin implements Plugin<Project> {
	/**
	 * Associated project
	 */
	protected Project project;
	/**
	 * Contains the list of additional SRGs to add.
	 */
	protected ExtraSrgContainer extraSrgContainer = new ExtraSrgContainer();

	@Override
	public void apply(Project project) {
		this.project = project;
		checkNoAssociatedPlugin();

		// Add configuration support
		new DslObject(project).getConvention().getPlugins()
				.put("extraSrgs", extraSrgContainer.new ConfigurationDelegate());

		project.getLogger().debug("Preparing afterEvaluate for SRG injection");
		project.afterEvaluate(project_ -> {
			project_.getLogger().debug("Calling afterEvaluate for SRG injection");
			if (project_.getState().getFailure() != null) {
				project_.getLogger().debug("Failed, aborting!");
				return;
			}

			afterEvaluate();
		});
	}

	/**
	 * HACKY.  Tries to find the active FG plugin.  In this case, we want to run before it
	 * (unlike with FG2), so there shouldn't be one.
	 */
	private void checkNoAssociatedPlugin() {
		PluginCollection<UserDevPlugin> plugins =
				project.getPlugins().withType(UserDevPlugin.class);

		if (plugins.size() >= 1) {
			throw new RuntimeException(new InvalidPluginException(
					"Can't set up custom SRGs - FG plugin already set up: " + plugins));
		}
	}

	/**
	 * Called after the project is evaluated.
	 * 
	 * @see Project#afterEvaluate(org.gradle.api.Action)
	 */
	protected void afterEvaluate() {
		if (!extraSrgContainer.hasAny()) {
			project.getLogger().warn("No custom SRGs present, despite injection plugin!");
			return;
		}

		// We need to add the forge maven now, since it's used to download the mappings
		// and otherwise we'd try to download them before UserDevPlugin adds it
		project.getRepositories().maven(e -> {
			e.setUrl(Utils.FORGE_MAVEN);
		});

		if (extraSrgContainer.hasSrgs()) {
			prepareCustomSrg();
		}
		if (extraSrgContainer.hasCsvs()) {
			prepareCustomCsvs();
		}
	}

	protected void prepareCustomSrg() {
		Configuration minecraft = project.getConfigurations().getByName("minecraft");
		DependencySet deps = minecraft.getDependencies();
		if (deps.size() != 1) {
			throw new RuntimeException("Expected only one minecraft dependency, but there were " + deps);
		}
		Dependency dep = deps.iterator().next();
		if (!(dep instanceof ExternalModuleDependency)) {
			throw new RuntimeException("Expected an ExternalModuleDependency, but was a " + dep.getClass() + " (" + dep + ")");
		}
		String newVersion = dep.getVersion() + "-" + extraSrgContainer.getSrgSpecifier();
		String newArtifact = dep.getGroup() + ":" + dep.getName() + ":" + newVersion;
		// Trick FG3 into using a new MCPConfig.  This only works for a direct MC dependency at the moment.
		String mcpConfig = "de.oceanlabs.mcp:mcp_config:" + dep.getVersion() + "@zip";
		String newMcpConfigPath = "de/oceanlabs/mcp/mcp_config/" + newVersion +
				"/mcp_config-" + newVersion + ".zip";

		File origConfig = MavenArtifactDownloader.manual(project, mcpConfig, false); // performs a download
		if (origConfig == null) {
			throw new RuntimeException("Failed to resolve " + mcpConfig);
		}

		File newConfig = Utils.getCache(project, "maven_downloader", newMcpConfigPath);

		try {
			Files.deleteIfExists(newConfig.toPath());
			createModifiedMcpConfig(origConfig, newConfig);
			Utils.updateHash(newConfig, HashFunction.MD5);
		} catch (IOException ex) {
			project.getLogger().error("Failed to create new SRGs!", ex);
			throw new UncheckedIOException(ex);
		}

		project.getLogger().lifecycle("NEW DEP " + newArtifact);
		project.getLogger().lifecycle("Deplist" + deps);
		deps.remove(dep);
		project.getLogger().lifecycle("Deplist" + deps);
		deps.add(project.getDependencies().create(newArtifact));
		project.getLogger().lifecycle("Deplist" + deps);
	}

	protected void createModifiedMcpConfig(File origConfig, File newConfig) throws IOException {
		try (ZipFile file = new ZipFile(origConfig)) {

			newConfig.getParentFile().mkdirs();
			try (ZipOutputStream stream = new ZipOutputStream(new FileOutputStream(newConfig))) {
				Enumeration<? extends ZipEntry> entries = file.entries(); // bad API :(
				while (entries.hasMoreElements()) {
					ZipEntry entry = entries.nextElement();
					if (entry.getName().equals("config/joined.tsrg")) {
						ZipEntry newEntry = new ZipEntry(entry.getName());
						newEntry.setTime(entry.getTime());
						stream.putNextEntry(newEntry);
						try (InputStream istream = file.getInputStream(entry)) {
							writeRemappedSrg(istream, stream);
						}
					} else {
						stream.putNextEntry(entry);
						try (InputStream istream = file.getInputStream(entry)) {
							IOUtils.copy(istream, stream);
						}
					}
				}
			}
		}
	}

	protected void writeRemappedSrg(InputStream inSrg, OutputStream outSrg) throws IOException {
		IMappingFile mapping = IMappingFile.load(inSrg);
		for (File file : extraSrgContainer.getSrgs()) {
			mapping = mapping.chain(IMappingFile.load(file));
		}
		Path tempFile = Files.createTempFile("RemappedSRG", ".tsrg");
		tempFile.toFile().deleteOnExit();
		mapping.write(tempFile, IMappingFile.Format.TSRG, false); // No write to stream :|
		try (InputStream s = Files.newInputStream(tempFile, StandardOpenOption.DELETE_ON_CLOSE)) {
			IOUtils.copy(s, outSrg);
		}
	}

	protected void prepareCustomCsvs() {
		MinecraftExtension minecraft = project.getExtensions().findByType(MinecraftExtension.class);
		// AWFUL hack: manually put the file in the expected place
		// Note that to my understanding it'll still make a web request to
		// check for it, but that'll fail (since it doesn't exist remotely)
		// and thus it'll decide to use the local one that matches

		// This isn't cached (so it'll be downloaded each time for each version), but the
		// actual decompilation is cached assuming the generated file's hashes stay the same.
		String channel = minecraft.getMappingChannel();
		String origVersion = minecraft.getMappingVersion();
		String newVersion = origVersion + "-" + extraSrgContainer.getSpecifier();

		String artifact = "de.oceanlabs.mcp:mcp_" + channel + ":" + origVersion + "@zip";
		String newCsvsPath = "de/oceanlabs/mcp/mcp_" + channel + "/" + newVersion +
				"/mcp_" + channel + "-" + newVersion + ".zip";

		File origCsvs = MavenArtifactDownloader.manual(project, artifact, false); // performs a download
		if (origCsvs == null) {
			throw new RuntimeException("Failed to resolve " + artifact);
		}

		File newCsvs = Utils.getCache(project, "maven_downloader", newCsvsPath);

		try {
			Files.deleteIfExists(newCsvs.toPath());
			createMergedCsvs(origCsvs, newCsvs);
			Utils.updateHash(newCsvs, HashFunction.MD5);
		} catch (IOException ex) {
			project.getLogger().error("Failed to create new CSVs!", ex);
			throw new UncheckedIOException(ex);
		}

		// This is enough to trick FG3 into using our new mappings
		minecraft.mappings(channel, newVersion);
	}

	protected void createMergedCsvs(File origCsvs, File newCsvs) throws IOException {
		LinkedHashMap<String, String[]> methods = Maps.newLinkedHashMap();
		LinkedHashMap<String, String[]> fields = Maps.newLinkedHashMap();
		LinkedHashMap<String, String[]> params = Maps.newLinkedHashMap();

		// Use a consistent timestamp, to help get a consistent hash
		long timestamp;
		try (ZipFile file = new ZipFile(origCsvs)) {
			ZipEntry methodsEntry = file.getEntry("methods.csv");
			ZipEntry fieldsEntry = file.getEntry("fields.csv");
			ZipEntry paramsEntry = file.getEntry("params.csv");
			timestamp = methodsEntry.getTime();
			ExtraSrgUtil.readCSVInto(new InputStreamReader(file.getInputStream(methodsEntry)), methods);
			ExtraSrgUtil.readCSVInto(new InputStreamReader(file.getInputStream(fieldsEntry)), fields);
			ExtraSrgUtil.readCSVInto(new InputStreamReader(file.getInputStream(paramsEntry)), params);
		}

		for (File file : extraSrgContainer.getMethods()) {
			ExtraSrgUtil.readCSVInto(file, methods);
		}
		for (File file : extraSrgContainer.getFields()) {
			ExtraSrgUtil.readCSVInto(file, fields);
		}
		for (File file : extraSrgContainer.getParams()) {
			ExtraSrgUtil.readCSVInto(file, params);
		}

		class NoCloseWriter extends OutputStreamWriter {
			public NoCloseWriter(OutputStream stream) {
				super(stream);
			}
			@Override
			public void close() throws IOException {
				// Do nothing
			}
		}

		newCsvs.getParentFile().mkdirs();
		try (ZipOutputStream stream = new ZipOutputStream(new FileOutputStream(newCsvs));
				Writer writer = new NoCloseWriter(stream)) {
			ZipEntry methodsEntry = new ZipEntry("methods.csv");
			methodsEntry.setTime(timestamp);
			ZipEntry fieldsEntry = new ZipEntry("fields.csv");
			fieldsEntry.setTime(timestamp);
			ZipEntry paramsEntry = new ZipEntry("params.csv");
			paramsEntry.setTime(timestamp);

			stream.putNextEntry(methodsEntry);
			ExtraSrgUtil.writeCSV(writer, methods, false);
			stream.putNextEntry(fieldsEntry);
			ExtraSrgUtil.writeCSV(writer, fields, false);
			stream.putNextEntry(paramsEntry);
			ExtraSrgUtil.writeCSV(writer, params, true);
			stream.closeEntry();
		}
	}
}
