package com.mymedia

import android.util.Log
import androidx.annotation.NonNull
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.annotations.NotNull
object FileUtils {
    private const val TAG = "David"
    fun writeBytes(array: ByteArray?, path: String?) {
        var writer: FileOutputStream? = null
        try {
            // 打开一个写文件器，构造函数中的第二个参数true表示以追加形式写文件
            writer = FileOutputStream(path, true)
            writer.write(array)
            writer.write('\n'.code)
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                writer?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun writeContent(array: ByteArray,  @NonNull file: File): String {
        file.takeIf {
            it.exists()
        } ?: file.createNewFile()
        val HEX_CHAR_TABLE = charArrayOf(
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
        )
        val sb = StringBuilder()
        for (b in array) {
            sb.append(HEX_CHAR_TABLE[b.toInt() and 0xf0 shr 4])
            sb.append(HEX_CHAR_TABLE[b.toInt() and 0x0f])
        }
        Log.i(TAG, "writeContent: $sb")
        return sb.toString().also {
            file.appendText(it)
        }
    }
}