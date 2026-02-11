package com.github.knokko.boiler.utilities;

import org.lwjgl.system.Platform;

import java.util.ArrayList;
import java.util.Collections;

/**
 * This is a utility class containing only the static method {@link #neededRestart()}.
 */
public class EnsureMainThread {

	/**
	 * <p>
	 *     This method can be used to automatically 'add' the <b>-XstartOnFirstThread</b> JVM argument when an
	 *     application is running on macOS. This JVM argument is needed on macOS because macOS only allows the main
	 *     thread to create and interact with windows (many Vulkan applications need to create windows...). Without
	 *     this JVM argument, the Java main thread will <b>not</b> be the native main thread, causing any window
	 *     creations/interactions to fail.
	 * </p>
	 *
	 * <h3>Considerations</h3>
	 * <p>
	 *     If you ship your application to macOS and need to create at least one window, you basically have
	 *     3 options:
	 * </p>
	 *
	 * <ul>
	 *     <li>
	 *         Distribute a native launcher that will launch Java with the <b>-XstartOnFirstThread</b> JVM argument.
	 *         This approach is probably the best way... <b>if</b> you have an Apple developer certificate.
	 *     </li>
	 *     <li>
	 *         Distribute a (fat) jar to your end users, and tell them to launch it via
	 *         <b>java -XstartOnFirstThread -jar your-game.jar</b>
	 *     </li>
	 *     <li>
	 *         Call this method at the start of your main method, exit/return if it returns {@code true}, distribute
	 *         a (fat) jar to your end users, and tell them to launch it via <b>java -jar your-game.jar</b>
	 *     </li>
	 * </ul>
	 *
	 * <p>
	 *     Options 2 and 3 bypass the need of a developer certificate by relying on the certificate of the Java
	 *     runtime that is hopefully installed on the computer of the end user. The difference between option 2 and
	 *     option 3 is that option 3 does not need the user to add <b>-XstartOnFirstThread</b>.
	 * </p>
	 *
	 * <p>
	 *     Unfortunately, option 3 does <b>not</b> really allow end users to start the application by double-clicking
	 *     the jar file, unless they mess with their OS settings and go through several confirmation popups...
	 * </p>
	 *
	 * <h3>Usage</h3>
	 * <p>
	 *     You should use this method by calling it at/near the start of your main method, and return/exit when it
	 *     returns {@code true}. Something like
	 *     {@code public static void main(String[] args) { if (EnsureMainThread.neededRestart()) {return;} restOfYourProgram();}}
	 * </p>
	 *
	 * <h3>Inner workings</h3>
	 * <p>
	 *     First of all, if the current OS is <b>not</b> macOS, this method will immediately return {@code false}.
	 *     But, if it <b>is</b> running on macOS, it tries to check whether the JVM was launched with
	 *     <b>-XstartOnFirstThread</b>. If yes, it returns {@code false}. If not, it starts a new JVM process with the
	 *     same command that was used to start this JVM process, but with an additional <b>-XstartOnFirstThread</b>
	 *     argument. It redirects all IO of that process, and returns {@code true} after the child process is
	 *     finished.
	 * </p>
	 *
	 * <h3>Limitations</h3>
	 * <p>
	 *     Currently, this method only works if {@code ProcessHandle.current().info().arguments()} and
	 *     {@code ProcessHandle.current().info().command()} are both present. This is true when an application is
	 *     launched from java -jar, but this is not always true when running through Gradle or an IDE. In such
	 *     development scenarios, this method will print a warning, and return {@code false}. Developers that run on
	 *     macOS may have to add the JVM argument manually.
	 * </p>
	 *
	 * @return True if and only if this method launched a new JVM process (and you should exit the current JVM process)
	 */
	public static boolean neededRestart() {
		if (Platform.get() != Platform.MACOSX) return false;

		String requiredJvmArgument = "-XstartOnFirstThread";
		var thisProgress = ProcessHandle.current().info();
		var thisExecutable = thisProgress.command();
		var theseArguments = thisProgress.arguments();
		if (thisExecutable.isPresent() && theseArguments.isPresent()) {
			var oldArgs = theseArguments.get();
			for (String oldArg : oldArgs) {
				if (requiredJvmArgument.equals(oldArg)) return false;
			}

			var newCommand = new ArrayList<String>();
			newCommand.add(thisExecutable.get());
			newCommand.add(requiredJvmArgument);
			Collections.addAll(newCommand, oldArgs);

			try {
				new ProcessBuilder(newCommand).inheritIO().start().onExit().get();
			} catch (Exception failedToRestart) {
				throw new RuntimeException(failedToRestart);
			}
			return true;
		}

		System.out.println("EnsureMainThread.neededRestart() failed: you may need to add " +
				requiredJvmArgument + " manually. This should normally happen only in development.");
		return false;
	}
}
