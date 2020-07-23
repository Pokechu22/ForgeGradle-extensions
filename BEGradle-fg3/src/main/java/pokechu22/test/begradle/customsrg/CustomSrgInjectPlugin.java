package pokechu22.test.begradle.customsrg;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.InvalidPluginException;
import org.gradle.api.plugins.PluginCollection;

import com.google.common.collect.Maps;

import net.minecraftforge.gradle.common.util.HashFunction;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.MinecraftExtension;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.userdev.UserDevPlugin;

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

		File newCsvs = Utils.getCache(project, "maven_downloader", newCsvsPath);

		try {
			Files.deleteIfExists(newCsvs.toPath());
			processCsvs(origCsvs, newCsvs);
			Utils.updateHash(newCsvs, HashFunction.MD5);
		} catch (IOException ex) {
			project.getLogger().error("Failed to create new CSVs!", ex);
			throw new RuntimeException(ex);
		}

		// This is enough to trick FG3 into using our new mappings
		minecraft.mappings(channel, newVersion);
	}

	protected void processCsvs(File origCsvs, File newCsvs) throws IOException {
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
