package pokechu22.test.begradle.customsrg;

import net.minecraftforge.gradle.common.Constants;

/**
 * @see Constants
 */
public class CustomSrgConstants {
	/**
	 * Like {@link Constants#REPLACE_MCP_CHANNEL}, but for our purposes.
	 */
	public static final String REPLACE_CUSTOM_SRG_SPECIFIER = "{MAPPING_SRG_SPECIFIER}";

	/**
	 * Task that generates MCP csvs.
	 */
	public static final String TASK_GENERATE_CSVS = "genCsvs";

	/**
	 * @see Constants#DIR_MCP_MAPPINGS
	 */
	public static final String DIR_CUSTOM_MCP_MAPPINGS = Constants.REPLACE_CACHE_DIR + "/de/oceanlabs/mcp/mcp_" + REPLACE_CUSTOM_SRG_SPECIFIER + "/" + Constants.REPLACE_MCP_VERSION;
	/**
	 * @see Constants#CSV_METHOD
	 */
	public static final String CUSTOM_CSV_METHOD       = DIR_CUSTOM_MCP_MAPPINGS + "/methods.csv";
	/**
	 * @see Constants#CSV_FIELD
	 */
	public static final String CUSTOM_CSV_FIELD        = DIR_CUSTOM_MCP_MAPPINGS + "/fields.csv";
	// We're still not touching the params CSV, as it's harder to handle correctly (and less important)
	// public static final String CUSTOM_CSV_PARAM        = DIR_CUSTOM_MCP_MAPPINGS + "/params.csv";
	/** @see Constants#SRG_NOTCH_TO_SRG */
	public static final String CUSTOM_SRG_NOTCH_TO_SRG = DIR_CUSTOM_MCP_MAPPINGS + "/" + Constants.REPLACE_MC_VERSION + "/srgs/notch-srg.srg";
	/** @see Constants#SRG_NOTCH_TO_MCP */
	public static final String CUSTOM_SRG_NOTCH_TO_MCP = DIR_CUSTOM_MCP_MAPPINGS + "/" + Constants.REPLACE_MC_VERSION + "/srgs/notch-mcp.srg";
	/** @see Constants#SRG_SRG_TO_MCP */
	public static final String CUSTOM_SRG_SRG_TO_MCP   = DIR_CUSTOM_MCP_MAPPINGS + "/" + Constants.REPLACE_MC_VERSION + "/srgs/srg-mcp.srg";
	/** @see Constants#SRG_MCP_TO_SRG */
	public static final String CUSTOM_SRG_MCP_TO_SRG   = DIR_CUSTOM_MCP_MAPPINGS + "/" + Constants.REPLACE_MC_VERSION + "/srgs/mcp-srg.srg";
	/** @see Constants#SRG_MCP_TO_NOTCH */
	public static final String CUSTOM_SRG_MCP_TO_NOTCH = DIR_CUSTOM_MCP_MAPPINGS + "/" + Constants.REPLACE_MC_VERSION + "/srgs/mcp-notch.srg";
	/** @see Constants#EXC_SRG */
	public static final String CUSTOM_EXC_SRG          = DIR_CUSTOM_MCP_MAPPINGS + "/" + Constants.REPLACE_MC_VERSION + "/srgs/srg.exc";
	/** @see Constants#EXC_MCP */
	public static final String CUSTOM_EXC_MCP          = DIR_CUSTOM_MCP_MAPPINGS + "/" + Constants.REPLACE_MC_VERSION + "/srgs/mcp.exc";

	// And finally, new constants
	public static final String CUSTOM_SRG_LOG = DIR_CUSTOM_MCP_MAPPINGS + "/" + Constants.REPLACE_MC_VERSION + "/srgs/custom_srg.txt";
	public static final String CUSTOM_REVERSE_SRG = DIR_CUSTOM_MCP_MAPPINGS + "/" + Constants.REPLACE_MC_VERSION + "/srgs/reverse.srg";
}
