package pokechu22.test.begradle.customsrg;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static pokechu22.test.begradle.customsrg.ExtraSRGTest.IsMapWithSize.*;

import java.util.Map;

import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
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
		assertThat(outSrg.classMap, maps("a", "foo/Example"));
	}

	@Test
	public void testClassRemap() {
		SrgContainer extra = new SrgContainer();
		extra.classMap.put("foo/Example", "foo/Thing");
		SrgContainer main = new SrgContainer();
		main.classMap.put("a", "foo/Example");
		SrgContainer outSrg = GenSrgsWithCustomSupportTask.remapSrg(main, extra);
		assertThat(outSrg.classMap, maps("a", "foo/Thing"));
	}

	@Test
	/** Checks remapping of the containing class */
	public void testMethodRemapClass() {
		SrgContainer extra = new SrgContainer();
		extra.classMap.put("foo/Example", "foo/Thing");
		SrgContainer main = new SrgContainer();
		main.classMap.put("a", "foo/Example");
		main.methodMap.put(new MethodData("a/b", "()V"),
				new MethodData("foo/Example/doThing", "()V"));
		SrgContainer outSrg = GenSrgsWithCustomSupportTask.remapSrg(main, extra);
		assertThat(outSrg.methodMap, maps(new MethodData("a/b", "()V"),
				new MethodData("foo/Thing/doThing", "()V")));
	}

	@Test
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
		assertThat(outSrg.methodMap, maps(new MethodData("b/c", "()La;"),
				new MethodData("foo/SomeObj/get", "()Lfoo/Thing;")));
	}

	@Test
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
		assertThat(outSrg.methodMap, maps(new MethodData("b/d", "(La;)V"),
				new MethodData("foo/SomeObj/set", "(Lfoo/Thing;)V")));
	}

	/**
	 * Matcher that checks that a map contains only the given entry.
	 */
	private static <A, B> Matcher<Map<? extends A, ? extends B>> maps(A key, B value) {
		return both(hasEntry(key, value)).and(isMapWithSize(1));
	}

	/** https://stackoverflow.com/a/36087146/3991344 */
	public static class IsMapWithSize<K, V>
			extends FeatureMatcher<Map<? extends K, ? extends V>, Integer> {
		public IsMapWithSize(Matcher<? super Integer> sizeMatcher) {
			super(sizeMatcher, "a map with size", "map size");
		}
		@Override
		protected Integer featureValueOf(Map<? extends K, ? extends V> actual) {
			return actual.size();
		}
		public static <K, V> Matcher<Map<? extends K, ? extends V>> isMapWithSize(int size) {
			Matcher<? super Integer> matcher = equalTo(size);
			return new IsMapWithSize<K, V>(matcher);
		}
	}
}
