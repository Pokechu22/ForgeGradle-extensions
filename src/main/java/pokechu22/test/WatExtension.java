package pokechu22.test;

import groovy.lang.Closure;
import net.minecraftforge.gradle.user.UserBaseExtension;

import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.jvm.tasks.Jar;

import com.google.common.base.Strings;

public class WatExtension extends UserBaseExtension {
	public final WatPlugin plugin;
	private Asdf bla = new Asdf();

	public WatExtension(WatPlugin plugin) {
		super(plugin);
		this.plugin = plugin;
	}
	
	public void setBla(Closure<?> configureClosure) {
		System.out.println(this.bla);
		ClosureBackedAction.execute(this.bla, configureClosure);
		System.out.println(this.bla);
	}

	@Override
	public void setVersion(String version) {
		super.setVersion(version);
		System.out.println(version);

		Jar jar = (Jar) project.getTasks().getByName("jar");
		if (Strings.isNullOrEmpty(jar.getClassifier())) {
			jar.setClassifier("mc" + version);
		}
	}
	
	public static class Asdf {
		public String a, b;
		@Override
		public String toString() { return "Asdf[" + a + "," + b + "]"; }
	}
}
