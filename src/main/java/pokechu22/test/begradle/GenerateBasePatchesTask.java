package pokechu22.test.begradle;

import org.gradle.api.DefaultTask;

public class GenerateBasePatchesTask extends DefaultTask {
	private Object patchesFolder;
	private Object unpatchedSource;
	private Object patchedSource;

	public void setPatchesFolder(Object folder) {
		this.patchesFolder = folder;
	}
	public void setUnpatchedSourceFolder(Object folder) {
		this.unpatchedSource = folder;
	}
	public void setPatchedSourceFolder(Object folder) {
		this.patchedSource = folder;
	}
}
