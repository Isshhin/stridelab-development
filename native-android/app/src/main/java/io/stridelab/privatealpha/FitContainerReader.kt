package io.stridelab.privatealpha

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * FIT structural validation and opaque-message decoder (Phase 38 candidate).
 *
 * Deliberately DOES NOT invent an ActivityRecord, distance, timestamps, HR,
 * or workout sets. FIT profile-specific scaling, invalid sentinels, compressed
 * timestamp reconstruction and developer field interpretation need the
 * official FIT profile/SDK adapter and independent fixtures before import.
 */
internal object FitContainerReader {
    const val MAX_BYTES = 16 * 1024 * 1024
    private const val MAX_MESSAGES = 100_000

    internal data class Field(val number: Int, val baseType: Int, val raw: ByteArray)
    internal data class Message(val globalNumber: Int, val localNumber: Int,
                                val fields: List<Field>, val compressedTimestamp: Boolean)
    internal data class Result(
        val ownerId: String, val source: String, val sha256: String,
        val protocolVersion: Int, val profileVersion: Int,
        val headerBytes: Int, val dataBytes: Int, val messages: List<Message>
    )

    private data class Definition(
        val global: Int,
        val fields: List<Pair<Int,Pair<Int,Int>>>,
        val developerByteLengths: List<Int>
    )

    fun read(input: InputStream, account: String, source: String = "fit-file"): Result {
        require(account.isNotBlank() && account.length <= 256)
        require(source.isNotBlank() && source.length <= 128)
        val output=ByteArrayOutputStream()
        val block=ByteArray(8192)
        input.use {
            while(true) {
                val read=it.read(block)
                if(read < 0) break
                if(read == 0) continue
                require(output.size()+read <= MAX_BYTES) { "FIT file exceeds size limit." }
                output.write(block,0,read)
            }
        }
        return decode(output.toByteArray(),account,source)
    }

    fun decode(bytes: ByteArray, account: String, source: String = "fit-file"): Result {
        require(account.isNotBlank() && account.length <= 256)
        require(source.isNotBlank() && source.length <= 128)
        require(bytes.size in 14..MAX_BYTES) { "Missing, tiny or oversized FIT file." }
        val headerSize=u8(bytes,0)
        require(headerSize==12 || headerSize==14) { "Unsupported FIT header length." }
        val protocol=u8(bytes,1)
        val profile=u16(bytes,2,false)
        val dataLength=u32(bytes,4)
        require(dataLength <= MAX_BYTES.toLong()-headerSize-2) { "FIT data size exceeds cap." }
        require(headerSize + dataLength + 2 == bytes.size.toLong()) {
            "FIT data boundary mismatch or chained FIT input; no partial import."
        }
        require(bytes.copyOfRange(8,12).contentEquals(byteArrayOf(
            '.'.code.toByte(),'F'.code.toByte(),'I'.code.toByte(),'T'.code.toByte()
        ))) { "Missing .FIT signature." }
        if(headerSize==14) {
            val headerCrc=u16(bytes,12,false)
            if(headerCrc!=0) require(headerCrc==crc16(bytes,0,12)) {
                "FIT header checksum mismatch."
            }
        }
        val actualFileCrc=u16(bytes,bytes.size-2,false)
        require(crc16(bytes,0,bytes.size-2)==actualFileCrc) {
            "FIT content checksum mismatch."
        }

        val end=headerSize+dataLength.toInt()
        val definitions=arrayOfNulls<Definition>(16)
        val messages=ArrayList<Message>()
        var pos=headerSize
        fun take(length:Int):ByteArray {
            require(length>=0 && pos+length<=end) { "Truncated FIT definition or data." }
            val result=bytes.copyOfRange(pos,pos+length)
            pos+=length
            return result
        }
        fun takeU8()=take(1)[0].toInt() and 255
        while(pos<end) {
            require(messages.size<MAX_MESSAGES) { "Excessive FIT data records." }
            val header=takeU8()
            val compressed=header and 0x80 != 0
            val local=if(compressed) (header shr 5) and 3 else header and 15
            if(!compressed && header and 0x40 != 0) {
                val reserved=takeU8()
                require(reserved==0) { "Invalid FIT definition reserved field." }
                val architecture=takeU8()
                require(architecture==0 || architecture==1)
                val globalBytes=take(2)
                val global=if(architecture==0)
                    ((globalBytes[1].toInt() and 255) shl 8) or (globalBytes[0].toInt() and 255)
                else ((globalBytes[0].toInt() and 255) shl 8) or (globalBytes[1].toInt() and 255)
                val count=takeU8()
                val fields=(0 until count).map {
                    val number=takeU8()
                    val length=takeU8()
                    val base=takeU8()
                    number to (length to base)
                }
                val developer=if(header and 0x20 != 0) {
                    val countDeveloper=takeU8()
                    (0 until countDeveloper).map {
                        val number=takeU8()
                        val size=takeU8()
                        val index=takeU8()
                        require(number in 0..255 && index in 0..255)
                        size
                    }
                } else emptyList()
                definitions[local]=Definition(global,fields,developer)
            } else {
                val definition=definitions[local]
                    ?: throw IllegalArgumentException("FIT data without prior local message definition.")
                // Compressed timestamp headers replace field 253's four bytes.
                val fields=definition.fields.filterNot { compressed && it.first==253 }.map { (number,typed) ->
                    Field(number,typed.second,take(typed.first))
                }
                definition.developerByteLengths.forEach { take(it) }
                messages.add(Message(definition.global,local,fields,compressed))
            }
        }
        require(pos==end)
        val digest=MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex="0123456789abcdef"
        val hash=buildString(digest.size*2) {
            for(b in digest) {
                val n=b.toInt() and 255
                append(hex[n shr 4]);append(hex[n and 15])
            }
        }
        return Result(account,source,hash,protocol,profile,headerSize,dataLength.toInt(),messages)
    }

    private fun u8(buffer:ByteArray,at:Int)=buffer[at].toInt() and 255
    private fun u16(buffer:ByteArray,at:Int,bigEndian:Boolean):Int =
        if(bigEndian) (u8(buffer,at) shl 8) or u8(buffer,at+1)
        else u8(buffer,at) or (u8(buffer,at+1) shl 8)
    private fun u32(buffer:ByteArray,at:Int):Long =
        (u8(buffer,at).toLong()) or
            (u8(buffer,at+1).toLong() shl 8) or
            (u8(buffer,at+2).toLong() shl 16) or
            (u8(buffer,at+3).toLong() shl 24)

    /** Garmin FIT nibble-table CRC is equivalent to this reflected CRC-16 implementation. */
    internal fun crc16(bytes:ByteArray,start:Int,end:Int):Int {
        var crc=0
        for(i in start until end) {
            crc=crc xor (bytes[i].toInt() and 255)
            repeat(8) { crc=if(crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
        }
        return crc and 0xffff
    }
}
