package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

/** Synthetic, valid single-file FIT envelopes; no invented athlete measurements. */
class FitContainerReaderTest {
    private fun envelope(data:ByteArray,includeHeaderCrc:Boolean=true):ByteArray {
        val header=ByteArray(14)
        header[0]=14
        header[1]=0x20
        val len=data.size
        for(i in 0..3) header[4+i]=(len ushr (8*i)).toByte()
        ".FIT".toByteArray(Charsets.US_ASCII).copyInto(header,8)
        if(includeHeaderCrc) {
            val crc=FitContainerReader.crc16(header,0,12)
            header[12]=(crc and 255).toByte()
            header[13]=(crc ushr 8).toByte()
        }
        val result=header+data+ByteArray(2)
        val crc=FitContainerReader.crc16(result,0,result.size-2)
        result[result.lastIndex-1]=(crc and 255).toByte()
        result[result.lastIndex]=(crc ushr 8).toByte()
        return result
    }

    private val fileIdDef=byteArrayOf(
        0x40, 0x00, 0x00, 0x00, 0x00,
        0x01, 0x00, 0x01, 0x00
    )
    private val fileIdActivity=byteArrayOf(0x00,0x04)

    @Test fun acceptsWellFormedFileAndRetainsOpaqueFieldsAndHash() {
        val raw=envelope(fileIdDef+fileIdActivity)
        val result=FitContainerReader.read(ByteArrayInputStream(raw),"account-a")
        assertEquals(14,result.headerBytes)
        assertEquals(11,result.dataBytes)
        assertEquals(1,result.messages.size)
        assertEquals(0,result.messages.single().globalNumber)
        assertEquals(0,result.messages.single().fields.single().number)
        assertTrue(result.messages.single().fields.single().raw.contentEquals(byteArrayOf(4)))
        assertEquals(64,result.sha256.length)
        assertEquals("account-a",result.ownerId)
    }

    @Test fun rejectsBadChecksumWithoutCreatingAnyWorkout() {
        val raw=envelope(fileIdDef+fileIdActivity)
        raw[raw.lastIndex]=(raw.last() xor 1)
        assertThrows(IllegalArgumentException::class.java) {
            FitContainerReader.decode(raw,"account-a")
        }
    }

    @Test fun rejectsMisleadingFileLengthOrChainedContent() {
        val file=envelope(fileIdDef+fileIdActivity)
        file[4]=9
        assertThrows(IllegalArgumentException::class.java) {
            FitContainerReader.decode(file,"account-a")
        }
    }

    @Test fun dataRecordWithoutPriorDefinitionIsInvalid() {
        assertThrows(IllegalArgumentException::class.java) {
            FitContainerReader.decode(envelope(fileIdActivity),"account-a")
        }
    }

    @Test fun optionalZeroHeaderChecksumIsSupported() {
        val v=FitContainerReader.decode(envelope(fileIdDef+fileIdActivity,false),"account-a")
        assertEquals(1,v.messages.size)
    }

    @Test fun truncatedDefinitionOrWrongSourceIdentityFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            FitContainerReader.decode(envelope(fileIdDef.copyOf(5)),"account-a")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FitContainerReader.decode(envelope(fileIdDef+fileIdActivity),"")
        }
    }
}
