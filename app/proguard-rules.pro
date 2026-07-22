# GS Compiler ProGuard Rules
-keep class com.mehdigm.compiler.compiler.NativeCompiler { native <methods>; *; }
-keep interface com.mehdigm.compiler.compiler.CompilationCallback { *; }
-keep class com.mehdigm.compiler.model.** { *; }
-keep class com.mehdigm.compiler.ui.console.ConsoleEntry { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn androidx.compose.**
