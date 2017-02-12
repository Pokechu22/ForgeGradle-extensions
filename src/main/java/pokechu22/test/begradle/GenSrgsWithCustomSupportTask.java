package pokechu22.test.begradle;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraftforge.gradle.tasks.GenSrgs;
import net.minecraftforge.gradle.user.UserBasePlugin;
import net.minecraftforge.srg2source.rangeapplier.MethodData;
import net.minecraftforge.srg2source.rangeapplier.SrgContainer;

import org.gradle.api.file.FileCollection;

/**
 * Fixes adding extra SRGs (since that's broken in ForgeGradle)
 */
public class GenSrgsWithCustomSupportTask extends GenSrgs {
	protected boolean hasCopied = false;
	protected boolean hasCustomSrgs = false;

	/**
	 * Copy settings from the previous version of this task.
	 *
	 * @param oldTask The original genSrgs task
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void copyFrom(GenSrgs oldTask) {
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
	}

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
	private static void readCSVs(File methodCsv, File fieldCsv, Map<String, String> methodMap, Map<String, String> fieldMap) {
		try {
			readCSVs.invoke(null, methodCsv, fieldCsv, methodMap, fieldMap);
		} catch (IllegalAccessException | IllegalArgumentException ex) {
			throw new AssertionError("Failed to invoke readCSVs", ex);
		} catch (InvocationTargetException ex) {
			throw new RuntimeException("Failed to invoke readCSVs", ex);
		}
	}
	private void writeOutSrgs(SrgContainer inSrg, Map<String, String> methods, Map<String, String> fields) throws IOException {
		try {
			writeOutSrgs.invoke(this, inSrg, methods, fields);
		} catch (IllegalAccessException | IllegalArgumentException ex) {
			throw new AssertionError("Failed to invoke writeOutSrgs", ex);
		} catch (InvocationTargetException ex) {
			throw new RuntimeException("Failed to invoke readwriteOutSrgsCSVs", ex);
		}
	}
	private void writeOutExcs(SrgContainer inSrg, Map<String, String> excRemap, Map<String, String> methods) throws IOException {
		try {
			writeOutExcs.invoke(this, inSrg, excRemap, methods);
		} catch (IllegalAccessException | IllegalArgumentException ex) {
			throw new AssertionError("Failed to invoke writeOutExcs", ex);
		} catch (InvocationTargetException ex) {
			throw new RuntimeException("Failed to invoke writeOutExcs", ex);
		}
	}

	// The important method.  Contents was in a comment block.
	private Map<String, String> readExtraSrgs(FileCollection extras, SrgContainer inSrg) {
		// Begin quote
        SrgContainer extraSrg = new SrgContainer().readSrgs(extras);
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
    private String remapMethodName(String qualified, String notchSig, Map<String, String> classMap, Map<MethodData, MethodData> methodMap)
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

    private String remapSig(String sig, Map<String, String> classMap)
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

    private static String remap(String thing, Map<String, String> map)
    {
        if (map.containsKey(thing))
            return map.get(thing);
        else
            return thing;
    }
	// End quote

	@Override
	public void addExtraSrg(File file) {
		if (!hasCustomSrgs) {
			// Added new custom SRGs; tell the project that it must use the local cache
			// This doesn't happen automatically unfortunately.
			// This needs to be done here instead of the task action because it must happen
			// even if the overall task is skipped.
			// No setter for this, so we use reflection
			Field field;
			try {
				field = UserBasePlugin.class.getDeclaredField("useLocalCache");
				field.setAccessible(true);
			} catch (NoSuchFieldException | SecurityException ex) {
				throw new RuntimeException("Failed to get useLocalCache field", ex);
			}

			for (UserBasePlugin<?> plugin : getProject().getPlugins()
					.withType(UserBasePlugin.class)) {
				getLogger().debug("Enabling useLocalCache for " + plugin);
				try {
					field.setBoolean(plugin, true);
				} catch (IllegalArgumentException | IllegalAccessException ex) {
					getLogger().warn("Failed to set useLocalCache for " + plugin, ex);
				}
			}
		}

		hasCustomSrgs = true;
		super.addExtraSrg(file);
	}
}
