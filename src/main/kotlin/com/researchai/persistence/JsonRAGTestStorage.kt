package com.researchai.persistence

import com.researchai.domain.models.RAGTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * JSON-based implementation of RAGTestStorage.
 * Stores tests as individual JSON files in the specified directory.
 */
class JsonRAGTestStorage(
    private val storageDir: String = "data/rag/tests"
) : RAGTestStorage {

    private val mutex = Mutex()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    // Cache: test ID -> filename (without extension)
    private val idToFilename = mutableMapOf<String, String>()
    // Cache: sanitized name -> test ID
    private val nameToId = mutableMapOf<String, String>()

    init {
        File(storageDir).mkdirs()
    }

    /**
     * Sanitize test name for use as filename.
     * Removes/replaces characters that are invalid in filenames.
     */
    private fun sanitizeFilename(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")  // Replace invalid chars
            .replace(Regex("\\s+"), " ")              // Normalize whitespace
            .trim()
            .take(200)  // Limit length
            .ifEmpty { "unnamed" }
    }

    override suspend fun save(test: RAGTest) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val sanitizedName = sanitizeFilename(test.name)
                val file = File(storageDir, "$sanitizedName.json")
                val tempFile = File(storageDir, "$sanitizedName.json.tmp")

                // Check if this test already exists with a different filename
                val existingFilename = idToFilename[test.id]
                if (existingFilename != null && existingFilename != sanitizedName) {
                    // Delete old file
                    File(storageDir, "$existingFilename.json").delete()
                    // Remove old name from cache
                    nameToId.remove(existingFilename)
                }

                try {
                    val jsonString = json.encodeToString(test)
                    tempFile.writeText(jsonString)

                    Files.move(
                        tempFile.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )

                    // Update caches
                    idToFilename[test.id] = sanitizedName
                    nameToId[sanitizedName] = test.id
                } catch (e: Exception) {
                    tempFile.delete()
                    throw Exception("Failed to save RAG test ${test.id}: ${e.message}", e)
                }
            }
        }
    }

    override suspend fun load(testId: String): RAGTest? {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                // First check cache
                val filename = idToFilename[testId]
                if (filename != null) {
                    val file = File(storageDir, "$filename.json")
                    if (file.exists()) {
                        return@withContext try {
                            val jsonString = file.readText()
                            json.decodeFromString<RAGTest>(jsonString)
                        } catch (e: Exception) {
                            throw Exception("Failed to load RAG test $testId: ${e.message}", e)
                        }
                    }
                }

                // If not in cache, scan all files
                val dir = File(storageDir)
                if (!dir.exists()) return@withContext null

                dir.listFiles { file ->
                    file.isFile && file.extension == "json" && !file.name.endsWith(".tmp")
                }?.forEach { file ->
                    try {
                        val jsonString = file.readText()
                        val test = json.decodeFromString<RAGTest>(jsonString)
                        // Update cache
                        val sanitizedName = file.nameWithoutExtension
                        idToFilename[test.id] = sanitizedName
                        nameToId[sanitizedName] = test.id

                        if (test.id == testId) {
                            return@withContext test
                        }
                    } catch (e: Exception) {
                        println("Warning: Failed to load test ${file.name}: ${e.message}")
                    }
                }

                null
            }
        }
    }

    override suspend fun loadAll(): List<RAGTest> {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val dir = File(storageDir)
                if (!dir.exists()) {
                    return@withContext emptyList()
                }

                // Clear caches before reloading
                idToFilename.clear()
                nameToId.clear()

                val tests = mutableListOf<RAGTest>()
                dir.listFiles { file ->
                    file.isFile && file.extension == "json" && !file.name.endsWith(".tmp")
                }?.forEach { file ->
                    try {
                        val jsonString = file.readText()
                        val test = json.decodeFromString<RAGTest>(jsonString)
                        tests.add(test)

                        // Update caches
                        val sanitizedName = file.nameWithoutExtension
                        idToFilename[test.id] = sanitizedName
                        nameToId[sanitizedName] = test.id
                    } catch (e: Exception) {
                        println("Warning: Failed to load test ${file.name}: ${e.message}")
                    }
                }

                tests.sortedByDescending { it.createdAt }
            }
        }
    }

    override suspend fun delete(testId: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                // Try to find filename from cache
                var filename = idToFilename[testId]

                // If not in cache, scan files
                if (filename == null) {
                    val dir = File(storageDir)
                    dir.listFiles { file ->
                        file.isFile && file.extension == "json" && !file.name.endsWith(".tmp")
                    }?.forEach { file ->
                        try {
                            val jsonString = file.readText()
                            val test = json.decodeFromString<RAGTest>(jsonString)
                            if (test.id == testId) {
                                filename = file.nameWithoutExtension
                            }
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }

                if (filename != null) {
                    File(storageDir, "$filename.json").delete()
                    idToFilename.remove(testId)
                    nameToId.remove(filename)
                }
            }
        }
    }

    override suspend fun exists(testId: String): Boolean {
        return load(testId) != null
    }

    override suspend fun existsByName(name: String): Boolean {
        return findByName(name) != null
    }

    override suspend fun findByName(name: String): RAGTest? {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val sanitizedName = sanitizeFilename(name)

                // Check cache first
                val testId = nameToId[sanitizedName]
                if (testId != null) {
                    val file = File(storageDir, "$sanitizedName.json")
                    if (file.exists()) {
                        return@withContext try {
                            val jsonString = file.readText()
                            json.decodeFromString<RAGTest>(jsonString)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }

                // If not in cache, try direct file lookup
                val file = File(storageDir, "$sanitizedName.json")
                if (file.exists()) {
                    return@withContext try {
                        val jsonString = file.readText()
                        val test = json.decodeFromString<RAGTest>(jsonString)
                        // Update cache
                        idToFilename[test.id] = sanitizedName
                        nameToId[sanitizedName] = test.id
                        test
                    } catch (e: Exception) {
                        null
                    }
                }

                null
            }
        }
    }
}
