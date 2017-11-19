package pokechu22.test.begradle.noinplacereobf;

import java.io.File;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.jvm.tasks.Jar;

import net.minecraftforge.gradle.user.TaskSingleReobf;
import net.minecraftforge.gradle.user.UserBasePlugin;

public class NoInPlaceReobfPlugin implements Plugin<Project> {
	protected Project project;
	protected UserBasePlugin<?> otherPlugin;

	@Override
	public void apply(Project target) {
		this.project = target;

		Jar jar = (Jar) project.getTasks().getByName("jar");
		File originalTarget = jar.getArchivePath();
		jar.setDestinationDir(new File(project.getBuildDir(), "unobfJar"));
		File newTarget = jar.getArchivePath();

		// XXX This is a very incomplete solution, since it doesn't replace all of the tasks
		// (or the factory that creates them).  But we only care about reobfJar.
		TaskSingleReobf origTask = (TaskSingleReobf) project.getTasks().getByName("reobfJar");
		TaskSingleReobf2 newTask = project.getTasks().replace("reobfJar", TaskSingleReobf2.class);
		newTask.copyFrom(origTask);
		newTask.dependsOn("genSrgs", "jar");

		newTask.setInJar(newTarget);
		newTask.setOutJar(originalTarget);
	}
}
