package com.opus.pdfreader

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private var renderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private lateinit var list: RecyclerView
    private lateinit var hint: TextView
    private var zoom = 1f
    private val lm by lazy { LinearLayoutManager(this) }

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            openPdf(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = android.widget.FrameLayout(this)
        list = RecyclerView(this).apply {
            layoutManager = lm
            setBackgroundColor(Color.DKGRAY)
        }
        hint = TextView(this).apply {
            text = "Нажмите «Открыть», чтобы выбрать PDF"
            textSize = 18f
            gravity = Gravity.CENTER
        }
        root.addView(list, ViewGroup.LayoutParams(-1, -1))
        root.addView(hint, ViewGroup.LayoutParams(-1, -1))
        setContentView(root)

        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) = updateTitle()
        })

        intent?.data?.let { openPdf(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { openPdf(it) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Открыть").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(0, 2, 1, "＋").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(0, 3, 2, "－").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> picker.launch(arrayOf("application/pdf"))
            2 -> setZoom(zoom * 1.25f)
            3 -> setZoom(zoom / 1.25f)
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setZoom(z: Float) {
        zoom = z.coerceIn(0.5f, 4f)
        val pos = lm.findFirstVisibleItemPosition()
        list.adapter?.notifyDataSetChanged()
        if (pos >= 0) lm.scrollToPosition(pos)
    }

    private fun openPdf(uri: Uri) {
        try {
            closePdf()
            pfd = contentResolver.openFileDescriptor(uri, "r")
            renderer = PdfRenderer(pfd!!)
            hint.visibility = android.view.View.GONE
            list.adapter = PageAdapter()
            updateTitle()
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateTitle() {
        val r = renderer ?: return
        val p = lm.findFirstVisibleItemPosition().coerceAtLeast(0) + 1
        title = "Стр. $p / ${r.pageCount}"
    }

    private fun closePdf() {
        renderer?.close(); renderer = null
        pfd?.close(); pfd = null
    }

    override fun onDestroy() {
        closePdf()
        super.onDestroy()
    }

    private inner class PageAdapter : RecyclerView.Adapter<PageAdapter.VH>() {
        inner class VH(val img: ImageView) : RecyclerView.ViewHolder(img)

        override fun getItemCount() = renderer?.pageCount ?: 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val iv = ImageView(parent.context).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            return VH(iv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = renderer ?: return
            val width = (list.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels)
            val targetW = (width * zoom).toInt()
            val bmp: Bitmap
            synchronized(r) {
                val page = r.openPage(position)
                val targetH = (targetW.toFloat() * page.height / page.width).toInt()
                bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
            }
            holder.img.layoutParams = RecyclerView.LayoutParams(targetW, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 8, 0, 8)
            }
            holder.img.setImageBitmap(bmp)
        }
    }
}
