package br.com.gate8.pos.printer

import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * A impressora da Cielo lê o PNG de outro aplicativo.
 * O arquivo precisa ficar em `/storage/emulated/0/saved_images`.
 * A pasta privada do app (cache) a impressora não abre: a imagem some do papel.
 */
object PrintImageFiles {
    private const val TAG = "PrintImageFiles"
    const val DIR = "/storage/emulated/0/saved_images"

    fun writePng(fileName: String, bitmap: Bitmap): String? {
        return runCatching {
            val dir = File(DIR)
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w(TAG, "Não foi possível criar $DIR")
                return null
            }
            dir.setReadable(true, false)
            dir.setExecutable(true, false)
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    Log.w(TAG, "Falha ao gravar $fileName")
                    return null
                }
            }
            outFile.setReadable(true, false)
            if (!outFile.exists() || outFile.length() == 0L) return null
            outFile.absolutePath
        }.onFailure { Log.w(TAG, "Imagem $fileName não gravada", it) }
            .getOrNull()
    }
}
