package pokechu22.test.begradle;

import static net.minecraftforge.gradle.common.Constants.JAR_CLIENT_FRESH;
import static net.minecraftforge.gradle.common.Constants.MCP_INJECT;
import static net.minecraftforge.gradle.common.Constants.MCP_PATCHES_CLIENT;
import static net.minecraftforge.gradle.common.Constants.REPLACE_CACHE_DIR;
import static net.minecraftforge.gradle.common.Constants.REPLACE_MC_VERSION;
import static net.minecraftforge.gradle.common.Constants.TASK_DL_CLIENT;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

import net.minecraftforge.gradle.tasks.DeobfuscateJar;
import net.minecraftforge.gradle.user.UserVanillaBasePlugin;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.jvm.tasks.Jar;

public class BaseEditPlugin extends
		UserVanillaBasePlugin<BaseEditExtension> {

	/*@Inject
	public BaseEditPlugin(SourceDirectorySetFactory sourceDirectorySetFactory) {
		this.sourceDirectorySetFactory = sourceDirectorySetFactory;
	}

	private final SourceDirectorySetFactory sourceDirectorySetFactory;*/

	@Override
	protected void applyVanillaUserPlugin() {
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

		final Jar sourceJar = (Jar) tasks.getByName("sourceJar");
		sourceJar.setBaseName(baseName);

		// Loosely based on AntlrPlugin.  Add new options to each sourceset.
		project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets()
				.all(new Action<SourceSet>() {
					@Override
					public void execute(SourceSet sourceSet) {
						injectSourceSet(sourceSet);
					}
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

		final DeobfuscateJar deobfTask = (DeobfuscateJar) tasks.getByName("deobfMcMCP");

		// Create the new tasks
		String genTaskName = sourceSet.getTaskName("generate", "BasePatches");
		String applyTaskName = sourceSet.getTaskName("apply", "BasePatches");

		GenerateBasePatchesTask genTask = tasks.create(
				genTaskName, GenerateBasePatchesTask.class);
		genTask.setDescription("Generates the " + sourceSet.getName()
				+ " base patches.");
		genTask.dependsOn(deobfTask);

		ApplyBasePatchesTask applyTask = tasks.create(
				applyTaskName, ApplyBasePatchesTask.class);
		applyTask.setDescription("Applies the " + sourceSet.getName()
				+ " base patches.");
		applyTask.dependsOn(deobfTask);

		// Set the default locations for the tasks (so that the user doesn't
		// need to specify them)
		genTask.setPatches(baseDirectoryDelegate.getPatches());
		genTask.setPatchedSource(baseDirectoryDelegate.getPatchedSource());
		genTask.setOrigJar(new Callable<File>() {
			@Override
			public File call() throws Exception {
				return deobfTask.getOutJar();
			}
		});

		applyTask.setPatches(baseDirectoryDelegate.getPatches());
		applyTask.setPatchedSource(baseDirectoryDelegate.getPatchedSource());
		applyTask.setOrigJar(new Callable<File>() {
			@Override
			public File call() throws Exception {
				return deobfTask.getOutJar();
			}
		});

		// Order the tasks
		tasks.getByName(sourceSet.getCompileJavaTaskName()).dependsOn(genTask);
		// TODO: What else do I want?  SourceJar?

		// Tell the java plugin to compile the patched source
		sourceSet.getJava().srcDir(
				baseDirectoryDelegate.getPatchedSourceCallable());
	}

	@Override
	protected void afterEvaluate() {
		final Task classesTask = project.getTasks().getByName("classes");
		//classesTask.dependsOn("baseClasses");
		System.out.println(classesTask);
		final Jar jarTask = (Jar) project.getTasks().getByName("jar");

		if (this.hasClientRun()) {
			JavaExec exec = (JavaExec) project.getTasks()
					.getByName("runClient");
			// Insert our code into the classpath now, so that
			// it can overwrite vanilla code
			exec.classpath(jarTask.getArchivePath());
		}

		if (this.hasServerRun()) {
			JavaExec exec = (JavaExec) project.getTasks()
					.getByName("runServer");
			// Insert our code into the classpath now, so that
			// it can overwrite vanilla code
			exec.classpath(jarTask.getArchivePath());
		}
		
		super.afterEvaluate();  // Among other things, sets up the other classpaths
	}

	@Override
	protected void setupDevTimeDeobf(final Task compileDummy,
			final Task providedDummy) {
		System.out.println("setupDevTimeDeobf: " + compileDummy + " / " + providedDummy);
		super.setupDevTimeDeobf(compileDummy, providedDummy);
	}

	@Override
	protected String getJarName() {
		return "minecraft";
	}

	@Override
	protected void createDecompTasks(String globalPattern, String localPattern) {
		System.out.println("Making decomp tasks...: " + globalPattern + " / " + localPattern);
		super.makeDecompTasks(globalPattern, localPattern,
				delayedFile(JAR_CLIENT_FRESH), TASK_DL_CLIENT,
				delayedFile(MCP_PATCHES_CLIENT), delayedFile(MCP_INJECT));
	}

	@Override
	protected boolean hasServerRun() {
		return false;
	}

	@Override
	protected boolean hasClientRun() {
		return true;
	}

	@Override
	protected Object getStartDir() {
		return delayedFile(REPLACE_CACHE_DIR + "/net/minecraft/" + getJarName()
				+ "/" + REPLACE_MC_VERSION + "/start");
	}

	@Override
	protected String getClientTweaker(BaseEditExtension ext) {
		return "";
	}

	@Override
	protected String getClientRunClass(BaseEditExtension ext) {
		return "Start";
	}

	@Override
	protected String getServerTweaker(BaseEditExtension ext) {
		// Not designed for server use, yet
		return "";
	}

	@Override
	protected String getServerRunClass(BaseEditExtension ext) {
		// Not designed for server use, yet
		return "";
	}

	@Override
	protected List<String> getClientJvmArgs(BaseEditExtension ext) {
		return ext.getResolvedClientJvmArgs();
	}

	@Override
	protected List<String> getServerJvmArgs(BaseEditExtension ext) {
		return ext.getResolvedServerJvmArgs();
	}
}
