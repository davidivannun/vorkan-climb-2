import java.io.File
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.vorkan.climb2"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.vorkan.climb2"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "2.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }
    signingConfigs {
        create("distribution") {
            val keystorePath = System.getenv("ESCALADA_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ESCALADA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ESCALADA_KEY_ALIAS")
                keyPassword = System.getenv("ESCALADA_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("distribution")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
val downloadSegmentationModel by tasks.registering {
    val model = layout.projectDirectory.file("src/main/assets/interactive_segmentation.task")
    outputs.file(model)
    doLast {
        val target = model.asFile
        target.parentFile.mkdirs()
        if (!target.exists() || target.length() < 100000L) {
            val tmp = File(target.parentFile, target.name + ".tmp")
            URI("https://storage.googleapis.com/mediapipe-models/interactive_segmenter_v2/magic_touch/int8/1/interactive_segmentation.task").toURL()
                .openConnection().apply { connectTimeout = 30000; readTimeout = 120000 }
                .getInputStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            check(tmp.length() > 100000L)
            check(tmp.renameTo(target))
        }
    }
}
val downloadHoldDetectorModel by tasks.registering {
    val model = layout.projectDirectory.file("src/main/assets/hold-detector.onnx")
    outputs.file(model)
    doLast {
        val target = model.asFile
        target.parentFile.mkdirs()
        if (!target.exists() || target.length() < 1_000_000L) {
            val tmp = File(target.parentFile, target.name + ".tmp")
            try {
                URI("https://huggingface.co/jwlarocque/yolov8n-freeclimbs-detect-2/resolve/main/yolov8n-freeclimbs-detect-2-fp32.onnx")
                    .toURL().openConnection().apply {
                        connectTimeout = 30000
                        readTimeout = 120000
                    }.getInputStream().use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    }
                check(tmp.length() in 1_000_000L..100_000_000L) { "Modelo ONNX inválido" }
                check(tmp.renameTo(target)) { "No se pudo guardar el modelo ONNX" }
            } finally {
                tmp.delete()
            }
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(downloadHoldDetectorModel) }
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(downloadSegmentationModel) }
dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.mediapipe:tasks-vision:1.0.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.21.0")
}
