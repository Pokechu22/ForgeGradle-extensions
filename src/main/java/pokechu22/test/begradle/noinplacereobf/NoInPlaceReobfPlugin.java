package pokechu22.test.begradle.noinplacereobf;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ListIterator;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.jvm.tasks.Jar;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.user.TaskSingleReobf;
import net.minecraftforge.gradle.user.UserBasePlugin;
import net.minecraftforge.gradle.util.caching.WriteCacheAction;

public class NoInPlaceReobfPlugin implements Plugin<Project> {
	protected Project project;
	protected UserBasePlugin<?> otherPlugin;

	@Override
	public void apply(Project target) {
		this.project = target;

		// XXX This is a very incomplete solution, since it doesn't replace all of the tasks
		// (or the factory that creates them).  But we only care about reobfJar.
		TaskSingleReobf reobf = (TaskSingleReobf) project.getTasks().getByName("reobfJar");

		Jar jar = (Jar) project.getTasks().getByName("jar");
		File originalTarget = jar.getArchivePath();
		jar.setDestinationDir(new File(project.getBuildDir(), "unobfJar"));
		File newTarget = jar.getArchivePath();
		File workJar = getWorkJar(reobf);

		reobf.setJar(workJar);

		reobf.doFirst(copy(newTarget, workJar));
		reobf.doLast(copy(workJar, originalTarget));

		project.getLogger().warn("Moveroni'd: " + reobf.getActions());
	}

	private File getWorkJar(TaskSingleReobf reobf) {
		try {
			File workJar = File.createTempFile("reobfFakeTarget", ".jar", reobf.getTemporaryDir());
			workJar.deleteOnExit();
			return workJar;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private Action<? super Task> copy(File from, File to) {
		return t -> {
			t.getLogger().debug("Copying {} to {}", from, to);
			try {
				Constants.copyFile(from, to);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			t.getLogger().debug("Copied.");
		};
	}
}
