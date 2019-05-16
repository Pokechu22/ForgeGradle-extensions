package pokechu22.test.begradle.baseedit;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;

import javax.annotation.Nonnull;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
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

import net.minecraftforge.gradle.userdev.UserDevPlugin;

public class BaseEditPlugin extends UserDevPlugin {
	private Project project;

	@Override
	public void apply(@Nonnull Project project) {
		configureEclipse();
		super.apply(project);
		this.project = project;

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

		final Jar sourceJar = (Jar) tasks.getByName("sourceJar");
		sourceJar.setBaseName(baseName);

		// Loosely based on AntlrPlugin.  Add new options to each sourceset.
		project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets()
				.all(this::injectSourceSet);
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

		Configuration minecraft = project.getConfigurations().getAt("minecraft");

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
		processTask.setOrigJar(new Callable<File>() {
			@Override
			public File call() throws Exception {
				System.out.println(minecraft.getFiles());
				return minecraft.getSingleFile();
			}
		});
		processTask.setBaseClasses(baseDirectoryDelegate.getBaseClassesCallable());

		genTask.setPatches(baseDirectoryDelegate.getPatches());
		genTask.setPatchedSource(baseDirectoryDelegate.getPatchedSource());
		genTask.setOrigJar(new Callable<File>() {
			@Override
			public File call() throws Exception {
				System.out.println(minecraft.getFiles());
				return minecraft.getSingleFile();
			}
		});
		genTask.setBaseClasses(baseDirectoryDelegate.getBaseClassesCallable());

		applyTask.setPatches(baseDirectoryDelegate.getPatches());
		applyTask.setPatchedSource(baseDirectoryDelegate.getPatchedSource());
		applyTask.setOrigJar(new Callable<File>() {
			@Override
			public File call() throws Exception {
				System.out.println(minecraft.getFiles());
				return minecraft.getSingleFile();
			}
		});
		applyTask.setBaseClasses(baseDirectoryDelegate.getBaseClassesCallable());

		// Order the tasks
		tasks.getByName(sourceSet.getCompileJavaTaskName()).dependsOn(processTask);
		// TODO: What else do I want?  SourceJar?

		// Tell the java plugin to compile the patched source
		sourceSet.getJava().srcDir(
				baseDirectoryDelegate.getPatchedSourceCallable());
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
