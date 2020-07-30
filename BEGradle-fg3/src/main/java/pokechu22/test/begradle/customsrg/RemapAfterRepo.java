package pokechu22.test.begradle.customsrg;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.gradle.api.Project;

import com.amadornes.artifactural.api.artifact.ArtifactIdentifier;

import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.userdev.tasks.RenameJar;
import net.minecraftforge.srgutils.IMappingFile;

public class RemapAfterRepo extends BaseRepo {
	private final Project project;
	private final String group, name, version;
	private final String realVersion;
	private final ExtraSrgContainer srgs;

	public RemapAfterRepo(Project project, String group, String name, String version, String realVersion, ExtraSrgContainer srgs) {
		// Re-using the cache for MinecraftUserRepo, meh.
		super(Utils.getCache(project, "minecraft_user_repo"), project.getLogger());
		this.project = project;
		this.group = group;
		this.name = name;
		this.version = version;
		this.realVersion = realVersion;
		this.srgs = srgs;
	}

	@Nullable
	@Override
	protected File findFile(ArtifactIdentifier artifact) throws IOException {
		log.info("Request for " + artifact);
		String group = artifact.getGroup();
		String name = artifact.getName();
		String version = artifact.getVersion();
		String classifier = artifact.getClassifier();
		String extension = artifact.getExtension();

		if (!group.equals(this.group) || !name.equals(this.name) || !version.equals(this.version)) {
			return null;
		}

		if (classifier == null) {
			classifier = "";
		}

		String realArtifact = group + ":" + name + ":" + realVersion;
		if (!classifier.isEmpty()) {
			realArtifact += ":" + classifier;
		}
		if (!extension.isEmpty()) {
			realArtifact += "@" + extension;
		}

		if (extension.equals("pom")) {
			return MavenArtifactDownloader.manual(project, realArtifact, false);
		}

		if (classifier.equals("sources")) {
			// TODO: do proper remapping here
			return MavenArtifactDownloader.manual(project, realArtifact, false);
		} else if (classifier.isEmpty()) {
			// TODO: do caching
			File resolvedArtifact = MavenArtifactDownloader.manual(project, realArtifact, false);
			if (resolvedArtifact == null) {
				log.warn("RemapAfterRepo: Failed to resolve " + realArtifact);
				return null;
			}
			List<IMappingFile> mappingFiles = new ArrayList<>();
			for (File file : srgs.getSrgs()) {
				mappingFiles.add(IMappingFile.load(file));
			}
			Optional<IMappingFile> mappings = mappingFiles.stream().reduce(IMappingFile::chain);
			if (!mappings.isPresent()) {
				log.error("No mappings?  List " + srgs.getSrgs() + ", read to " + mappingFiles);
				return null;
			}
			Path tempFile = Files.createTempFile("RemappedSRG", ".tsrg");
			tempFile.toFile().deleteOnExit();
			mappings.get().write(tempFile, IMappingFile.Format.TSRG, false);

			File remappedDir = cache(group.replace('.', '/'), name, version);
			remappedDir.mkdirs();
			File remappedFile = new File(remappedDir, name + "-" + version + "-" + classifier + ".jar");

			// Task usage seems dubious to me, but is what FG3 does elsewhere
			RenameJar rename = project.getTasks().create("remapPatcher", RenameJar.class);
			rename.setHasLog(false);
			rename.setInput(resolvedArtifact);
			rename.setOutput(remappedFile);
			rename.setMappings(tempFile.toFile());
			rename.apply();

			return remappedFile;
		} else {
			log.warn("RemapAfterRepo: Unexpected classifier " + classifier);
			return null;
		}
	}
}
