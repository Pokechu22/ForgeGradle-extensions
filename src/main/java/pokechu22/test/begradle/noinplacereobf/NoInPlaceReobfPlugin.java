package pokechu22.test.begradle.noinplacereobf;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.Callable;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.jvm.tasks.Jar;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.user.TaskSingleReobf;
import net.minecraftforge.gradle.user.UserBasePlugin;

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
		File originalDirectory = jar.getDestinationDir();
		Callable<File> origTarget = () -> new File(originalDirectory, jar.getArchiveName());
		jar.setDestinationDir(new File(project.getBuildDir(), "unobfJar"));
		Callable<File> newTarget = jar::getArchivePath;
		File workJar = getWorkJar(reobf);

		reobf.setJar(workJar);

		reobf.doFirst(copy(newTarget, workJar));
		reobf.doLast(copy(workJar, origTarget));
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

	private Action<? super Task> copy(Object from, Object to) {
		return t -> {
			File fromFile = t.getProject().file(from);
			File toFile = t.getProject().file(to);
			t.getLogger().debug("Copying {} ({}) to {} ({})", fromFile, from, toFile, to);
			try {
				Constants.copyFile(fromFile, toFile);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			t.getLogger().debug("Copied.");
		};
	}
}
