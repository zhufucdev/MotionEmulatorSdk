import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get

fun Project.publish(config: PublishingExtension.(name: String) -> Unit) {
    configure<PublishingExtension> {
        repositories {
            maven {
                setUrl("https://oss.sonatype.org/service/local/staging/deploy/maven2")
                credentials {
                    username = findProperty("OSSRH_ID")?.toString() ?: error("OSSRH ID")
                    password = findProperty("OSSRH_PASSWORD")?.toString() ?: error("OSSRH password")
                }
            }
        }

        val publicationName = "maven"
        publications {
            create<MavenPublication>(publicationName) {
                groupId = "com.zhufucdev.me"
                artifactId = project.name
                version = project.version as String
                afterEvaluate { from(components["release"]) }

                pom {
                    name = "MotionEmulatorStub"
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
