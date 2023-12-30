import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get

fun Project.publish(config: PublishingExtension.(name: String) -> Unit) {
    configure<PublishingExtension> {
        val publicationName = "maven"
        publications {
            create<MavenPublication>(publicationName) {
                afterEvaluate { from(components["release"]) }

                pom {
                    name = "${project.group}:${project.name}"
                    description = "SDK for building Motion Emulator plug-ins"
                    url = "https://github.com/zhufucdev/MotionEmulatorSdk"
                    licenses {
                        license {
                            name = "Apache 2.0"
                            url = "https://www.apache.org/licenses/LICENSE-2.0"
                        }
                    }
                    developers {
                        developer {
                            id = "zhufucdev"
                            name = "Steve Reed"
                            email = "29772292+zhufucdev@users.noreply.github.com"
                            organization = "zhufucdev"
                            organizationUrl = "https://zhufucdev.com"
                        }
                    }
                    scm {
                        connection = "scm:git@github.com:zhufucdev/MotionEmulatorSdk.git"
                        developerConnection = "scm:git@github.com:zhufucdev/MotionEmulatorSdk.git"
                        url = "https://github.com/zhufucdev/MotionEmulatorSdk"
                    }
                }
            }
        }

        config(publicationName)
    }
}
