package com.researchai.persistence

import com.researchai.domain.models.RAGDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class JsonRAGDocumentStorage(
    private val storageDir: String = "data/rag/documents"
) : RAGDocumentStorage {

    private val mutex = Mutex()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    // Cache: document ID -> filename (without extension)
    private val idToFilename = mutableMapOf<String, String>()
    // Cache: sanitized name -> document ID
    private val nameToId = mutableMapOf<String, String>()

    init {
        File(storageDir).mkdirs()
    }

    /**
     * Sanitize document name for use as filename
     * Removes/replaces characters that are invalid in filenames
     */
    private fun sanitizeFilename(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")  // Replace invalid chars
            .replace(Regex("\\s+"), " ")              // Normalize whitespace
            .trim()
            .take(200)  // Limit length
            .ifEmpty { "unnamed" }
    }

    override suspend fun save(document: RAGDocument) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val sanitizedName = sanitizeFilename(document.name)
                val file = File(storageDir, "$sanitizedName.json")
                val tempFile = File(storageDir, "$sanitizedName.json.tmp")

                // Check if this document already exists with a different filename
                val existingFilename = idToFilename[document.id]
                if (existingFilename != null && existingFilename != sanitizedName) {
                    // Delete old file
                    File(storageDir, "$existingFilename.json").delete()
                    // Remove old name from cache
                    nameToId.remove(existingFilename)
                }

                try {
                    val jsonString = json.encodeToString(document)
                    tempFile.writeText(jsonString)

                    Files.move(
                        tempFile.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )

                    // Update caches
                    idToFilename[document.id] = sanitizedName
                    nameToId[sanitizedName] = document.id
                } catch (e: Exception) {
                    tempFile.delete()
                    throw Exception("Failed to save RAG document ${document.id}: ${e.message}", e)
                }
            }
        }
    }

    override suspend fun load(documentId: String): RAGDocument? {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                // First check cache
                val filename = idToFilename[documentId]
                if (filename != null) {
                    val file = File(storageDir, "$filename.json")
                    if (file.exists()) {
                        return@withContext try {
                            val jsonString = file.readText()
                            json.decodeFromString<RAGDocument>(jsonString)
                        } catch (e: Exception) {
                            throw Exception("Failed to load RAG document $documentId: ${e.message}", e)
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
                        val document = json.decodeFromString<RAGDocument>(jsonString)
                        // Update cache
                        val sanitizedName = file.nameWithoutExtension
                        idToFilename[document.id] = sanitizedName
                        nameToId[sanitizedName] = document.id

                        if (document.id == documentId) {
                            return@withContext document
                        }
                    } catch (e: Exception) {
                        println("Warning: Failed to load document ${file.name}: ${e.message}")
                    }
                }

                null
            }
        }
    }

    override suspend fun loadAll(): List<RAGDocument> {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val dir = File(storageDir)
                if (!dir.exists()) {
                    return@withContext emptyList()
                }

                // Clear caches before reloading
                idToFilename.clear()
                nameToId.clear()

                val documents = mutableListOf<RAGDocument>()
                dir.listFiles { file ->
                    file.isFile && file.extension == "json" && !file.name.endsWith(".tmp")
                }?.forEach { file ->
                    try {
                        val jsonString = file.readText()
                        val document = json.decodeFromString<RAGDocument>(jsonString)
                        documents.add(document)

                        // Update caches
                        val sanitizedName = file.nameWithoutExtension
                        idToFilename[document.id] = sanitizedName
                        nameToId[sanitizedName] = document.id
                    } catch (e: Exception) {
                        println("Warning: Failed to load document ${file.name}: ${e.message}")
                    }
                }

                documents
            }
        }
    }

    override suspend fun delete(documentId: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                // Try to find filename from cache
                var filename = idToFilename[documentId]

                // If not in cache, scan files
                if (filename == null) {
                    val dir = File(storageDir)
                    dir.listFiles { file ->
                        file.isFile && file.extension == "json" && !file.name.endsWith(".tmp")
                    }?.forEach { file ->
                        try {
                            val jsonString = file.readText()
                            val document = json.decodeFromString<RAGDocument>(jsonString)
                            if (document.id == documentId) {
                                filename = file.nameWithoutExtension
                            }
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }

                if (filename != null) {
                    File(storageDir, "$filename.json").delete()
                    idToFilename.remove(documentId)
                    nameToId.remove(filename)
                }
            }
        }
    }

    override suspend fun exists(documentId: String): Boolean {
        return load(documentId) != null
    }

    override suspend fun existsByName(name: String): Boolean {
        return findByName(name) != null
    }

    override suspend fun findByName(name: String): RAGDocument? {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val sanitizedName = sanitizeFilename(name)

                // Check cache first
                val documentId = nameToId[sanitizedName]
                if (documentId != null) {
                    val file = File(storageDir, "$sanitizedName.json")
                    if (file.exists()) {
                        return@withContext try {
                            val jsonString = file.readText()
                            json.decodeFromString<RAGDocument>(jsonString)
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
                        val document = json.decodeFromString<RAGDocument>(jsonString)
                        // Update cache
                        idToFilename[document.id] = sanitizedName
                        nameToId[sanitizedName] = document.id
                        document
                    } catch (e: Exception) {
                        null
                    }
                }

                null
            }
        }
    }
}
