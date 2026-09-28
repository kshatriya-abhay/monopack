package dev.abhay.monopack.library

import java.io.File

class FakeLibraryStore(var tree: String? = null) : LibraryStore {
    var records: Map<String, ExportRecord> = emptyMap()

    override suspend fun loadTree() = tree

    override suspend fun saveTree(treeUri: String?) {
        tree = treeUri
    }

    override suspend fun loadRecords() = records

    override suspend fun saveRecord(record: ExportRecord) {
        records = records + (record.fileName to record)
    }

    override suspend fun removeRecords(fileNames: Collection<String>) {
        records = records - fileNames.toSet()
    }

    var hintDismissed = false

    override suspend fun loadApplyHintDismissed() = hintDismissed

    override suspend fun saveApplyHintDismissed() {
        hintDismissed = true
    }

    var iconShape: String? = null

    override suspend fun loadIconShape() = iconShape

    override suspend fun saveIconShape(shape: String) {
        iconShape = shape
    }
}

class FakeLibraryFolder : LibraryFolder {
    val granted = mutableSetOf<String>()
    val files = mutableListOf<FolderFile>()
    val deleted = mutableListOf<String>()
    var opened = 0

    fun add(name: String) = FolderFile(name, "content://tree/doc/$name", "primary:Download/Monopack/$name", 1, 1).also { files += it }

    override fun hasAccess(treeUri: String) = treeUri in granted

    override fun takeAccess(treeUri: String) {
        granted += treeUri
    }

    override fun releaseAccess(treeUri: String) {
        granted -= treeUri
    }

    override fun label(treeUri: String) = "Download/Monopack"

    override suspend fun list(treeUri: String) = files.toList()

    override suspend fun write(treeUri: String, file: File, name: String, mimeType: String) = add(name)

    override suspend fun delete(documentUri: String): Boolean {
        deleted += documentUri
        return files.removeIf { it.documentUri == documentUri }
    }

    override fun open(treeUri: String): Boolean {
        opened++
        return true
    }
}
