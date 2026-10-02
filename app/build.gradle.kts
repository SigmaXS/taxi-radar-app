import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Ключ подписи лежит вне проекта (TaxiRadar-keys): пароли не попадают в код.
val keystoreProps = Properties().apply {
    val f = rootProject.file("../TaxiRadar-keys/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}


// Временная копия исходников в TaxiRadar/_src (чтобы ассистент мог их прочитать). Можно удалить.
run {
    val src = file("src/main/java/com/example/taxiradar")
    val dst = rootProject.file("_src")
    dst.mkdirs()
    src.listFiles()?.filter { it.isFile }?.forEach { it.copyTo(dst.resolve(it.name), true) }
}

android {
    namespace = "com.example.taxiradar"
    compileSdk = 34

    defaultConfig {
        applicationId = "md.taxiradar.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 17
        versionName = "1.16"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Все сборки (и «Run», и «Generate APKs») подписываем ключом Taxi Radar,
        // чтобы новая версия всегда ставилась поверх старой без удаления.
        debug {
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

    // Сеть и корутины
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Геолокация FusedLocation (для перехвата Fake GPS)
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Карта спроса (OpenStreetMap, без ключей)
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Зависимости для тестов (убирают ошибки Unresolved reference)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}