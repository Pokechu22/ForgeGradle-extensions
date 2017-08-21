package pokechu22.test.begradle.customsrg;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraftforge.gradle.tasks.GenSrgs;
import net.minecraftforge.srg2source.rangeapplier.MethodData;
import net.minecraftforge.srg2source.rangeapplier.SrgContainer;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/**
 * Handles secondary SRGs, _and_ creates reverse SRGs.
 */
public class GenSrgsWithCustomSupportTask extends GenSrgs {
	protected boolean hasCopied = false;
	protected boolean hasCustomSrgs = false;

	private Object srgLog;
	/**
	 * A file that lists the SRGs that were used, for reference (and caching?) purposes.
	 * @return The SRG log.
	 */
	@OutputFile
	public File getSrgLog() {
		return getProject().file(srgLog);
	}
	/**
	 * A file that lists the SRGs that were used, for reference purposes.
	 * @param srgLog The file to log to
	 */
	public void setSrgLog(Object srgLog) {
		this.srgLog = srgLog;
	}

	private Object reverseSrg;
	/**
	 * A SRG that reverses the extra SRGs.
	 * @return The reverse SRG
	 */
	@OutputFile
	public File getReverseSrg() {
		return getProject().file(reverseSrg);
	}
	/**
	 * A SRG that reverses the extra SRGs.
	 * @param reverseSrg The file to write the reverse SRG to.
	 */
	public void setReverseSrg(Object reverseSrg) {
		this.reverseSrg = reverseSrg;
	}

	private Object extraSrgs;
	/**
	 * Gets the auxiliary SRGs provided to this task.
	 * @return The extra SRGs.
	 */
	@Override
	@OutputFiles
	public FileCollection getExtraSrgs() {
		return getProject().files(extraSrgs);
	}
	/**
	 * Sets the auxiliary SRGs provided to this task.
	 * @param extraSrgs The extra SRG files.
	 */
	public void setExtraSrgs(Object extraSrgs) {
		this.extraSrgs = extraSrgs;
	}
	@Override
	@Deprecated
	public void addExtraSrg(File file) {
		throw new RuntimeException("addExtraSrg doesn't work; use the extraSrgs block");
	}

	/**
	 * Copy settings from the previous version of this task.
	 *
	 * @param oldTask The original genSrgs task
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected void copyFrom(GenSrgs oldTask) {
		getLogger().debug("Copying from " + oldTask);

		hasCopied = true;
		try {
			for (Field field : GenSrgs.class.getDeclaredFields()) {
				if (Modifier.isFinal(field.getModifiers())) {
					if (List.class.isAssignableFrom(field.getType())) {
						field.setAccessible(true);
						List oldList = (List) field.get(oldTask);
						List myList = (List) field.get(this);
						myList.addAll(oldList);
					} else {
						throw new RuntimeException(
								"Don't know what to do with final field "
										+ field);
					}
					continue;
				} else {
					field.setAccessible(true);
					Object value = field.get(oldTask);
					field.set(this, value);
				}
			}
		} catch (IllegalAccessException | SecurityException ex) {
			throw new RuntimeException("Failed to copy fields from " + oldTask, ex);
		}

		this.setDoesCache(oldTask.doesCache());
		this.setDependsOn(oldTask.getDependsOn());
	}

	/** Overrides original task action. */
	@Override
	public void doTask() throws IOException {
		if (!hasCopied) {
			getLogger().warn("Did not copy settings for custom SRG task; this is needed for the task to do anything.");
		}

		// Begin quote from original
        // csv data.  SRG -> MCP
        HashMap<String, String> methods = new HashMap<String, String>();
        HashMap<String, String> fields = new HashMap<String, String>();
        readCSVs(getMethodsCsv(), getFieldsCsv(), methods, fields);

        // Do SRG stuff
        SrgContainer inSrg = new SrgContainer().readSrg(getInSrg());
        Map<String, String> excRemap = readExtraSrgs(getExtraSrgs(), inSrg);
        writeOutSrgs(inSrg, methods, fields);

        // do EXC stuff
        writeOutExcs(inSrg, excRemap, methods);

		// End quote
	}

	private static final Method readCSVs, writeOutSrgs, writeOutExcs;
	static {
		try {
			Class<GenSrgs> clazz = GenSrgs.class;
			readCSVs = clazz.getDeclaredMethod("readCSVs", File.class, File.class, Map.class, Map.class);
			readCSVs.setAccessible(true);
			writeOutSrgs = clazz.getDeclaredMethod("writeOutSrgs", SrgContainer.class, Map.class, Map.class);
			writeOutSrgs.setAccessible(true);
			writeOutExcs = clazz.getDeclaredMethod("writeOutExcs", SrgContainer.class, Map.class, Map.class);
			writeOutExcs.setAccessible(true);
		} catch (NoSuchMethodException | SecurityException ex) {
			throw new AssertionError("Failed to find/access private methods", ex);
		}
	}
	protected void readCSVs(File methodCsv, File fieldCsv, Map<String, String> methodMap, Map<String, String> fieldMap) {
		try {
			// The original readCSVs was static, thus null is used
			readCSVs.invoke(null, methodCsv, fieldCsv, methodMap, fieldMap);
		} catch (IllegalAccessException | IllegalArgumentException ex) {
			throw new AssertionError("Failed to invoke readCSVs", ex);
		} catch (InvocationTargetException ex) {
			throw new RuntimeException("Failed to invoke readCSVs", ex);
		}
	}
	protected void writeOutSrgs(SrgContainer inSrg, Map<String, String> methods, Map<String, String> fields) throws IOException {
		try {
			writeOutSrgs.invoke(this, inSrg, methods, fields);
		} catch (IllegalAccessException | IllegalArgumentException ex) {
			throw new AssertionError("Failed to invoke writeOutSrgs", ex);
		} catch (InvocationTargetException ex) {
			throw new RuntimeException("Failed to invoke readwriteOutSrgsCSVs", ex);
		}
	}
	protected void writeOutExcs(SrgContainer inSrg, Map<String, String> excRemap, Map<String, String> methods) throws IOException {
		try {
			writeOutExcs.invoke(this, inSrg, excRemap, methods);
		} catch (IllegalAccessException | IllegalArgumentException ex) {
			throw new AssertionError("Failed to invoke writeOutExcs", ex);
		} catch (InvocationTargetException ex) {
			throw new RuntimeException("Failed to invoke writeOutExcs", ex);
		}
	}

	/**
	 * The important method.  Contents was in a comment block.
	 * <p>
	 * In addition to the original behavior of creating a custom EXC, this method
	 * now puts remapped classes back in the original SRG.
	 * 
	 * @param extras The extra SRGs to load
	 * @param inSrg The original SRG.
	 * @return Remapped EXC data
	 */
	protected Map<String, String> readExtraSrgs(FileCollection extras, SrgContainer inSrg) {
		SrgContainer extraSrg = new SrgContainer().readSrgs(extras);
		// Update the class mapping.
		Map<String, String> inInverseClassMap = inSrg.classMap.inverse();
		for (Map.Entry<String, String> classRemap : extraSrg.classMap.entrySet()) {
			String from = classRemap.getKey();
			String to = classRemap.getValue();
			if (inInverseClassMap.containsKey(from)) {
				String origFrom = inInverseClassMap.get(from);
				inSrg.classMap.put(origFrom, to);
			}
		}

		// Begin quote
        // Need to convert these to Notch-SRG names. and add them to the other one.
        // These Extra SRGs are in MCP->SRG names as they are denoting dev time values.
        // So we need to swap the values we get.

        HashMap<String, String> excRemap = new HashMap<String, String>(extraSrg.methodMap.size());

        // SRG -> notch map
        Map<String, String> classMap = inSrg.classMap.inverse();
        Map<MethodData, MethodData> methodMap = inSrg.methodMap.inverse();

        // rename methods
        for (Entry<MethodData, MethodData> e : extraSrg.methodMap.inverse().entrySet())
        {
            String notchSig = remapSig(e.getValue().sig, classMap);
            String notchName = remapMethodName(e.getKey().name, notchSig, classMap, methodMap);
            //getProject().getLogger().lifecycle(e.getKey().name + " " + e.getKey().sig + " " + e.getValue().name + " " + e.getValue().sig);
            //getProject().getLogger().lifecycle(notchName       + " " + notchSig       + " " + e.getValue().name + " " + e.getValue().sig);
            inSrg.methodMap.put(new MethodData(notchName, notchSig), e.getValue());
            excRemap.put(e.getKey().name, e.getValue().name);
        }

        return excRemap;
		// End quote
	}

	// These methods were removed in f35ae3952a735efc907da9afb584a9029e852b79 - begin quote
	protected String remapMethodName(String qualified, String notchSig, Map<String, String> classMap, Map<MethodData, MethodData> methodMap)
    {

        for (MethodData data : methodMap.keySet())
        {
            if (data.name.equals(qualified))
                return methodMap.get(data).name;
        }

        String cls = qualified.substring(0, qualified.lastIndexOf('/'));
        String name = qualified.substring(cls.length() + 1);
        getProject().getLogger().lifecycle(qualified);
        getProject().getLogger().lifecycle(cls + " " + name);

        String ret = classMap.get(cls);
        if (ret != null)
            cls = ret;

        return cls + '/' + name;
    }

    protected String remapSig(String sig, Map<String, String> classMap)
    {
        StringBuilder newSig = new StringBuilder(sig.length());

        int last = 0;
        int start = sig.indexOf('L');
        while(start != -1)
        {
            newSig.append(sig.substring(last, start));
            int next = sig.indexOf(';', start);
            newSig.append('L').append(remap(sig.substring(start + 1, next), classMap)).append(';');
            last = next + 1;
            start = sig.indexOf('L', next);
        }
        newSig.append(sig.substring(last));

        return newSig.toString();
    }

    protected static String remap(String thing, Map<String, String> map)
    {
        if (map.containsKey(thing))
            return map.get(thing);
        else
            return thing;
    }
	// End quote

	/**
	 * The second task action, which handles the SRG log and the reverse SRG.
	 * @throws IOException if an IO error occurs.
	 */
	@TaskAction
	public void handleTask() throws IOException {
		writeSrgLog();
		writeReverseSrg();
	}

	protected void writeSrgLog() throws IOException {
		try (BufferedWriter writer = Files.newWriter(getSrgLog(), Charsets.UTF_8)) {
			FileCollection extraSrgs = getExtraSrgs();
			writer.write("Extra SRGs:");
			writer.newLine();
			for (File file : extraSrgs) {
				writer.write(file.getCanonicalPath());
				writer.newLine();
			}
		}
	}

	protected void writeReverseSrg() throws IOException {
		// Convert to a list so that we can reverse it.
		List<File> extraSrgs = new ArrayList<File>(getExtraSrgs().getFiles());
		Collections.reverse(extraSrgs);
		// We need to reverse the list to reverse priorities; thus, duplicate entries
		// are handled in the opposite order.  Well, technically that behavior may be
		// undefined, but it seems better to do this.
		SrgContainer srg = new SrgContainer().readSrgs(extraSrgs);

		// We don't need to reverse the _order_ of the entries since a map can only
		// have one key; thus it doesn't matter.  But we _do_ need to reverse the order
		// of the elements (value -> key).

		try (BufferedWriter writer = Files.newWriter(getReverseSrg(), Charsets.UTF_8)) {
			// Packages
			for (Entry<String, String> e : srg.packageMap.entrySet()) {
				writer.write("PK: " + e.getValue() + " " + e.getKey());
				writer.newLine();
			}
			// Classes
			for (Entry<String, String> e : srg.classMap.entrySet()) {
				writer.write("CL: " + e.getValue() + " " + e.getKey());
				writer.newLine();
			}
			// Fields
			for (Entry<String, String> e : srg.fieldMap.entrySet()) {
				writer.write("FD: " + e.getValue() + " " + e.getKey());
				writer.newLine();
			}
			// Methods
			for (Entry<MethodData, MethodData> e : srg.methodMap.entrySet()) {
				// toString handles the format automatically
				writer.write("MD: " + e.getValue() + " " + e.getKey());
				writer.newLine();
			}
		}
	}
}
