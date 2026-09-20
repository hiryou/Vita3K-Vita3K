package org.vita3k.emulator.esde

import java.io.File
import java.util.zip.ZipFile

/**
 * Resolves an ES-DE `.zip` ROM entry to a single installed Vita title ID.
 *
 * 1. Fast path: a unique title ID token in the filename (e.g. `Limbo (PCSE00268) (NTSC).zip`).
 * 2. Zip path: a unique `app/<titleID>/` directory inside the zip, when the file is readable.
 * 3. Installed-app path: unique fuzzy match of the zip name against Vita3K's installed titles.
 */
object VitaEsdeZipResolver {
    private val TITLE_ID_REGEX = Regex("""[A-Z]{4}[0-9]{5}""")
    private val APP_DIR_REGEX = Regex("""(?:^|/)app/([A-Z]{4}[0-9]{5})(?:/|$)""")
    private val REGION_TAG_REGEX =
        Regex("""\s*\((NTSC|PAL|USA|EUR|JPN|JAPAN)[^)]*\)""", RegexOption.IGNORE_CASE)

    data class Result(
        val titleId: String,
        val gameTitle: String,
        val source: String
    )

    data class InstalledApp(
        val titleId: String,
        val title: String
    )

    fun resolve(path: String, installedApps: () -> List<InstalledApp> = { emptyList() }): Result? {
        val file = File(path)
        val name = file.name.ifBlank { return null }
        if (!name.endsWith(".zip", ignoreCase = true)) {
            return null
        }

        titleIdsIn(name).singleOrNull()?.let { titleId ->
            return Result(titleId, displayTitle(name, titleId), "filename")
        }

        if (file.canRead()) {
            runCatching { titleIdsFromZip(file) }.getOrDefault(emptySet()).singleOrNull()?.let { titleId ->
                return Result(titleId, displayTitle(name, titleId), "zip")
            }
        }

        val fromInstalled = matchInstalledApp(name, installedApps()) ?: return null
        return Result(fromInstalled.titleId, fromInstalled.title, "installed")
    }

    internal fun titleIdsIn(text: String): Set<String> {
        return TITLE_ID_REGEX.findAll(text).map { it.value }.toSet()
    }

    internal fun titleIdsFromZip(file: File): Set<String> {
        ZipFile(file).use { zip ->
            val ids = linkedSetOf<String>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entryName = entries.nextElement().name.replace('\\', '/')
                APP_DIR_REGEX.find(entryName)?.groupValues?.get(1)?.let(ids::add)
            }
            return ids
        }
    }

    internal fun matchInstalledApp(fileName: String, apps: List<InstalledApp>): InstalledApp? {
        val needle = normalize(displayTitle(fileName, titleIdsIn(fileName).singleOrNull().orEmpty()))
        if (needle.length < 4) {
            return null
        }
        return apps.filter { app ->
            val title = normalize(app.title)
            title.isNotEmpty() && (needle.contains(title) || title.contains(needle))
        }.singleOrNull()
    }

    internal fun displayTitle(fileName: String, titleId: String): String {
        val withoutExt = fileName.replace(Regex("""\.[Zz][Ii][Pp]$"""), "")
        val stripped = withoutExt
            .replace(titleId.takeIf { it.isNotEmpty() }?.let { Regex("""\s*\($it\)""") } ?: Regex("$^"), "")
            .replace(REGION_TAG_REGEX, "")
            .trim()
        return stripped.ifBlank { titleId.ifBlank { withoutExt } }
    }

    private fun normalize(value: String): String {
        return value.lowercase()
            .replace("®", "")
            .replace("™", "")
            .replace(Regex("""\([^)]*\)"""), "")
            .replace(Regex("[^a-z0-9]+"), "")
    }
}
