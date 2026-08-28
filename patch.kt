import java.io.File
fun main() {
    val file = File("app/build.gradle.kts")
    var content = file.readText()
    
    val target = "versionName = \"1.0.$versionCode\"\n        buildConfigField(\"String\", \"GITHUB_REPO\", \"\\\"pjaraf/JetGo\\\"\")"
    val replacement = """versionName = "1.0.${'$'}versionCode"
        buildConfigField("String", "GITHUB_REPO", "\"pjaraf/JetGo\"")
        
        // Limita las librerías nativas de VLC solo a ARM (Teléfonos y TVs comunes). 
        // Esto reduce el tamaño del APK de ~100MB a ~30MB al descartar binarios x86/x86_64.
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
        }"""
        
    if (content.contains("buildConfigField(\"String\", \"GITHUB_REPO\"")) {
        content = content.replace(target, replacement)
        file.writeText(content)
        println("Patched successfully")
    } else {
        println("Target not found")
    }
}
