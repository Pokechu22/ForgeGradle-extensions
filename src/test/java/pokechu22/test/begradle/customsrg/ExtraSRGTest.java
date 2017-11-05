package pokechu22.test.begradle.customsrg;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import org.junit.Ignore;
import org.junit.Test;

import net.minecraftforge.srg2source.rangeapplier.MethodData;
import net.minecraftforge.srg2source.rangeapplier.SrgContainer;

/**
 * Tests the behavior of readExtraSrgs.
 */
public class ExtraSRGTest {

	@Test
	public void testEmptyExtra() {
		SrgContainer extra = new SrgContainer();
		SrgContainer main = new SrgContainer();
		main.classMap.put("a", "foo/Example");
		SrgContainer outSrg = GenSrgsWithCustomSupportTask.remapSrg(main, extra);
		assertThat(outSrg.classMap, hasEntry("a", "foo/Example"));
	}

	@Test
	public void testClassRemap() {
		SrgContainer extra = new SrgContainer();
		extra.classMap.put("foo/Example", "foo/Thing");
		SrgContainer main = new SrgContainer();
		main.classMap.put("a", "foo/Example");
		SrgContainer outSrg = GenSrgsWithCustomSupportTask.remapSrg(main, extra);
		assertThat(outSrg.classMap, hasEntry("a", "foo/Thing"));
	}

	@Test
	@Ignore("Known failure")
	/** Checks remapping of the containing class */
	public void testMethodRemapClass() {
		SrgContainer extra = new SrgContainer();
		extra.classMap.put("foo/Example", "foo/Thing");
		SrgContainer main = new SrgContainer();
		main.classMap.put("a", "foo/Example");
		main.methodMap.put(new MethodData("a/b", "()V"),
				new MethodData("foo/Example/doThing()", "()V"));
		SrgContainer outSrg = GenSrgsWithCustomSupportTask.remapSrg(main, extra);
		assertThat(outSrg.methodMap, hasEntry(new MethodData("a/b", "()V"),
				new MethodData("foo/Thing/doThing", "()V")));
	}

	@Test
	@Ignore("Known failure")
	/** Checks remapping of the return type */
	public void testMethodRemapReturnType() {
		SrgContainer extra = new SrgContainer();
		extra.classMap.put("foo/Example", "foo/Thing");
		SrgContainer main = new SrgContainer();
		main.classMap.put("a", "foo/Example");
		main.classMap.put("b", "foo/SomeObj");
		main.methodMap.put(new MethodData("b/c", "()La;"),
				new MethodData("foo/SomeObj/get", "()Lfoo/Example;"));
		SrgContainer outSrg = GenSrgsWithCustomSupportTask.remapSrg(main, extra);
		assertThat(outSrg.methodMap, hasEntry(new MethodData("b/c", "()La;"),
				new MethodData("foo/SomeObj/get", "()Lfoo/Thing;")));
	}

	@Test
	@Ignore("Known failure")
	/** Checks remapping of the return type */
	public void testMethodRemapParamType() {
		SrgContainer extra = new SrgContainer();
		extra.classMap.put("foo/Example", "foo/Thing");
		SrgContainer main = new SrgContainer();
		main.classMap.put("a", "foo/Example");
		main.classMap.put("b", "foo/SomeObj");
		main.methodMap.put(new MethodData("b/d", "(La;)V"),
				new MethodData("foo/SomeObj/set", "(Lfoo/Example;)V"));
		SrgContainer outSrg = GenSrgsWithCustomSupportTask.remapSrg(main, extra);
		assertThat(outSrg.methodMap, hasEntry(new MethodData("b/d", "(La;)V"),
				new MethodData("foo/SomeObj/set", "(Lfoo/Thing;)V")));
	}
}
