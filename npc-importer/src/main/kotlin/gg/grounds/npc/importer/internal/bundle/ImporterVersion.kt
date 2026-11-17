package gg.grounds.npc.importer.internal.bundle

import java.util.Properties

internal object ImporterVersion {
    val value: String =
        Properties()
            .also { properties ->
                val stream =
                    ImporterVersion::class
                        .java
                        .getResourceAsStream("/gg/grounds/npc/importer/version.properties")
                        ?: error("Importer version resource is missing")
                stream.use { properties.load(it) }
            }
            .getProperty("version") ?: error("Importer artifact has no version")
}
