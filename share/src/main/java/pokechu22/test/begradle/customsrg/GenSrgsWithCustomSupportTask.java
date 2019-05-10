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
import com.google.common.base.Joiner;
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

	private Object remappedInExc;
	/**
	 * Gets the location to save the remapped version of the in EXC.
	 * @return The remapped in EXC.
	 */
	public File getRemappedInExc() {
		return getProject().file(remappedInExc);
	}
	/**
	 * Sets the location to save the remapped version of the in EXC.
	 * @param remappedInExc The remapped in EXC.
	 */
	public void setRemappedInExc(Object remappedInExc) {
		this.remappedInExc = remappedInExc;
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
        SrgContainer extraSrg = new SrgContainer().readSrgs(getExtraSrgs());
        SrgContainer inSrg = new SrgContainer().readSrg(getInSrg());
        SrgContainer newSrg = remapSrg(inSrg, extraSrg);
        writeOutSrgs(newSrg, methods, fields);

        // do EXC stuff
        this.makeRemappedInExc(extraSrg);
        writeOutExcs(newSrg, Collections.emptyMap(), methods);

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

	// Hack -- super.writeOutExcs uses getInExc; redirect it when needed.
	private boolean writingExcs = false;
	@Override
	public File getInExc() {
		if (this.writingExcs) {
			return this.getRemappedInExc();
		}
		return super.getInExc();
	}

	protected void writeOutExcs(SrgContainer inSrg, Map<String, String> excRemap, Map<String, String> methods) throws IOException {
		try {
			this.writingExcs = true;
			writeOutExcs.invoke(this, inSrg, excRemap, methods);
			this.writingExcs = false;
		} catch (IllegalAccessException | IllegalArgumentException ex) {
			throw new AssertionError("Failed to invoke writeOutExcs", ex);
		} catch (InvocationTargetException ex) {
			throw new RuntimeException("Failed to invoke writeOutExcs", ex);
		}
	}

	/**
	 * Remaps one srg with the extra SRG. Loosely based off of
	 * {@link GenSrgs#readExtraSrgs}, specifically the commented out portion.
	 *
	 * @param inSrg
	 *            The original SRG.
	 * @param extraSrg
	 *            The extra SRG. Only its {@link SrgContainer#classMap classMap} is
	 *            used.
	 * @return The remapped SRG
	 */
	protected static SrgContainer remapSrg(SrgContainer inSrg, SrgContainer extraSrg) {
		SrgContainer outSrg = new SrgContainer();

		// Remap classes
		for (Entry<String, String> e : inSrg.classMap.entrySet()) {
			String newClass = remapClass(e.getValue(), extraSrg.classMap);
			outSrg.classMap.put(e.getKey(), newClass);
		}

		// Remap methods
		for (Entry<MethodData, MethodData> e : inSrg.methodMap.entrySet()) {
			String newSig = remapMethodDescriptor(e.getValue().sig, extraSrg.classMap);
			String newName = remapQualifiedName(e.getValue().name, extraSrg.classMap);
			outSrg.methodMap.put(e.getKey(), new MethodData(newName, newSig));
		}

		// Remap fields
		for (Entry<String, String> e : inSrg.fieldMap.entrySet()) {
			String newName = remapQualifiedName(e.getValue(), extraSrg.classMap);
			outSrg.fieldMap.put(e.getKey(), newName);
		}

		// Don't do anything with packages
		outSrg.packageMap.putAll(inSrg.packageMap);

		return outSrg;
	}

	/**
	 * Remaps the contents of the EXC file, based on the SRG.
	 *
	 * @param extraSrg The extra SRG. Only its {@link SrgContainer#classMap
	 *                 classMap} is used.
	 * @throws IOException when an IO error occurs.
	 */
	protected void makeRemappedInExc(SrgContainer extraSrg) throws IOException {
		List<String> inLines = Files.readLines(super.getInExc(), Charsets.UTF_8);
		List<String> outLines = new ArrayList<>();
		Joiner comma = Joiner.on(',');

		for (String line : inLines) {
			if (line.startsWith("#")) {
				outLines.add(line);
			} else {
				String[] pts = line.split("=");

				// Target method signature - e.g. net/minecraft/Foo.doThing(Ljava/lang/Object;)V
				String method = pts[0];
				// Contains Exception1,Exception2|param1,param2
				String value = pts[1];

				// Remap method class and signature
				int dotIndex = method.indexOf('.');
				int sigIndex = method.indexOf('(');
				if (dotIndex == -1 || sigIndex == -1) {
					// Not actually method -- e.g. max_constructor_index
					outLines.add(line);
					continue;
				}
				if (dotIndex > sigIndex) {
					throw new RuntimeException("Weird EXC line: '" + line + "'");
				}

				String oldClass = method.substring(0, dotIndex);
				String name = method.substring(dotIndex + 1, sigIndex);
				String oldSignature = method.substring(sigIndex);

				String newClass = remapClass(oldClass, extraSrg.classMap);
				String newSignature = remapMethodDescriptor(oldSignature, extraSrg.classMap);
				String newMethod = newClass + "." + name + newSignature;

				String newValue;
				if (!method.endsWith("Access")) {
					int lineIndex = value.indexOf('|');
					String exceptionsLine, remainder;
					if (lineIndex >= 0) {
						exceptionsLine = value.substring(0, lineIndex);
						remainder = value.substring(lineIndex + 1);
					} else {
						exceptionsLine = value;
						remainder = "";
					}

					String[] exceptions;
					if (!exceptionsLine.isEmpty()) {
						exceptions = exceptionsLine.split(",");
					} else {
						// "".split(",") produces a 1-element array containing the empty string
						exceptions = new String[0];
					}

					String[] newExceptions = new String[exceptions.length];
					for (int i = 0; i < exceptions.length; i++) {
						newExceptions[i] = remapClass(exceptions[i], extraSrg.classMap);
					}

					newValue = comma.join(newExceptions) + '|' + remainder;
				} else {
					newValue = value;
				}
				outLines.add(newMethod + '=' + newValue);
			}
		}

		try (BufferedWriter out = Files.newWriter(getRemappedInExc(), Charsets.UTF_8)) {
			for (String line : outLines) {
				out.write(line);
				out.newLine();
			}
		}
	}

	/**
	 * Remaps object types in a JVM <a href=
	 * "https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.3">
	 * method descriptor</a>. As per the documentation on <a href=
	 * "https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.2">field
	 * descriptors</a>, this means we need to remap things in the form of:
	 * <code>L<u>fully/qualified/class/Name</u>;</code>, changing the underlined
	 * portion.
	 *
	 * <p>
	 * Based on the method originally removed in f35ae3952a735efc907da9afb584a9029e852b79.
	 *
	 * @param sig
	 *            The signature, e.g. <code>(ILcom/example/Obj;I)V</code>
	 * @param classMap
	 *            The class map to use to rename, e.g. <code>{com/example/Obj &rarr;
	 *            com/example/Thing}</code>
	 * @return The new signature, e.g. <code>(ILcom/example/Thing;I)V</code>
	 */
	protected static String remapMethodDescriptor(String sig, Map<String, String> classMap) {
		StringBuilder newSig = new StringBuilder(sig.length());

		int last = 0;
		int start = sig.indexOf('L');
		while (start != -1) {
			newSig.append(sig.substring(last, start));
			int next = sig.indexOf(';', start);
			newSig.append('L').append(remapClass(sig.substring(start + 1, next), classMap)).append(';');
			last = next + 1;
			start = sig.indexOf('L', next);
		}
		newSig.append(sig.substring(last));

		return newSig.toString();
	}

	/**
	 * Remaps a fully qualified member (i.e. method or field) name.
	 *
	 * @param qualifiedName
	 *            The fully qualified name, e.g. <code>com/example/Obj/doIt</code>
	 * @param classMap
	 *            The class map to use to rename, e.g. <code>{com/example/Obj &rarr;
	 *            com/example/Thing}</code>
	 * @return The new name, e.g. <code>com/example/Thing/doIt</code>
	 */
	protected static String remapQualifiedName(String qualifiedName, Map<String, String> classMap) {
		String cls = qualifiedName.substring(0, qualifiedName.lastIndexOf('/'));
		String name = qualifiedName.substring(cls.length() + 1);

		return remapClass(cls, classMap) + '/' + name;
	}

	/**
	 * Remaps a class name.
	 *
	 * <p>
	 * Based on the method originally removed in f35ae3952a735efc907da9afb584a9029e852b79.
	 *
	 * @param className
	 *            The original name of the class
	 * @param classMap
	 *            The class map to use to rename
	 * @return The new name if contained in the map, else the old name
	 */
	protected static String remapClass(String className, Map<String, String> classMap) {
		if (classMap.containsKey(className)) {
			return classMap.get(className);
		} else {
			return className;
		}
	}

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
