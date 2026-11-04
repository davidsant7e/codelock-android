// AGP 9 trae Kotlin integrado: no se aplica el plugin org.jetbrains.kotlin.android.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
