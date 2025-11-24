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

    init {
        File(storageDir).mkdirs()
    }

    override suspend fun save(document: RAGDocument) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val file = File(storageDir, "${document.id}.json")
                val tempFile = File(storageDir, "${document.id}.json.tmp")

                try {
                    val jsonString = json.encodeToString(document)
                    tempFile.writeText(jsonString)

                    Files.move(
                        tempFile.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
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
                val file = File(storageDir, "$documentId.json")
                if (!file.exists()) {
                    return@withContext null
                }

                try {
                    val jsonString = file.readText()
                    json.decodeFromString<RAGDocument>(jsonString)
                } catch (e: Exception) {
                    throw Exception("Failed to load RAG document $documentId: ${e.message}", e)
                }
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

                val documents = mutableListOf<RAGDocument>()
                dir.listFiles { file ->
                    file.isFile && file.extension == "json" && !file.name.endsWith(".tmp")
                }?.forEach { file ->
                    try {
                        val jsonString = file.readText()
                        val document = json.decodeFromString<RAGDocument>(jsonString)
                        documents.add(document)
                    } catch (e: Exception) {
                        // Log error but continue loading other documents
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
                val file = File(storageDir, "$documentId.json")
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    override suspend fun exists(documentId: String): Boolean {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                File(storageDir, "$documentId.json").exists()
            }
        }
    }
}
