package pokechu22.test.begradle.noinplacereobf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.jvm.tasks.Jar;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.user.TaskExtractAnnotations;
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
		File workJar = getWorkJar("reobfFakeTarget", reobf);
		File emptyRemovedJar = getWorkJar("noEmptyFolders", reobf);

		reobf.setJar(workJar);

		reobf.doFirst(copy(newTarget, workJar));
		reobf.doLast(copyRemovingEmptyFolders(workJar, emptyRemovedJar));

		// Decide on what to do with the extract task after evaluation
		project.afterEvaluate(p -> {
			// The extractAnnotationsJar also works in-place, currently.  Stop that too.
			// Also, it previously targeted the wrong jar, which is a problem.

			// However: on base edits, we do not want the extract task enabled at all.
			Task extractTask = project.getTasks().findByName("extractAnnotationsJar");
			if (extractTask != null && extractTask.getEnabled()) {
				TaskExtractAnnotations extract = (TaskExtractAnnotations) extractTask;

				File extractWorkJar = getWorkJar("extractFakeTarget", extract);
				extract.setJar(extractWorkJar);

				extract.doFirst(copy(emptyRemovedJar, extractWorkJar));
				extract.doLast(copy(extractWorkJar, origTarget));
			} else {
				reobf.doLast(copy(emptyRemovedJar, origTarget));
			}
		});
	}

	private File getWorkJar(String name, Task task) {
		try {
			File workJar = File.createTempFile(name, ".jar", task.getTemporaryDir());
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

	private Action<? super Task> copyRemovingEmptyFolders(Object from, Object to) {
		return (t) -> {
			File fromFile = t.getProject().file(from);
			File toFile = t.getProject().file(to);
			try (ZipFile zip = new ZipFile(fromFile)) {
				List<ZipEntry> files = zip.stream()
						.filter(e -> !e.isDirectory())
						.collect(Collectors.toList());
				Predicate<ZipEntry> fileOrHasChildren = (dir) -> {
					return !dir.isDirectory() || files.stream()
							.anyMatch(e -> e.getName().startsWith(dir.getName()));
				};
				if (t.getLogger().isDebugEnabled()) {
					zip.stream().filter(fileOrHasChildren.negate()).forEachOrdered((e) -> {
						t.getLogger().debug("Removing folder: " + e.getName());
					});
				}
				List<ZipEntry> entriesToKeep = zip.stream()
						.filter(fileOrHasChildren)
						.collect(Collectors.toList());

				try (FileOutputStream outStream = new FileOutputStream(toFile)) {
					try (ZipOutputStream stream = new ZipOutputStream(outStream)) {
						for (ZipEntry entry : entriesToKeep) {
							stream.putNextEntry(entry);
							try (InputStream entryStream = zip.getInputStream(entry)) {
								IOUtils.copy(entryStream, stream);
							}
							stream.closeEntry();
						}
					}
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		};
	}
}
