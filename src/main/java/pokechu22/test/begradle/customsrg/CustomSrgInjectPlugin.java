package pokechu22.test.begradle.customsrg;

import static net.minecraftforge.gradle.common.Constants.*;
import static net.minecraftforge.gradle.user.UserConstants.*;
import static pokechu22.test.begradle.customsrg.CustomSrgConstants.*;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import net.minecraftforge.gradle.tasks.ApplyS2STask;
import net.minecraftforge.gradle.tasks.CreateStartTask;
import net.minecraftforge.gradle.tasks.DeobfuscateJar;
import net.minecraftforge.gradle.tasks.GenSrgs;
import net.minecraftforge.gradle.tasks.PostDecompileTask;
import net.minecraftforge.gradle.tasks.RemapSources;
import net.minecraftforge.gradle.user.TaskSingleDeobfBin;
import net.minecraftforge.gradle.user.TaskSingleReobf;
import net.minecraftforge.gradle.user.UserBasePlugin;
import net.minecraftforge.gradle.util.delayed.DelayedFile;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.InvalidPluginException;
import org.gradle.api.plugins.PluginCollection;

import com.google.common.collect.Multimap;

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
	protected ExtraSrgContainer extraSrgContainer = new ExtraSrgContainer(this);
	/**
	 * Has {@link #forceLocalCache()} been called?
	 */
	protected boolean hasForcedLocalCache = false;

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

		project.getLogger().debug("Adding CSV generation task");
		GenCsvsTask genCsvs = project.getTasks().create(TASK_GENERATE_CSVS, GenCsvsTask.class);
		{
			genCsvs.setInMethodsCSV(delayedFile(CSV_METHOD));
			genCsvs.setOutMethodsCSV(delayedFile(CUSTOM_CSV_METHOD));
			genCsvs.setInFieldsCSV(delayedFile(CSV_FIELD));
			genCsvs.setOutFieldsCSV(delayedFile(CUSTOM_CSV_FIELD));
			genCsvs.setExtraSrgContainer(extraSrgContainer);

			genCsvs.setDescription("Generates custom MCP method and field CSVs.");
			genCsvs.dependsOn(TASK_EXTRACT_MCP, TASK_EXTRACT_MAPPINGS);
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
		task.setRemappedInExc(delayedFile(CUSTOM_REMAPPED_IN_EXC));
		task.setReverseSrg(delayedFile(CUSTOM_REVERSE_SRG));
		task.setExtraSrgs(extraSrgContainer.getSrgs());
		task.setMethodsCsv(delayedFile(CUSTOM_CSV_METHOD));
		task.setFieldsCsv(delayedFile(CUSTOM_CSV_FIELD));
		task.dependsOn(TASK_GENERATE_CSVS);
		project.getLogger().debug("Injected - " + task);

		// Now, tweak all of the output locations.  This is the fragile part.
		// This doesn't affect, for instance, the patcher plugin.  That _may_
		// be a problem... but I don't want to deal with it right now.
		updateBasePluginMakeCommonTasks();
		updateUserBasePluginRemapDeps();
		updateUserBasePluginMakeDecompTasks();
		updateUserBasePluginConfigureRetromapping();
		updateUserBasePluginSetupReobf();

		// Finally, fix the reobf task.  This is _probably_ wrong, as there are
		// _probably_ many more tasks I also need to fix...  But I'd need to
		// edit the factory, which seems more complex.
		TaskSingleReobf reobf = getTask("reobfJar", TaskSingleReobf.class);
		reobf.setPrimarySrg(delayedFile(CUSTOM_SRG_MCP_TO_NOTCH));
		// Note: no need to directly apply the reverse SRG; the custom SRG
		// already handles it.
		// ... however, this might not choose the right reobfuscation map;
		// might want MCP to SRG (forge).

		injectCustomPatches();
	}

	/**
	 * @see net.minecraftforge.gradle.common.BasePlugin.makeCommonTasks
	 */
	private void updateBasePluginMakeCommonTasks() {
		GenSrgs genSrgs = getTask(TASK_GENERATE_SRGS, GenSrgs.class);
		// genSrgs.setInSrg(delayedFile(MCP_DATA_SRG));
		// genSrgs.setInExc(delayedFile(MCP_DATA_EXC));
		// genSrgs.setInStatics(delayedFile(MCP_DATA_STATICS));
		genSrgs.setMethodsCsv(delayedFile(CUSTOM_CSV_METHOD));
		genSrgs.setFieldsCsv(delayedFile(CUSTOM_CSV_FIELD));
		genSrgs.setNotchToSrg(delayedFile(CUSTOM_SRG_NOTCH_TO_SRG));
		genSrgs.setNotchToMcp(delayedFile(CUSTOM_SRG_NOTCH_TO_MCP));
		genSrgs.setSrgToMcp(delayedFile(CUSTOM_SRG_SRG_TO_MCP));
		genSrgs.setMcpToSrg(delayedFile(CUSTOM_SRG_MCP_TO_SRG));
		genSrgs.setMcpToNotch(delayedFile(CUSTOM_SRG_MCP_TO_NOTCH));
		genSrgs.setSrgExc(delayedFile(CUSTOM_EXC_SRG));
		genSrgs.setMcpExc(delayedFile(CUSTOM_EXC_MCP));

		genSrgs.dependsOn(TASK_GENERATE_CSVS);
	}

	/**
	 * @see net.minecraftforge.gradle.user.UserBasePlugin#makeDecompTasks
	 */
	private void updateUserBasePluginMakeDecompTasks() {
		final DeobfuscateJar deobfBin = getTask(TASK_DEOBF_BIN, DeobfuscateJar.class);
		deobfBin.setSrg(delayedFile(CUSTOM_SRG_NOTCH_TO_MCP));
		// deobfBin.setExceptorJson(delayedFile(MCP_DATA_EXC_JSON));
		deobfBin.setExceptorCfg(delayedFile(CUSTOM_EXC_MCP));
		deobfBin.setFieldCsv(delayedFile(CUSTOM_CSV_FIELD));
		deobfBin.setMethodCsv(delayedFile(CUSTOM_CSV_METHOD));
		deobfBin.dependsOn(TASK_GENERATE_CSVS);

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
	 * @see net.minecraftforge.gradle.user.UserBasePlugin#remapDeps
	 */
	private void updateUserBasePluginRemapDeps() {
		for (Task task : project.getTasks()) {
			if (task instanceof TaskSingleDeobfBin) {
				TaskSingleDeobfBin deobf = (TaskSingleDeobfBin) task;
				deobf.setFieldCsv(delayedFile(CUSTOM_CSV_FIELD));
				deobf.setMethodCsv(delayedFile(CUSTOM_CSV_METHOD));
				deobf.dependsOn(TASK_GENERATE_CSVS);
			}
			if (task instanceof RemapSources) {
				RemapSources remap = (RemapSources) task;

				remap.setFieldsCsv(delayedFile(CUSTOM_CSV_FIELD));
				remap.setMethodsCsv(delayedFile(CUSTOM_CSV_METHOD));
				remap.dependsOn(TASK_GENERATE_CSVS);
			}
		}
	}

	/**
	 * @see net.minecraftforge.gradle.user.UserBasePlugin#setupReobf
	 */
	private void updateUserBasePluginSetupReobf() {
		for (Task task : project.getTasks()) {
			if (task instanceof TaskSingleReobf) {
				TaskSingleReobf reobf = (TaskSingleReobf) task;
				reobf.setFieldCsv(delayedFile(CUSTOM_CSV_FIELD));
				reobf.setMethodCsv(delayedFile(CUSTOM_CSV_METHOD));
				reobf.dependsOn(TASK_GENERATE_CSVS);
			}
		}
	}

	/**
	 * @see net.minecraftforge.gradle.user.UserBasePlugin#configureRetromapping
	 */
	private void updateUserBasePluginConfigureRetromapping() {
		// This also may be broken, as it seems like there can be multiple of
		// these tasks.  Also we need to remove the original SRGs and EXCs...
		ApplyS2STask retromap = getTask(String.format(TMPL_TASK_RETROMAP, "Main"), ApplyS2STask.class);
		clear(retromap);
		retromap.addSrg(delayedFile(CUSTOM_SRG_MCP_TO_SRG));
		retromap.addExc(delayedFile(CUSTOM_EXC_MCP));
		retromap.addExc(delayedFile(CUSTOM_EXC_SRG));

		retromap = getTask(String.format(TMPL_TASK_RETROMAP_RPL, "Main"), ApplyS2STask.class);
		clear(retromap);
		retromap.addSrg(delayedFile(CUSTOM_SRG_MCP_TO_SRG));
		retromap.addExc(delayedFile(CUSTOM_EXC_MCP));
		retromap.addExc(delayedFile(CUSTOM_EXC_SRG));
	}
	
	/**
	 * Clears the EXC and SRG files of the given task, so that new ones can be added.
	 * @param task The task to edit.
	 */
	private void clear(ApplyS2STask task) {
		Field srg;
		Field exc;
		try {
			srg = ApplyS2STask.class.getDeclaredField("srg");
			srg.setAccessible(true);
		} catch (NoSuchFieldException | SecurityException ex) {
			throw new RuntimeException("Failed to get srg field", ex);
		}
		try {
			exc = ApplyS2STask.class.getDeclaredField("exc");
			exc.setAccessible(true);
		} catch (NoSuchFieldException | SecurityException ex) {
			throw new RuntimeException("Failed to get exc field", ex);
		}

		project.getLogger().debug("Clearing SRG list for " + task);
		try {
			List<?> srgList = (List<?>) srg.get(task);
			project.getLogger().debug("Old list was " + srgList);
			srgList.clear();
		} catch (IllegalArgumentException | IllegalAccessException ex) {
			project.getLogger().warn("Failed to clear SRGs for " + task, ex);
		}
		project.getLogger().debug("Clearing EXC list for " + task);
		try {
			List<?> excList = (List<?>) exc.get(task);
			project.getLogger().debug("Old list was " + excList);
			excList.clear();
		} catch (IllegalArgumentException | IllegalAccessException ex) {
			project.getLogger().warn("Failed to clear EXCs for " + task, ex);
		}
	}

	private void injectCustomPatches() {
		try {
			final PostDecompileTask postDecomp = getTask(TASK_POST_DECOMP, PostDecompileTask.class);
			Field field = PostDecompileTask.class.getDeclaredField("patchesMap");
			field.setAccessible(true);
			@SuppressWarnings("unchecked")
			Multimap<String, File> patchesMap = (Multimap<String, File>) field.get(postDecomp);
			for (Map.Entry<String, File> patch : extraSrgContainer.getPatches().entrySet()) {
				patchesMap.put(patch.getKey(), patch.getValue());
			}
		} catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException ex) {
			throw new RuntimeException("Failed to inject custom patches map!", ex);
		}
	}

	/**
	 * Force each project to use the local cache, so that normal jars aren't clobbered.
	 * This doesn't happen automatically with custom SRGs (it does with access transformers);
	 *
	 * Unfortunately there is no setter for this, so we need to use reflection.
	 *
	 * We can't override the normal method for this because that requires implementing a
	 * FG plugin, and we don't want to force a specific one of those.
	 */
	protected final void forceLocalCache() {
		if (hasForcedLocalCache) {
			return;
		}
		hasForcedLocalCache = true;

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
