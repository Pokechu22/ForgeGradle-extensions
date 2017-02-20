package pokechu22.test.begradle.customsrg;

import static net.minecraftforge.gradle.common.Constants.*;
import static net.minecraftforge.gradle.user.UserConstants.*;
import static pokechu22.test.begradle.customsrg.CustomSrgConstants.*;

import java.lang.reflect.Field;

import net.minecraftforge.gradle.tasks.CreateStartTask;
import net.minecraftforge.gradle.tasks.DeobfuscateJar;
import net.minecraftforge.gradle.tasks.GenSrgs;
import net.minecraftforge.gradle.user.TaskSingleReobf;
import net.minecraftforge.gradle.user.UserBasePlugin;
import net.minecraftforge.gradle.util.delayed.DelayedFile;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.InvalidPluginException;
import org.gradle.api.plugins.PluginCollection;

/**
 * A plugin that handles custom SRG tasks.
 */
public class CustomSrgInjectPlugin implements Plugin<Project> {
	/**
	 * Associated project
	 */
	protected Project project;
	/**
	 * A BasePlugin that can be used for replacements and such.
	 */
	protected UserBasePlugin<?> otherPlugin;
	/**
	 * Contains the list of additional SRGs to add.
	 */
	protected ExtraSrgContainer extraSrgContainer = new ExtraSrgContainer();

	@Override
	public void apply(Project project) {
		this.project = project;
		this.otherPlugin = findAssociatedPlugin();
		project.getLogger().debug("Associated plugin is " + otherPlugin);

		// Add configuration support
		new DslObject(project).getConvention().getPlugins()
				.put("extraSrgs", extraSrgContainer.new ConfigurationDelegate());

		project.getLogger().debug("Preparing afterEvaluate for SRG injection");
		project.afterEvaluate(new Action<Project>() {
			@Override
			public void execute(Project project) {
				project.getLogger().debug("Calling afterEvaluate for SRG injection");
				if (project.getState().getFailure() != null) {
					project.getLogger().debug("Failed, aborting!");
					return;
				}

				afterEvaluate();
			}
		});
	}

	/**
	 * HACKY.  Finds the active FG plugin.  Hopefully there's only one...
	 */
	private UserBasePlugin<?> findAssociatedPlugin() {
		@SuppressWarnings("rawtypes")
		PluginCollection<UserBasePlugin> plugins =
				project.getPlugins().withType(UserBasePlugin.class);

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

		// Provide this one new replacement token.
		otherPlugin.replacer.putReplacement(REPLACE_CUSTOM_SRG_SPECIFIER,
				extraSrgContainer.getSpecifier());

		// Add the new genSrgs task
		// XXX is this actually working?
		project.getLogger().debug("Injecting new SRGs task");
		GenSrgs origGenSrgs = getTask(TASK_GENERATE_SRGS, GenSrgs.class);
		GenSrgsWithCustomSupportTask task = project.getTasks().replace(
				TASK_GENERATE_SRGS, GenSrgsWithCustomSupportTask.class);
		task.copyFrom(origGenSrgs);
		task.setSrgLog(delayedFile(CUSTOM_SRG_LOG));
		task.setReverseSrg(delayedFile(CUSTOM_REVERSE_SRG));
		task.setExtraSrgs(extraSrgContainer);
		project.getLogger().debug("Injected - " + task);

		// We need the local cache
		forceLocalCache();

		// Now, tweak all of the output locations.  This is the fragile part.
		// This doesn't affect, for instance, the patcher plugin.  That _may_
		// be a problem... but I don't want to deal with it right now.
		updateBasePluginMakeCommonTasks();
		updateUserBasePluginMakeDecompTasks();

		// Finally, fix the reobf task.  This is _probably_ wrong, as there are
		// _probably_ many more tasks I also need to fix...
		TaskSingleReobf reobf = getTask("reobfJar", TaskSingleReobf.class);
		reobf.addSecondarySrgFile(CUSTOM_REVERSE_SRG);
	}

	/**
	 * @see net.minecraftforge.gradle.common.BasePlugin.makeCommonTasks
	 */
	private void updateBasePluginMakeCommonTasks() {
		GenSrgs genSrgs = getTask(TASK_GENERATE_SRGS, GenSrgs.class);
		// genSrgs.setInSrg(delayedFile(MCP_DATA_SRG));
		// genSrgs.setInExc(delayedFile(MCP_DATA_EXC));
		// genSrgs.setInStatics(delayedFile(MCP_DATA_STATICS));
		// genSrgs.setMethodsCsv(delayedFile(CSV_METHOD));
		// genSrgs.setFieldsCsv(delayedFile(CSV_FIELD));
		genSrgs.setNotchToSrg(delayedFile(CUSTOM_SRG_NOTCH_TO_SRG));
		genSrgs.setNotchToMcp(delayedFile(CUSTOM_SRG_NOTCH_TO_MCP));
		genSrgs.setSrgToMcp(delayedFile(CUSTOM_SRG_SRG_TO_MCP));
		genSrgs.setMcpToSrg(delayedFile(CUSTOM_SRG_MCP_TO_SRG));
		genSrgs.setMcpToNotch(delayedFile(CUSTOM_SRG_MCP_TO_NOTCH));
		genSrgs.setSrgExc(delayedFile(CUSTOM_EXC_SRG));
		genSrgs.setMcpExc(delayedFile(CUSTOM_EXC_MCP));
	}

	/**
	 * @see net.minecraftforge.gradle.user.UserBasePlugin#makeDecompTasks
	 */
	private void updateUserBasePluginMakeDecompTasks() {
		final DeobfuscateJar deobfBin = getTask(TASK_DEOBF_BIN, DeobfuscateJar.class);
		deobfBin.setSrg(delayedFile(CUSTOM_SRG_NOTCH_TO_MCP));
		// deobfBin.setExceptorJson(delayedFile(MCP_DATA_EXC_JSON));
		deobfBin.setExceptorCfg(delayedFile(CUSTOM_EXC_MCP));
		// deobfBin.setFieldCsv(delayedFile(CSV_FIELD));
		// deobfBin.setMethodCsv(delayedFile(CSV_METHOD));

		final DeobfuscateJar deobfDecomp = getTask(TASK_DEOBF, DeobfuscateJar.class);
		deobfDecomp.setSrg(delayedFile(CUSTOM_SRG_NOTCH_TO_SRG));
		// deobfDecomp.setExceptorJson(delayedFile(MCP_DATA_EXC_JSON));
		deobfDecomp.setExceptorCfg(delayedFile(CUSTOM_EXC_SRG));

		final CreateStartTask makeStart = getTask(TASK_MAKE_START, CreateStartTask.class);
		makeStart.addReplacement("@@SRGDIR@@", delayedFile(DIR_CUSTOM_MCP_MAPPINGS + "/srgs/"));
		makeStart.addReplacement("@@SRG_NOTCH_SRG@@", delayedFile(CUSTOM_SRG_NOTCH_TO_SRG));
		makeStart.addReplacement("@@SRG_NOTCH_MCP@@", delayedFile(CUSTOM_SRG_NOTCH_TO_MCP));
		makeStart.addReplacement("@@SRG_SRG_MCP@@", delayedFile(CUSTOM_SRG_SRG_TO_MCP));
		makeStart.addReplacement("@@SRG_MCP_SRG@@", delayedFile(CUSTOM_SRG_MCP_TO_SRG));
		makeStart.addReplacement("@@SRG_MCP_NOTCH@@", delayedFile(CUSTOM_SRG_MCP_TO_NOTCH));
		// makeStart.addReplacement("@@CSVDIR@@", delayedFile(DIR_MCP_MAPPINGS));
	}

	/**
	 * Force each project to use the local cache, so that normal jars aren't clobbered.
	 * This doesn't happen automatically with custom SRGs (it does with access transformers);
	 *
	 * Unfortunately there is no setter for this, so we need to use reflection.
	 */
	protected final void forceLocalCache() {
		Field field;
		try {
			field = UserBasePlugin.class.getDeclaredField("useLocalCache");
			field.setAccessible(true);
		} catch (NoSuchFieldException | SecurityException ex) {
			throw new RuntimeException("Failed to get useLocalCache field", ex);
		}

		project.getLogger().debug("Enabling useLocalCache for " + otherPlugin);
		try {
			field.setBoolean(otherPlugin, true);
		} catch (IllegalArgumentException | IllegalAccessException ex) {
			project.getLogger().warn("Failed to set useLocalCache for " + otherPlugin, ex);
		}
	}

	protected DelayedFile delayedFile(String path) {
		return otherPlugin.delayedFile(path);
	}

	protected <T> T getTask(String name, Class<T> type) {
		return type.cast(project.getTasks().getByName(name));
	}
}
