package pokechu22.test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public class BaseClassesTask extends DefaultTask {
	public BaseClassesTask() {
		this.onlyIf(new Spec<Task>() {
			@Override
			public boolean isSatisfiedBy(Task element) {
				return !((BaseClassesTask)element).classList.isEmpty();
			}
		});
	}

	private List<String> classList;
	private Object inJar;

	@Input
	public List<String> getClassList() {
		return classList;
	}
	
	@OutputDirectory
	public File getOutputFolder() {
		return getProject().file(this.getTemporaryDir());
	}

	public void setClassList(List<String> classList) {
		this.classList = classList;
	}

	@TaskAction
	public void doTask() throws IOException {
		try (ZipFile zip = new ZipFile(getProject().file(inJar), ZipFile.OPEN_READ)) {
			for (String file : classList) {
				ZipEntry in = zip.getEntry(file);
				File out = new File(getOutputFolder(), file);
				out.mkdirs();
				try (InputStream istream = zip.getInputStream(in)) {
					Files.copy(istream, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	@InputFile
	public void setJar(Object file) {
		this.inJar = file;
	}
}
