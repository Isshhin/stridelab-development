package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class GarminFitArchiveTest {
    private fun archive(vararg entries:Pair<String,ByteArray>):ByteArray {
        val output=ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name,content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
    @Test fun extractsOneFitWithoutModifyingOriginalArchive() {
        val fit=byteArrayOf(12,1,2,3,4)
        val zipped=archive("metadata.txt" to "ignored".toByteArray(),
            "activity/record.fit" to fit)
        assertArrayEquals(fit,GarminFitArchive.extractSingleFit(zipped))
        assertTrue(zipped.size>fit.size)
    }
    @Test fun rejectsUnsafeNamesOrMoreThanOneActivity() {
        for(bad in listOf("../activity.fit","/absolute/activity.fit","a/../../activity.fit")) {
            try { GarminFitArchive.extractSingleFit(archive(bad to byteArrayOf(1))) ;fail("Path accepted") }
            catch(_:GarminProtocolException) {}
        }
        try { GarminFitArchive.extractSingleFit(
            archive("a.fit" to byteArrayOf(1),"b.fit" to byteArrayOf(2))) ;fail("Ambiguous") }
        catch(_:GarminProtocolException) {}
    }
    @Test fun rejectsArchivesWithoutRealFitData() {
        try { GarminFitArchive.extractSingleFit(archive("meta.txt" to byteArrayOf(1))) ;fail("Missing") }
        catch(_:GarminProtocolException) {}
    }
}
