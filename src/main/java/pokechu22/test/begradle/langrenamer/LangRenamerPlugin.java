package pokechu22.test.begradle.langrenamer;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.language.jvm.tasks.ProcessResources;

import net.minecraftforge.gradle.common.Constants;

/**
 * Adds a property, <code>ext.renameLangFiles</code>, that controls whether
 * language files should be renamed to use the 1.10 convention (en_US.lang)
 * instead of the 1.11 convention (en_us.lang). Requires that all files are
 * already using the 1.11 convention.
 */
public class LangRenamerPlugin implements Plugin<Project> {
	@Override
	public void apply(Project project) {
		ExtensionAware extension = (ExtensionAware) project.getExtensions().getByName(Constants.EXT_NAME_MC);
		ExtraPropertiesExtension ext = extension.getExtensions().getExtraProperties();
		ext.set("capitalizeLangFiles", false);
		ext.set("jsonLangFiles", false);
		ext.set("langMap", Collections.<String, List<String>>emptyMap());

		// Manage up to date check.  As far as I can tell, this is the correct way of
		// doing this (only reruns when the value is changed).
		TaskContainer tasks = project.getTasks();
		ProcessResources resourcesTask = (ProcessResources) tasks.getByName("processResources");
		TaskInputs inputs = resourcesTask.getInputs();
		Callable<Object> capitalizeLangFilesCallable = () -> ext.get("capitalizeLangFiles");
		inputs.property("capitalizeLangFiles", capitalizeLangFilesCallable);
		Callable<Object> jsonLangFilesCallable = () -> ext.get("jsonLangFiles");
		inputs.property("jsonLangFiles", jsonLangFilesCallable);
		Callable<Object> langMapCallable = () -> ext.get("langMap");
		inputs.property("langMap", langMapCallable);

		project.afterEvaluate(this::afterEvaluate);
	}

	@SuppressWarnings("unchecked")
	private void afterEvaluate(Project project) {
		TaskContainer tasks = project.getTasks();
		ProcessResources resourcesTask = (ProcessResources) tasks.getByName("processResources");
		ExtensionAware extension = (ExtensionAware) project.getExtensions().getByName(Constants.EXT_NAME_MC);
		ExtraPropertiesExtension ext = extension.getExtensions().getExtraProperties();
		boolean capitalizeLangFiles = (boolean) ext.get("capitalizeLangFiles");
		boolean jsonLangFiles = (boolean) ext.get("jsonLangFiles");
		Map<String, List<String>> langMap = (Map<String, List<String>>) ext.get("langMap");

		resourcesTask.eachFile((spec) -> {
			if (isLanguageFile(spec)) {
				String name = extractLanguageName(spec);
				String fileExtension = jsonLangFiles ? ".json" : ".lang";
				String newName = capitalizeLangFiles ? capitalizeLanguageName(name) : name;

				if (jsonLangFiles) {
					jsonifyFile(spec);
				}
				spec.setName(newName + fileExtension);
				if (langMap.containsKey(name)) {
					for (String language : langMap.get(name)) {
						if (language.equals(name)) {
							project.getLogger().warn(project + ": langMap for " + name + " contains itself");
							continue;
						}
						String newLanguage = capitalizeLangFiles ? capitalizeLanguageName(language) : language;
						RelativePath path = spec.getRelativePath().replaceLastName(newLanguage + fileExtension);
						project.getLogger().info(project + ": Copying " + name + " to " + language);
						spec.copyTo(path.getFile(resourcesTask.getDestinationDir()));
					}
				} else {
					project.getLogger().info(project + ": No langMap info for language " + name);
				}
			}
		});
	}

	private boolean isLanguageFile(FileCopyDetails spec) {
		return spec.getSourceName().endsWith(".lang");
	}

	// Returns e.g. en_us
	private String extractLanguageName(FileCopyDetails spec) {
		assert isLanguageFile(spec);
		return spec.getSourceName().replace(".lang", "");
	}

	// Converts en_us to en_US
	private String capitalizeLanguageName(String lowercaseName) {
		int underscore = lowercaseName.lastIndexOf("_");
		String prefix = lowercaseName.substring(0, underscore); // e.g. "en"
		String suffix = lowercaseName.substring(underscore); // e.g. "_us"
		if (!suffix.equals(suffix.toLowerCase(Locale.ROOT))) {
			throw new RuntimeException("Bad locale name '" + lowercaseName + "': name should be lowercase");
		}
		return prefix + suffix.toUpperCase(Locale.ROOT);
	}

	private void jsonifyFile(FileCopyDetails spec) {
		spec.filter(JsonifyFilterReader.class);
	}
}
