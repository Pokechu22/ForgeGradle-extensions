package pokechu22.test.begradle;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraftforge.gradle.common.BaseExtension;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.user.UserBaseExtension;
import net.minecraftforge.gradle.util.GradleConfigurationException;

import org.gradle.api.InvalidUserDataException;
import org.gradle.jvm.tasks.Jar;

import com.google.common.base.Strings;

public class BaseEditExtension extends UserBaseExtension {
	public final BaseEditPlugin plugin;
	/**
	 * Since both {@link #setMappings} and {@link #setVersion} call
	 * {@link #checkMappings()}, we can't suppress warnings after either have
	 * been called (the warnings are already fired).
	 */
	private boolean canSuppressWarnings = true;
	private boolean suppressMappingWarnings = false;

	public BaseEditExtension(BaseEditPlugin plugin) {
		super(plugin);
		this.plugin = plugin;
	}

	/**
	 * Optionally, suppress the
	 * "This mapping 'snapshot_xxxxxxxx' was designed for MC 1.x.x! Use at your own peril."
	 * warnings. This is useful if you want to use multiple mappings for the
	 * same versions.
	 * <br>
	 * Must be called before either {@link #setMappings} or {@link #setVersion} are called.
	 * 
	 * @param value
	 *            New value
	 */
	public void setSuppressMappingVersionWarnings(boolean value) {
		if (!canSuppressWarnings) {
			throw new InvalidUserDataException(
					"Cannot suppress mapping version warnings after the " +
					"verison or mappings have already been specified!  " +
					"Please move the warning suppression above the version " +
					"assignment.  This is due to warnings occuring while " +
					"assigning the versions, sorry.");
		}
		this.suppressMappingWarnings = value;
	}

	/**
	 * Should the warnings relating to mismatched mappings be suppressed?
	 * @return True if they should
	 * @see #setSuppressMappingVersionWarnings
	 */
	public boolean getSuppressMappingVersionWarnings() {
		return suppressMappingWarnings;
	}

	@Override
	public void setMappings(String mappings) {
		super.setMappings(mappings);

		canSuppressWarnings = false;
	}

	@Override
	public void setVersion(String version) {
		super.setVersion(version);

		Jar jar = (Jar) project.getTasks().getByName("jar");
		if (Strings.isNullOrEmpty(jar.getClassifier())) {
			jar.setClassifier("mc" + version);
		}

		canSuppressWarnings = false;
	}

	@Override
	protected void checkMappings() {
		if (!suppressMappingWarnings) {
			// Use the parent implementation
			super.checkMappings();
			return;
		} else {
			Map<String, Map<String, int[]>> mcpJson = getMcpJson();
			if (mcpJson == null) {
				// We can't access the MCP json, so use the parent version
				// (which will complain about mismatched verisons)
				super.checkMappings();
				return;
			}
			// Use our own implementation, which mostly matches the base version,
			// but avoid a few warnings.

			// mappings or mc version are null - the mappings are fine
			if (mappingsChannel == null || Strings.isNullOrEmpty(version)) {
				return;
			}

			// Add a replacement for the minecraft version.
			replacer.putReplacement(Constants.REPLACE_MCP_MCVERSION, version);

			// If a custom mapping was specified, stop.
			if (mappingsCustom != null) {
				return;
			}

			// Check if that minecraft version exists in the map
			Map<String, int[]> versionMap = mcpJson.get(version);
			String channel = getMappingsChannelNoSubtype();
			if (versionMap != null) {
				int[] channelList = versionMap.get(channel);
				if (channelList == null)
					throw new GradleConfigurationException(
							"There is no such MCP mapping channel named "
									+ channel);

				// Check if the version exists
				Arrays.sort(channelList);
				if (Arrays.binarySearch(channelList, mappingsVersion) >= 0) {
					return;
				}
			}

			// The version doesn't exist for that MC version; try to find it.
			for (Entry<String, Map<String, int[]>> mcEntry : mcpJson.entrySet()) {
				for (Entry<String, int[]> channelEntry : mcEntry.getValue()
						.entrySet()) {
					// Check if the version exists
					int[] value = channelEntry.getValue();
					Arrays.sort(value);
					if (Arrays.binarySearch(value, mappingsVersion) >= 0) {
						boolean rightMc = mcEntry.getKey().equals(version);
						boolean rightChannel = channelEntry.getKey().equals(
								channel);

						// Right channel, but not the right MC version
						if (rightChannel && !rightMc) {
							// Normally there'd be a warning here; silence it.
							// However, we do need to point it to the right
							// version (for maven), so update the replacement.
							replacer.putReplacement(
									Constants.REPLACE_MCP_MCVERSION,
									mcEntry.getKey());
							return;
						}

						// Right MC, but wrong channel
						else if (rightMc && !rightChannel) {
							throw new GradleConfigurationException(
									"This mapping '"
											+ getMappings()
											+ "' doesn't exist! Perhaps you meant '"
											+ channelEntry.getKey() + "_"
											+ mappingsVersion + "'?");
						}
					}
				}
			}

			// The specific version doesn't exist at all
			throw new GradleConfigurationException("The specified mapping '"
					+ getMappings() + "' does not exist!");
		}
	}

	/**
	 * <blockquote
	 * cite="https://github.com/MinecraftForge/ForgeGradle/commit/15003c91">
	 * this should never be touched except by the base plugin in this
	 * package</blockquote> <cite>&mdash; AbrarSyed, comment on
	 * {@linkplain BaseExtension#mcpJson}</cite>
	 * <hr>
	 * Sure.  Totally it'll <em>never</em> need to be used.  Hah.
	 * @return {@link BaseExtension#mcpJson}, or <code>null</code> if it can't be found.
	 */
	private Map<String, Map<String, int[]>> getMcpJson() {
		try {
			Field field = BaseExtension.class.getDeclaredField("mcpJson");
			field.setAccessible(true);
			@SuppressWarnings("unchecked")
			Map<String, Map<String, int[]>> mcpJson =
					(Map<String, Map<String, int[]>>) field.get(this);
			return mcpJson;
		} catch (Exception e) {
			plugin.project.getLogger().warn(
					"Failed to 'politely get' mcp JSON from BaseExtension", e);

			return null;
		}
	}
}
