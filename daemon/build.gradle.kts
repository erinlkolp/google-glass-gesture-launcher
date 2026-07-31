import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins { id("java-library") }

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile>().configureEach { options.release.set(8) }

dependencies {
    implementation(project(":gesture-core"))
    testImplementation("junit:junit:4.13.2")
}

/**
 * The `d8` binary bundled in this SDK checkout (a "-dev" build, not a stable
 * release: `D8 8.2.2-dev ... go/r8bot`) crashes with a NullPointerException
 * reading any class file compiled by JDK 21's javac, because javac 21 always
 * emits a `MethodParameters` attribute for the compiler-synthesized
 * `(String name, int ordinal)` parameters of every enum's implicit
 * constructor, and records them with no name (`name_index == 0`), which is
 * legal per the JVM spec but unhandled by this d8 build. gesture-core's
 * `Gesture` and `TouchPhase` enums trigger it unconditionally, independent of
 * anything in this project's own source. Confirmed by isolating the failure
 * down to a two-constant standalone enum compiled with the exact same
 * toolchain and reproducing the identical stack trace.
 *
 * `gesture-core` and `app` may not be modified, so the fix lives entirely in
 * this build script: strip only the `MethodParameters` attribute (which
 * carries no information `app_process`/ART needs) from method attribute
 * lists of the jar's class files before handing them to `d8`. Everything
 * else in the class file, including the `Code` attribute's own nested
 * tables, is copied through as an untouched opaque blob.
 */
fun stripMethodParametersAttribute(classBytes: ByteArray): ByteArray {
    val input = DataInputStream(ByteArrayInputStream(classBytes))
    val bytesOut = ByteArrayOutputStream(classBytes.size)
    val out = DataOutputStream(bytesOut)

    out.writeInt(input.readInt()) // magic
    out.writeShort(input.readUnsignedShort()) // minor_version
    out.writeShort(input.readUnsignedShort()) // major_version

    val constantPoolCount = input.readUnsignedShort()
    out.writeShort(constantPoolCount)

    var methodParametersIndex = -1
    var i = 1
    while (i < constantPoolCount) {
        val tag = input.readUnsignedByte()
        out.writeByte(tag)
        when (tag) {
            1 -> { // Utf8
                val length = input.readUnsignedShort()
                val bytes = ByteArray(length)
                input.readFully(bytes)
                out.writeShort(length)
                out.write(bytes)
                if (String(bytes, Charsets.US_ASCII) == "MethodParameters") {
                    methodParametersIndex = i
                }
            }
            3, 4 -> out.writeInt(input.readInt()) // Integer, Float
            5, 6 -> { // Long, Double: occupy two constant-pool slots
                out.writeLong(input.readLong())
                i++
            }
            7, 8, 16, 19, 20 -> // Class, String, MethodType, Module, Package
                out.writeShort(input.readUnsignedShort())
            9, 10, 11, 12, 17, 18 -> { // *ref, NameAndType, Dynamic, InvokeDynamic
                out.writeShort(input.readUnsignedShort())
                out.writeShort(input.readUnsignedShort())
            }
            15 -> { // MethodHandle
                out.writeByte(input.readUnsignedByte())
                out.writeShort(input.readUnsignedShort())
            }
            else -> throw IllegalStateException("Unexpected constant pool tag $tag")
        }
        i++
    }

    out.writeShort(input.readUnsignedShort()) // access_flags
    out.writeShort(input.readUnsignedShort()) // this_class
    out.writeShort(input.readUnsignedShort()) // super_class

    val interfacesCount = input.readUnsignedShort()
    out.writeShort(interfacesCount)
    repeat(interfacesCount) { out.writeShort(input.readUnsignedShort()) }

    copyMembers(input, out, -1) // fields: never filtered
    copyMembers(input, out, methodParametersIndex) // methods
    copyAttributeList(input, out, -1) // class-level attributes

    return bytesOut.toByteArray()
}

fun copyMembers(input: DataInputStream, out: DataOutputStream, filterAttributeNameIndex: Int) {
    val count = input.readUnsignedShort()
    out.writeShort(count)
    repeat(count) {
        out.writeShort(input.readUnsignedShort()) // access_flags
        out.writeShort(input.readUnsignedShort()) // name_index
        out.writeShort(input.readUnsignedShort()) // descriptor_index
        copyAttributeList(input, out, filterAttributeNameIndex)
    }
}

fun copyAttributeList(input: DataInputStream, out: DataOutputStream, filterAttributeNameIndex: Int) {
    val count = input.readUnsignedShort()
    val kept = ArrayList<ByteArray>(count)
    repeat(count) {
        val nameIndex = input.readUnsignedShort()
        val length = input.readInt()
        val data = ByteArray(length)
        input.readFully(data)
        if (nameIndex != filterAttributeNameIndex) {
            val entry = ByteArrayOutputStream(6 + length)
            val entryOut = DataOutputStream(entry)
            entryOut.writeShort(nameIndex)
            entryOut.writeInt(length)
            entryOut.write(data)
            kept.add(entry.toByteArray())
        }
    }
    out.writeShort(kept.size)
    kept.forEach { out.write(it) }
}

fun writeSanitizedJar(source: File, destination: File) {
    destination.parentFile.mkdirs()
    ZipFile(source).use { zip ->
        ZipOutputStream(destination.outputStream()).use { zipOut ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                val outBytes = if (!entry.isDirectory && entry.name.endsWith(".class")) {
                    stripMethodParametersAttribute(bytes)
                } else {
                    bytes
                }
                zipOut.putNextEntry(ZipEntry(entry.name))
                zipOut.write(outBytes)
                zipOut.closeEntry()
            }
        }
    }
}

val dexJar by tasks.registering(Exec::class) {
    description = "Dexes the daemon and gesture-core into a jar runnable by app_process."
    group = "build"
    dependsOn(tasks.named("jar"), project(":gesture-core").tasks.named("jar"))

    val sdkDir = File(rootProject.rootDir, "tools/android-sdk")
    val d8 = File(sdkDir, "build-tools/34.0.0/d8")
    val androidJar = File(sdkDir, "platforms/android-34/android.jar")
    val outputJar = layout.buildDirectory.file("libs/gestured.jar").get().asFile
    val sanitizedDir = layout.buildDirectory.dir("tmp/dexJar").get().asFile
    val sanitizedDaemonJar = File(sanitizedDir, "daemon-nomethodparams.jar")
    val sanitizedGestureCoreJar = File(sanitizedDir, "gesture-core-nomethodparams.jar")

    doFirst {
        outputJar.parentFile.mkdirs()
        writeSanitizedJar(tasks.named<Jar>("jar").get().archiveFile.get().asFile, sanitizedDaemonJar)
        writeSanitizedJar(
            project(":gesture-core").tasks.named<Jar>("jar").get().archiveFile.get().asFile,
            sanitizedGestureCoreJar
        )
        commandLine(
            d8.absolutePath,
            "--release",
            "--min-api", "22",
            "--lib", androidJar.absolutePath,
            "--output", outputJar.absolutePath,
            sanitizedDaemonJar.absolutePath,
            sanitizedGestureCoreJar.absolutePath
        )
    }
}
