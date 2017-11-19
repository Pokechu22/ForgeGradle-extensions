package pokechu22.test.begradle.noinplacereobf;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;

import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.user.TaskSingleReobf;

public class TaskSingleReobf2 extends TaskSingleReobf {
	// Non-annotated fields, because FG doesn't use that system
	protected final File workJar;
	protected Object inJar;
	protected Object outJar;

	public TaskSingleReobf2() throws IOException {
		workJar = File.createTempFile("reobfFakeTarget", ".jar", getTemporaryDir());
		super.setJar(workJar);
		workJar.deleteOnExit();
	}

	public File getInJar() {
		return getProject().file(this.inJar);
	}
	public void setInJar(Object inJar) {
		this.inJar = inJar;
	}

	public File getOutJar() {
		return getProject().file(this.outJar);
	}
	public void setOutJar(Object outJar) {
		this.outJar = outJar;
	}

	@Override
	@TaskAction
	public void doTask() throws IOException {
		File inJar = getInJar();
		File outJar = getOutJar();

		// This is a bit of a redundant process, as super.doTask also copies from temp
		// locations.  But it's not avoidable :/
		Constants.copyFile(inJar, workJar);
		super.doTask();
		Constants.copyFile(workJar, outJar);
	}

	@Override
	public void setJar(Object jar) {
		throw new RuntimeException("Use setInJar/setOutJar instead of setJar");
	}
	/**
	 * Copies fields from the superclass
	 * @param origTask The task to copy from
	 */
	public void copyFrom(TaskSingleReobf origTask) {
		try {
			for (Field field : TaskSingleReobf.class.getDeclaredFields()) {
				field.setAccessible(true);
				field.set(this, field.get(origTask));
			}
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
		origTask.setActions(Collections.emptyList());
		origTask.doFirst((t) -> {
			throw new RuntimeException("Wrong task instance was called!");
		});
		origTask.setJar(workJar);
	}
}
