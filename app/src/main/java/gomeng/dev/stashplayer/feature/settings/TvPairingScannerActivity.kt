package gomeng.dev.stashplayer.feature.settings

import android.os.Build
import android.view.Gravity
import android.view.RoundedCorner
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlin.math.roundToInt

class TvPairingScannerActivity : CaptureActivity() {
    override fun initializeContent(): DecoratedBarcodeView {
        val scanner = super.initializeContent()
        val status = scanner.statusView ?: return scanner
        val margin = (24 * resources.displayMetrics.density).roundToInt()
        status.gravity = Gravity.CENTER
        status.updateLayoutParams<FrameLayout.LayoutParams> {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            setMargins(margin, 0, margin, margin)
        }
        ViewCompat.setOnApplyWindowInsetsListener(scanner) { _, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val cornerRadius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val platformInsets = insets.toWindowInsets()
                maxOf(
                    platformInsets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0,
                    platformInsets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0,
                )
            } else {
                0
            }
            status.updateLayoutParams<FrameLayout.LayoutParams> {
                setMargins(margin + safe.left, 0, margin + safe.right, margin + maxOf(safe.bottom, cornerRadius))
            }
            insets
        }
        ViewCompat.requestApplyInsets(scanner)
        return scanner
    }
}
