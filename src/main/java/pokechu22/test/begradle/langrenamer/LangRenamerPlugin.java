package pokechu22.test.begradle.langrenamer;

import groovy.lang.Closure;

import java.util.Locale;
import java.util.concurrent.Callable;

import net.minecraftforge.gradle.common.Constants;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.jvm.tasks.ProcessResources;

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
		ext.set("renameLangFiles", false);

		// Manage up to date check.  As far as I can tell, this is the correct way of
		// doing this (only reruns when the value is changed).
		TaskContainer tasks = project.getTasks();
		ProcessResources resourcesTask = (ProcessResources) tasks.getByName("processResources");
		Callable<Object> callable = () -> ext.get("renameLangFiles");
		resourcesTask.getInputs().property("renameLangFiles", callable);

		project.afterEvaluate(this::afterEvaluate);
	}

	@SuppressWarnings("serial")
	private void afterEvaluate(Project project) {
		TaskContainer tasks = project.getTasks();
		ProcessResources resourcesTask = (ProcessResources) tasks.getByName("processResources");
		ExtensionAware extension = (ExtensionAware) project.getExtensions().getByName(Constants.EXT_NAME_MC);
		ExtraPropertiesExtension ext = extension.getExtensions().getExtraProperties();
		boolean renameLangFiles = (boolean) ext.get("renameLangFiles");
		if (renameLangFiles) {
			resourcesTask.rename(new Closure<String>(LangRenamerPlugin.this, LangRenamerPlugin.class) {
				@Override
				public String call(Object arguments) {
					String name = (String) arguments; // e.g. "en_us.lang"
					project.getLogger().trace("Checking if '" + name + "' should be renamed...");
					if (name.endsWith(".lang")) {
						int underscore = name.lastIndexOf("_");
						int lang = name.lastIndexOf(".lang");
						String prefix = name.substring(0, underscore); // e.g. "en"
						String suffix = name.substring(underscore, lang); // e.g. "_us"
						if (!suffix.equals(suffix.toLowerCase(Locale.ROOT))) {
							throw new RuntimeException("Bad locale file name '" + name + "': file on disk's name should be lowercase");
						}

						String newName = prefix + suffix.toUpperCase(Locale.ROOT) + ".lang";
						project.getLogger().debug("Renaming '" + name + "' to '" + newName + "'.");

						return newName;
					} else {
						project.getLogger().trace("Doesn't need a rename.");
						return null;
					}
				}
			});
		}
	}
}
