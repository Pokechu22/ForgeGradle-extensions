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
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import net.minecraftforge.gradle.userdev.tasks.RenameJar;
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
			project_.getLogger().debug("Calling outer afterEvaluate for SRG injection");
			if (project_.getState().getFailure() != null) {
				project_.getLogger().debug("Failed, aborting!");
				return;
			}

			if (!extraSrgContainer.hasAny()) {
				project.getLogger().warn("No custom SRGs present, despite injection plugin!");
				return;
			}

			project.afterEvaluate(project__ -> {
				project_.getLogger().debug("Calling nested afterEvaluate for SRG injection");
				if (project_.getState().getFailure() != null) {
					project_.getLogger().debug("Failed, aborting!");
					return;
				}

				afterEvaluateAfterFG();
			});

			afterEvaluateBeforeFG();
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
	 * Called after the project is evaluated, but before ForgeGradle's afterEvaluate
	 * is called.
	 */
	protected void afterEvaluateBeforeFG() {
		// We need to add the forge maven now, since it's used to download the mappings
		// and otherwise we'd try to download them before UserDevPlugin adds it
		project.getRepositories().maven(e -> {
			e.setUrl(Utils.FORGE_MAVEN);
		});

		if (extraSrgContainer.hasSrgs()) {
			if (!isPatcherDep()) {
				prepareCustomSrgVanilla();
			}
		}
		if (extraSrgContainer.hasCsvs()) {
			prepareCustomCsvs();
		}
	}

	/**
	 * Called after the project is evaluated and after ForgeGradle's afterEvaluate
	 * is called.
	 */
	protected void afterEvaluateAfterFG() {
		if (extraSrgContainer.hasSrgs()) {
			if (isPatcherDep()) {
				prepareCustomSrgPatcher();
			}
		}
	}

	/**
	 * Checks if the current minecraft dependency is a patcher.
	 *
	 * @return True if it is a patcher.
	 */
	protected boolean isPatcherDep() {
		return !getMinecraftDep().getGroup().equals("net.minecraft");
	}

	/**
	 * Gets the current minecraft dependency, throwing an exception if there is an
	 * unexpected number of such dependencies.
	 *
	 * @return The minecraft dependency.
	 */
	protected Dependency getMinecraftDep() {
		Configuration minecraft = project.getConfigurations().getByName("minecraft");
		DependencySet deps = minecraft.getDependencies();
		//if (deps.size() != 1) {
		//	throw new RuntimeException("Expected only one minecraft dependency, but there were " + new ArrayList<>(deps));
		//}
		Dependency dep = deps.iterator().next();
		if (!(dep instanceof ExternalModuleDependency)) {
			throw new RuntimeException("Expected an ExternalModuleDependency, but was a " + dep.getClass() + " (" + dep + ")");
		}
		return dep;
	}

	/**
	 * Replaces the minecraft dependency with a different dependency.
	 *
	 * @param newArtifact The new minecraft dependency.
	 */
	protected void replaceMinecraftDep(String newArtifact) {
		Configuration minecraft = project.getConfigurations().getByName("minecraft");
		DependencySet deps = minecraft.getDependencies();
		deps.clear();
		deps.add(project.getDependencies().create(newArtifact));
	}

	/**
	 * Sets up the custom SRG and replaces the minecraft dependency. This verison
	 * works only if
	 */
	protected void prepareCustomSrgVanilla() {
		Dependency dep = getMinecraftDep();
		String newVersion = dep.getVersion() + "-" + extraSrgContainer.getSrgSpecifier();
		String newArtifact = dep.getGroup() + ":" + dep.getName() + ":" + newVersion;
		project.getLogger().info("Replacing " + dep + " with " + newArtifact);
		// Trick FG3 into using a new MCPConfig.  This only works for a direct MC dependency at the moment.
		String mcpConfig = "de.oceanlabs.mcp:mcp_config:" + dep.getVersion() + "@zip";
		String newMcpConfigPath = "de/oceanlabs/mcp/mcp_config/" + newVersion +
				"/mcp_config-" + newVersion + ".zip";

		// performs a download
		File origConfig = MavenArtifactDownloader.manual(project, mcpConfig, false);
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

		replaceMinecraftDep(newArtifact);
	}

	protected void createModifiedMcpConfig(File origConfig, File newConfig) throws IOException {
		// Create a copy for mutation
		Map<String, File> patches = new HashMap<>(extraSrgContainer.getPatches());
		long tsrgTime = 0; // If not found, whatever, it'll still produce a consistent time

		newConfig.getParentFile().mkdirs();

		try (ZipFile file = new ZipFile(origConfig);
				ZipOutputStream stream = new ZipOutputStream(new FileOutputStream(newConfig))) {
			Enumeration<? extends ZipEntry> entries = file.entries(); // bad API :(

			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName();
				if (name.equals("config/joined.tsrg")) {
					ZipEntry newEntry = new ZipEntry(entry.getName());
					tsrgTime = entry.getTime();
					newEntry.setTime(tsrgTime);
					stream.putNextEntry(newEntry);
					try (InputStream istream = file.getInputStream(entry)) {
						writeRemappedSrg(istream, stream);
					}
				} else if (name.startsWith("patches/")
						&& patches.containsKey(name.substring("patches/".length()))) {
					// Replacing a patch - remove it since we don't want to add it again later
					File patch = patches.remove(name.substring("patches/".length()));
					ZipEntry newEntry = new ZipEntry(entry.getName());
					newEntry.setTime(entry.getTime());
					stream.putNextEntry(newEntry);
					try (InputStream istream = Files.newInputStream(patch.toPath())) {
						IOUtils.copy(istream, stream);
					}
				} else {
					stream.putNextEntry(entry);
					try (InputStream istream = file.getInputStream(entry)) {
						IOUtils.copy(istream, stream);
					}
				}
			}

			for (Map.Entry<String, File> e : patches.entrySet()) {
				ZipEntry newEntry = new ZipEntry("patches/" + e.getKey());
				newEntry.setTime(tsrgTime);
				stream.putNextEntry(newEntry);
				try (InputStream istream = Files.newInputStream(e.getValue().toPath())) {
					IOUtils.copy(istream, stream);
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

	protected void prepareCustomSrgPatcher() {
		Dependency dep = getMinecraftDep();
		String group = dep.getGroup();
		String name = dep.getName();
		String version = dep.getVersion();
		String[] parts = version.split("_", 2);
		// 1.15.2-31.1.0_mapped_snapshot_20200728-1.15.1 to
		// 1.15.2-31.1.0-custom-3135772e181398b7_mapped_snapshot_20200728-1.15.1
		// (ForgeGradle needs the stuff after the underscore to match normal mappings)
		String newVersion = parts[0] + "-" + extraSrgContainer.getSrgSpecifier() + "_" + parts[1];

		String realArtifact = group + ":" + name + ":" + version;
		String newArtifact = group + ":" + name + ":" + newVersion;
		String newPath = group.replace('.', '/') + "/" + name + "/" + newVersion + "/" +
				name + "-" + newVersion + ".jar";
		String newPathPom = group.replace('.', '/') + "/" + name + "/" + newVersion + "/" +
				name + "-" + newVersion + ".pom";

		// This is even more expensive than downloading it, since we don't do any caching :|
		File realPatcher = MavenArtifactDownloader.generate(project, realArtifact, false);
		File realPatcherPom = MavenArtifactDownloader.generate(project, realArtifact + "@pom", false);
		if (realPatcher == null || realPatcherPom == null) {
			throw new RuntimeException("Failed to resolve real patcher " + realArtifact);
		}

		File newPatcher = Utils.getCache(project, "maven_downloader", newPath);
		File newPatcherPom = Utils.getCache(project, "maven_downloader", newPathPom);

		try {
			Files.deleteIfExists(newPatcher.toPath());
			remapPatcherBinary(realPatcher, newPatcher);
			Utils.updateHash(newPatcher, HashFunction.SHA1);

			Files.copy(realPatcherPom.toPath(), newPatcherPom.toPath(), StandardCopyOption.REPLACE_EXISTING);
			Utils.updateHash(newPatcherPom, HashFunction.SHA1);
		} catch (IOException ex) {
			project.getLogger().error("Failed to create new patcher!", ex);
			throw new UncheckedIOException(ex);
		}

		replaceMinecraftDep(newArtifact);
	}

	protected void remapPatcherBinary(File origPatcher, File newPatcher) throws IOException {
		newPatcher.getParentFile().mkdirs();

		List<IMappingFile> mappingFiles = new ArrayList<>();
		for (File file : extraSrgContainer.getSrgs()) {
			mappingFiles.add(IMappingFile.load(file));
		}
		Optional<IMappingFile> mappings = mappingFiles.stream().reduce(IMappingFile::chain);
		if (!mappings.isPresent()) {
			project.getLogger().error("No mappings?  List " +
					extraSrgContainer.getSrgs() + ", read to " + mappingFiles);
		}

		Path tempFile = Files.createTempFile("RemappedSRG", ".tsrg");
		tempFile.toFile().deleteOnExit();
		mappings.get().write(tempFile, IMappingFile.Format.TSRG, false);

		// Task usage seems dubious to me, but is what FG3 does elsewhere
		RenameJar rename = project.getTasks().create("remapPatcher", RenameJar.class);
		rename.setHasLog(false);
		rename.setInput(origPatcher);
		rename.setOutput(newPatcher);
		rename.setMappings(tempFile.toFile());
		rename.apply();
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
