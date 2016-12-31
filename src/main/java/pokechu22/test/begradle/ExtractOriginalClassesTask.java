package pokechu22.test.begradle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public class ExtractOriginalClassesTask extends DefaultTask {
	public ExtractOriginalClassesTask() {
		this.onlyIf(new Spec<Task>() {
			@Override
			public boolean isSatisfiedBy(Task element) {
				return !((ExtractOriginalClassesTask)element).classList.isEmpty();
			}
		});
	}

	private Set<String> classList = new HashSet<>();
	private Object inJar;

	@Input
	public Set<String> getClassList() {
		return classList;
	}
	
	@OutputDirectory
	public File getOutputFolder() {
		return getProject().file(this.getTemporaryDir());
	}

	public void setClassList(Set<String> classList) {
		this.classList = classList;
	}

	public void addClass(String clazz) {
		this.classList.add(clazz);
	}

	public void addClasses(Collection<String> classes) {
		this.classList.addAll(classes);
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

	public void setJar(Object file) {
		this.inJar = file;
	}

	@InputFile
	public Object getJar() {
		return inJar;
	}
}
