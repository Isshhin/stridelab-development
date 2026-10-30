package io.stridelab.privatealpha

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FitFileIntakeTest {
    private fun envelope():ByteArray {
        val body=byteArrayOf(
            0x40,0,0,0,0,1,0,1,0, // definition for file_id/type field
            0,4 // activity FIT file id message
        )
        val header=ByteArray(14)
        header[0]=14
        header[1]=0x20
        header[4]=body.size.toByte()
        ".FIT".toByteArray(Charsets.US_ASCII).copyInto(header,8)
        val headerCrc=FitContainerReader.crc16(header,0,12)
        header[12]=headerCrc.toByte()
        header[13]=(headerCrc ushr 8).toByte()
        val full=header+body+ByteArray(2)
        val crc=FitContainerReader.crc16(full,0,full.size-2)
        full[full.lastIndex-1]=crc.toByte()
        full[full.lastIndex]=(crc ushr 8).toByte()
        return full
    }

    @Test fun selectedLocalDocumentIsInspectedButNoWorkoutCreated() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"synthetic-fit-"+System.nanoTime()+".fit")
        try {
            file.writeBytes(envelope())
            val result=FitFileIntake.inspect(context,Uri.fromFile(file),"fictional-athlete")
            assertEquals("fictional-athlete",result.ownerId)
            assertEquals("fit-file",result.source)
            assertEquals(1,result.messages.size)
            assertEquals(64,result.sha256.length)
        } finally { file.delete() }
    }

    @Test fun shareIntentRequiresASingleSafeDocumentUri() {
        val accepted=Uri.parse("content://test.fit.provider/activity/1")
        val intent=Intent(Intent.ACTION_SEND).apply {
            type="application/octet-stream"
            putExtra(Intent.EXTRA_STREAM,accepted)
        }
        assertEquals(accepted,FitFileIntake.sharedSingleFile(intent))
        assertNull(FitFileIntake.sharedSingleFile(Intent(Intent.ACTION_VIEW).apply {
            putExtra(Intent.EXTRA_STREAM,accepted)
        }))
        assertNull(FitFileIntake.sharedSingleFile(Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM,Uri.parse("https://example.com/file.fit"))
        }))
    }
}
