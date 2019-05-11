package pokechu22.test.begradle.customsrg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.base.Joiner;

import net.minecraftforge.srg2source.rangeapplier.MethodData;
import net.minecraftforge.srg2source.rangeapplier.SrgContainer;

public class ExtraSrgUtil {

	/**
	 * Remaps one srg with the extra SRG. Loosely based off of the FG 2
	 * GenSrgs.readExtraSrgs, specifically the commented out portion.
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
	 * @param inLines  The lines in the input EXC file.
	 * @param extraSrg The extra SRG. Only its {@link SrgContainer#classMap
	 *                 classMap} is used.
	 * @return The lines of the remapped file.
	 */
	public static List<String> remapExc(List<String> inLines, SrgContainer extraSrg) {
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
		return outLines;
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
}
