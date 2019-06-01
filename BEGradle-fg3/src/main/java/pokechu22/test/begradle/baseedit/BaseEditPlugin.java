package pokechu22.test.begradle.baseedit;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.annotation.Nonnull;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.file.CopySpec;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.jvm.tasks.Jar;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.SourceFolder;

import com.google.common.collect.ImmutableMap;

import net.minecraftforge.gradle.userdev.UserDevPlugin;

public class BaseEditPlugin extends UserDevPlugin {
	// Null until apply
	private Project project;
	// Null until afterEvaluate
	private File mcSourcesJar;

	@Override
	public void apply(@Nonnull Project project) {
		if (this.project != null) {
			// If it is possible for this to happen, then this code is broken
			throw new AssertionError("Expected plugin to only be applied once!");
		}
		this.project = project;

		project.apply(ImmutableMap.of("plugin", "eclipse"));
		project.apply(ImmutableMap.of("plugin", "idea"));

		configureEclipse();
		super.apply(project);

		String baseName = "mod-"
				+ this.project.property("archivesBaseName").toString()
						.toLowerCase();

		TaskContainer tasks = this.project.getTasks();
		final Jar jar = (Jar) tasks.getByName("jar");
		// Note: format is:
		// [baseName]-[appendix]-[version]-[classifier].[extension]
		jar.setBaseName(baseName);
		jar.setAppendix("baseedit");
		jar.setExtension("zip");

		// Disable generation of META-INF.
		// Note that we can't use jar.getMetaInf - that isn't actually a getter,
		// but rather something that creates a subtask.  So steal the field directly.
		try {
			Field metaInfField = Jar.class.getDeclaredField("metaInf");
			metaInfField.setAccessible(true);
			CopySpec metaInf = (CopySpec) metaInfField.get(jar);
			metaInf.exclude("**");  // This is the call that actually disables generation
		} catch (Exception ex) {
			throw new RuntimeException("Failed to disable META-INF generation", ex);
		}

		// Loosely based on AntlrPlugin.  Add new options to each sourceset.
		project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets()
				.all(this::injectSourceSet);

		Configuration minecraft = project.getConfigurations().getByName("minecraft");

		project.afterEvaluate(p -> {
			// Prepare for actually finding the sources jar
			List<Dependency> deps = new ArrayList<>(minecraft.getDependencies());
			System.out.println("Deps: " + deps);
			if (deps.size() != 1) {
				throw new RuntimeException("Expected only one minecraft dependency, but there were " + deps);
			}
			Dependency dep = deps.get(0);
			if (!(dep instanceof ExternalModuleDependency)) {
				throw new RuntimeException("Expected an ExternalModuleDependency, but was a " + dep.getClass() + " (" + dep + ")");
			}
			ExternalModuleDependency mcDep = (ExternalModuleDependency)dep;
			// Keep the actual jar
			mcDep.artifact(art -> {
				art.setName("client");
				art.setType("maven");
			});
			// But we also want sources
			mcDep.artifact(art -> {
				art.setName("client");
				art.setType("maven");
				art.setClassifier("sources");
			});
			// We also want this for its dependencies (which don't show up for some reason)
			// (Unfortunately this doesn't work; the result seems to either be that it's ignored, or that it breaks resolution of the actual one)
			/*mcDep.artifact(art -> {
				art.setName("client");
				art.setType("maven");
				art.setClassifier("extra");
			});*/

			Set<File> files = minecraft.getResolvedConfiguration().getFiles();
			String expectedName = mcDep.getName() + "-" + mcDep.getVersion() + "-sources.jar";
			Set<File> matching = new HashSet<>();
			for (File file : files) {
				if (file.getName().equals(expectedName)) {
					matching.add(file);
				}
			}
			if (matching.size() != 1) {
				throw new RuntimeException("Expected only 1 resolved file to match be named \"" + expectedName + "\": " + matching + " (from " + files + ")");
			}
			mcSourcesJar = matching.iterator().next();
		});
	}

	/**
	 * Injects the needed tasks and such into a {@link SourceSet}.
	 * 
	 * @see org.gradle.api.plugins.antlr.AntlrPlugin#apply Inner class within AntlrPlugin
	 */
	private void injectSourceSet(SourceSet sourceSet) {
		TaskContainer tasks = this.project.getTasks();

		// Add a new "base" virtual directory mapping that can be used within the SourceSet.
		final BaseClassesVirtualDirectory baseDirectoryDelegate = new BaseClassesVirtualDirectory(
				sourceSet);
		new DslObject(sourceSet).getConvention().getPlugins().put(
				BaseClassesVirtualDirectory.NAME, baseDirectoryDelegate);
		// Add the patched source to the all source list
		sourceSet.getAllSource().srcDir(
				baseDirectoryDelegate.getPatchedSourceCallable());

		// Create the new tasks
		String processTaskName = sourceSet.getTaskName("process", "BasePatches");
		String genTaskName = sourceSet.getTaskName("generate", "BasePatches");
		String applyTaskName = sourceSet.getTaskName("apply", "BasePatches");

		ProcessBasePatchesTask processTask = tasks.create(
				processTaskName, ProcessBasePatchesTask.class);
		processTask.setDescription("Processes all of the " + sourceSet.getName()
				+ " base patches, applying and generating them as needed.");

		GenerateBasePatchesTask genTask = tasks.create(
				genTaskName, GenerateBasePatchesTask.class);
		genTask.setDescription("Generates all of the " + sourceSet.getName()
				+ " base patches, removing any existing ones.");

		ApplyBasePatchesTask applyTask = tasks.create(
				applyTaskName, ApplyBasePatchesTask.class);
		applyTask.setDescription("Applies all of the " + sourceSet.getName()
				+ " base patches, overwriting any other modified source.");

		// Set the default locations for the tasks (so that the user doesn't
		// need to specify them)
		processTask.setPatches(baseDirectoryDelegate.getPatches());
		processTask.setPatchedSource(baseDirectoryDelegate.getPatchedSource());
		processTask.setOrigJar((Callable<File>)this::getMcSourcesJar);
		processTask.setBaseClasses(baseDirectoryDelegate.getBaseClassesCallable());

		genTask.setPatches(baseDirectoryDelegate.getPatches());
		genTask.setPatchedSource(baseDirectoryDelegate.getPatchedSource());
		genTask.setOrigJar((Callable<File>)this::getMcSourcesJar);
		genTask.setBaseClasses(baseDirectoryDelegate.getBaseClassesCallable());

		applyTask.setPatches(baseDirectoryDelegate.getPatches());
		applyTask.setPatchedSource(baseDirectoryDelegate.getPatchedSource());
		applyTask.setOrigJar((Callable<File>)this::getMcSourcesJar);
		applyTask.setBaseClasses(baseDirectoryDelegate.getBaseClassesCallable());

		// Order the tasks
		tasks.getByName(sourceSet.getCompileJavaTaskName()).dependsOn(processTask);
		// TODO: What else do I want?  SourceJar?

		// Tell the java plugin to compile the patched source
		sourceSet.getJava().srcDir(
				baseDirectoryDelegate.getPatchedSourceCallable());
	}

	private File getMcSourcesJar() throws IllegalStateException {
		if (mcSourcesJar == null) {
			throw new IllegalStateException("mcSourcesJar is still null -- getMcSourcesJar actually called before afterEvaluate?");
		}
		return mcSourcesJar;
	}

	protected void configureEclipse() {
		EclipseModel eclipseConv = (EclipseModel) project.getExtensions().getByName("eclipse");
		final XmlFileContentMerger classpathMerger = eclipseConv.getClasspath().getFile();
		classpathMerger.whenMerged(obj -> {
			Classpath classpath = (Classpath)obj;
			for (ClasspathEntry entry : classpath.getEntries()) {
				if (entry instanceof SourceFolder) {
					SourceFolder sf = (SourceFolder)entry;
					if (sf.getPath().contains("base")) { // XXX this check is sub-optimal and could have false positives
						sf.getEntryAttributes().put("ignore_optional_problems", "true");
					}
				}
			}
		});
	}
}
