package pokechu22.test.begradle;

import java.util.concurrent.Callable;

import pokechu22.test.begradle.BasePatchSettings;

/**
 * Settings to patch
 */
public class BasePatchSettings {
	Object patchFolder;
	Object patchedSourceFolder;

	public BasePatchSettings(String sourceSetName) {
		this.patchFolder = "src/" + sourceSetName + "/base-patches";
		this.patchedSourceFolder = "src/" + sourceSetName + "/base";
	}

	public void setPatchFolder(Object folder) {
		this.patchFolder = folder;
	}

	public Object getPatchFolder() {
		return patchFolder;
	}

	/**
	 * Gets a {@link Callable} that returns the value of {@link #getPatchFolder}.
	 * 
	 * @return A callable that expands to {@link #getPatchFolder}
	 */
	Callable<Object> getPatchFolderCallable() {
		return new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				return getPatchFolder();
			}
		};
	}

	public void setPatchedSourceFolder(Object folder) {
		this.patchedSourceFolder = folder;
	}

	public Object getPatchedSourceFolder() {
		return patchedSourceFolder;
	}

	/**
	 * Gets a {@link Callable} that returns the value of {@link #getPatchedSourceFolder}.
	 * 
	 * @return A callable that expands to {@link #getPatchedSourceFolder}
	 */
	Callable<Object> getPatchedSourceFolderCallable() {
		return new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				return getPatchedSourceFolder();
			}
		};
	}
}
