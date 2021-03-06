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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.InvalidPluginException;
import org.gradle.api.plugins.PluginCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

import com.google.common.collect.Maps;

import net.minecraftforge.gradle.common.util.HashFunction;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.MinecraftExtension;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.userdev.UserDevPlugin;
import net.minecraftforge.gradle.userdev.tasks.RenameJarInPlace;
import net.minecraftforge.srgutils.IMappingFile;
import pokechu22.test.begradle.baseedit.BaseEditPlugin;

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
	 * HACKY. After evaluate, we now want to find a plugin, to handle additional
	 * remapping.
	 */
	private UserDevPlugin findNewlyAssociatedPlugin() {
		PluginCollection<UserDevPlugin> plugins =
				project.getPlugins().withType(UserDevPlugin.class);

		if (plugins.size() == 0) {
			throw new RuntimeException(new InvalidPluginException(
					"Can't set up custom SRGs - can't find a normal FG plugin."));
		}
		if (plugins.size() > 1) {
			throw new RuntimeException(new InvalidPluginException(
					"Can't set up custom SRGs - more than 1 FG plugin: " + plugins +
					" (if this is a valid configuration, sorry; I didn't design around it)"));
		}

		return plugins.iterator().next();
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
		project.getLogger().info("Replacing " + dep + " with " + newArtifact);
		// Trick FG3 into using a new MCPConfig.  This only works for a direct MC dependency at the moment.
		String mcpConfig = "de.oceanlabs.mcp:mcp_config:" + dep.getVersion() + "@zip";
		String newMcpConfigPath = "de/oceanlabs/mcp/mcp_config/" + newVersion +
				"/mcp_config-" + newVersion + ".zip";

		boolean isPatcher = !dep.getGroup().equals("net.minecraft");

		if (isPatcher) {
			// Do further work AFTER ForgeGradle does its afterEvaluate.
			// This second afterEvaluate (remember, we're currently in one) is called after
			// all other ones finish.
			throw new RuntimeException("Custom SRGs don't currently work with patcher projects!");
			/*project.afterEvaluate(project -> {
				deps.remove(dep);
				deps.add(project.getDependencies().create(newArtifact));

				RemapAfterRepo remapRepo = new RemapAfterRepo(project, dep.getGroup(), dep.getName(),
						newVersion, dep.getVersion(), extraSrgContainer);

				UserDevExtension extension = project.getExtensions().findByType(UserDevExtension.class);
				MinecraftUserRepo mcrepo = new MinecraftUserRepo(project, dep.getGroup(), dep.getName(),
						dep.getVersion(), extension.getAccessTransformers(), extension.getMappings()) {
					@Override
					public void validate(Configuration cfg, Map<String, RunConfig> runs, ExtractNatives extractNatives, DownloadAssets downloadAssets, GenerateSRG createSrgToMcp) {
						project.getLogger().info("validate()");
					}
				};
				DeobfuscatingRepo deobfrepo = null; // We don't care about this yet
				new BaseRepo.Builder()
						.add(mcrepo)
						.add(remapRepo)
						.add(deobfrepo)
						.add(MCPRepo.create(project))
						.add(MinecraftRepo.create(project))
						.attach(project);
			});*/
		} else {
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

			deps.remove(dep);
			deps.add(project.getDependencies().create(newArtifact));
		}

		// TODO: After patcher reintroduced, this code may need to be moved.
		UserDevPlugin plugin = findNewlyAssociatedPlugin();

		// We don't need to adjust the reobf tasks for baseedits, since they reobf to Notch names.
		if (!(plugin instanceof BaseEditPlugin)) {
			adjustReobfTasks();
		}
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

	// TODO: This is mostly a duplicate of writeRemappedSrg
	public static class GenerateReverseExtraSrg extends DefaultTask {
		private File output = getProject().file("build/" + getName() + "/output.tsrg");
		private List<File> extraSrgs;

		@InputFiles
		public List<File> getExtraSrgs() {
			return extraSrgs;
		}
		public void setExtraSrgs(List<File> value) {
			this.extraSrgs = value;
		}

		@OutputFile
		public File getOutput() {
			return output;
		}
		public void setOutput(File value) {
			this.output = value;
		}

		@TaskAction
		public void apply() throws IOException, NoSuchElementException {
			List<IMappingFile> mappings = new ArrayList<>(extraSrgs.size());
			for (File file : extraSrgs) {
				mappings.add(IMappingFile.load(file).reverse());
			}
			IMappingFile mapping = mappings.stream().reduce(IMappingFile::chain).get();
			mapping.write(output.toPath(), IMappingFile.Format.TSRG, false);
		}
	}

	// This must be run in a 2nd-level afterEvaluate, so that the main plugin has
	// had its afterEvaluate called.
	protected void adjustReobfTasks() {
		TaskProvider<GenerateReverseExtraSrg> createReverseExtraSrg = project.getTasks()
				.register("createReverseExtraSrg", GenerateReverseExtraSrg.class);

		createReverseExtraSrg.configure(task -> {
			task.setExtraSrgs(extraSrgContainer.getSrgs());
		});

		// NOTE: I previously used setMappings here, but that didn't work for some reason
		// (as if FG was overwriting my mappings).  No idea why, but extraMapping is fine.
		project.getTasks().withType(RenameJarInPlace.class, task -> {
			task.dependsOn(createReverseExtraSrg);
			task.extraMapping(createReverseExtraSrg.get().getOutput());
		});
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
