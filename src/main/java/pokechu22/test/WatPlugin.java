package pokechu22.test;

import static net.minecraftforge.gradle.common.Constants.JAR_CLIENT_FRESH;
import static net.minecraftforge.gradle.common.Constants.MCP_INJECT;
import static net.minecraftforge.gradle.common.Constants.MCP_PATCHES_CLIENT;
import static net.minecraftforge.gradle.common.Constants.REPLACE_CACHE_DIR;
import static net.minecraftforge.gradle.common.Constants.REPLACE_MC_VERSION;
import static net.minecraftforge.gradle.common.Constants.TASK_DL_CLIENT;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

import net.minecraftforge.gradle.user.TaskRecompileMc;
import net.minecraftforge.gradle.user.UserVanillaBasePlugin;

import org.gradle.api.Task;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.jvm.tasks.Jar;

public class WatPlugin extends
		UserVanillaBasePlugin<WatExtension> {
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
		
		final TaskRecompileMc recompTask = (TaskRecompileMc) tasks.getByName("recompileMc");
		
		BaseClassesTask baseTask = makeTask("baseClasses", BaseClassesTask.class);
		baseTask.setJar(new Callable<File>() {
			@Override
			public File call() throws Exception {
				return recompTask.getInSources();
			}
		});
		baseTask.dependsOn("deobfMcMCP");
	}

	@Override
	protected void afterEvaluate() {
		final Task classesTask = project.getTasks().getByName("classes");
		classesTask.dependsOn("baseClasses");
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
	protected String getClientTweaker(WatExtension ext) {
		return "";
	}

	@Override
	protected String getClientRunClass(WatExtension ext) {
		return "Start";
	}

	@Override
	protected String getServerTweaker(WatExtension ext) {
		// Not designed for server use, yet
		return "";
	}

	@Override
	protected String getServerRunClass(WatExtension ext) {
		// Not designed for server use, yet
		return "";
	}

	@Override
	protected List<String> getClientJvmArgs(WatExtension ext) {
		return ext.getResolvedClientJvmArgs();
	}

	@Override
	protected List<String> getServerJvmArgs(WatExtension ext) {
		return ext.getResolvedServerJvmArgs();
	}
}
