package io.stridelab.privatealpha

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** Bounded, in-memory extraction of a user's own Garmin FIT ZIP download. */
internal object GarminFitArchive {
    private const val MAX_ENTRIES=32
    private const val MAX_EXPANDED_BYTES=20*1024*1024

    fun extractSingleFit(archive:ByteArray):ByteArray {
        require(archive.size in 4..GarminUrlConnectionTransport.MAX_ZIP_BYTES) {
            "Garmin ZIP size is unsupported"
        }
        var fit:ByteArray?=null
        var expanded=0
        var entries=0
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while(true) {
                val entry=zip.nextEntry ?: break
                if(++entries>MAX_ENTRIES)throw GarminProtocolException("Garmin ZIP has too many entries")
                val name=entry.name.replace('\\','/')
                if(name.startsWith("/") || name.split('/').any { it==".." || it=="." })
                    throw GarminProtocolException("Garmin ZIP contains an unsafe file path")
                val isFit=!entry.isDirectory && name.endsWith(".fit",ignoreCase=true)
                if(isFit && fit!=null)throw GarminProtocolException(
                    "Garmin ZIP contains more than one FIT activity")
                val output=if(isFit)ByteArrayOutputStream() else null
                val chunk=ByteArray(8192)
                while(true) {
                    val n=zip.read(chunk)
                    if(n<0)break
                    expanded+=n
                    if(expanded>MAX_EXPANDED_BYTES)
                        throw GarminProtocolException("Garmin ZIP expanded content exceeds the size limit")
                    if(isFit && (output!!.size()+n>FitContainerReader.MAX_BYTES))
                        throw GarminProtocolException("Garmin FIT file exceeds the size limit")
                    output?.write(chunk,0,n)
                }
                if(isFit)fit=output!!.toByteArray()
                zip.closeEntry()
            }
        }
        return fit?.takeIf { it.isNotEmpty() }
            ?: throw GarminProtocolException("Garmin ZIP contains no recorded FIT activity")
    }
}
