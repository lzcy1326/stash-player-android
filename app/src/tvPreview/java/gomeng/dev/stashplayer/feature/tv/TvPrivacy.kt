package gomeng.dev.stashplayer.feature.tv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.R
import kotlinx.coroutines.delay
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

internal class TvPinStore(context: Context) {
    private val prefs = context.getSharedPreferences("tv_privacy", Context.MODE_PRIVATE)
    val enabled: Boolean get() = prefs.contains("verifier")
    val lockedUntil: Long get() = prefs.getLong("locked_until", 0L)

    fun save(pin: String) {
        val encoded = Base64.encodeToString(verifier(pin, createKey = true), Base64.NO_WRAP)
        check(prefs.edit().putString("verifier", encoded).remove("failures").remove("locked_until").commit())
    }

    fun disable() {
        check(prefs.edit().clear().commit())
    }

    fun verify(pin: String): Boolean {
        val now = System.currentTimeMillis()
        if (now < lockedUntil) return false
        val saved = checkNotNull(prefs.getString("verifier", null))
        val valid = MessageDigest.isEqual(Base64.decode(saved, Base64.NO_WRAP), verifier(pin))
        val failures = if (now >= lockedUntil && lockedUntil != 0L) 0 else prefs.getInt("failures", 0)
        val update = prefs.edit()
        when {
            valid -> update.remove("failures").remove("locked_until")
            failures >= 4 -> update.putInt("failures", 0).putLong("locked_until", now + 30_000L)
            else -> update.putInt("failures", failures + 1).remove("locked_until")
        }
        check(update.commit())
        return valid
    }

    private fun verifier(pin: String, createKey: Boolean = false): ByteArray {
        val alias = "stash_tv_pin"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = (keyStore.getKey(alias, null) as? SecretKey) ?: run {
            check(createKey)
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN).build())
            }.generateKey()
        }
        return Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(pin.toByteArray(Charsets.US_ASCII))
    }
}

internal enum class TvPrivacyPage { Home, Settings, Privacy, Server, Unlock, Change, Disable, Create, Confirm }

@Composable
internal fun TvPinScreen(
    title: String,
    exitHint: String,
    error: String?,
    lockedUntil: Long,
    onSubmit: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var remaining by remember { mutableIntStateOf(0) }
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(lockedUntil) {
        do {
            remaining = ((lockedUntil - System.currentTimeMillis() + 999L) / 1000L).coerceAtLeast(0L).toInt()
            if (remaining > 0) delay(250)
        } while (remaining > 0)
    }
    LaunchedEffect(Unit) { initialFocus.requestFocus() }

    fun enter(digit: String) {
        if (System.currentTimeMillis() < lockedUntil) return
        input += digit
        if (input.length == 4) {
            val pin = input
            input = ""
            onSubmit(pin)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 820.dp).padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(44.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.widthIn(max = 290.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Stash Player TV", style = MaterialTheme.typography.titleMedium)
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text((0..3).joinToString("  ") { if (it < input.length) "●" else "○" },
                style = MaterialTheme.typography.headlineMedium)
            val message = if (remaining > 0) stringResource(R.string.tv_pin_wait, remaining) else error
            if (message != null) Text(message, color = MaterialTheme.colorScheme.error)
            Text(exitHint, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫"))) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (digit in row) {
                        if (digit.isEmpty()) {
                            Spacer(Modifier.width(68.dp))
                        } else {
                            TvPinKey(
                                digit,
                                if (digit == "5") Modifier.focusRequester(initialFocus) else Modifier,
                            ) {
                                if (digit == "⌫") input = input.dropLast(1) else enter(digit)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvPinKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Button(
        modifier = modifier.size(68.dp, 52.dp).onFocusChanged { focused = it.isFocused }
            .border(2.dp, if (focused) Color.White else Color.Transparent, shape),
        shape = shape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) colors.primary else colors.surface,
            contentColor = if (focused) colors.onPrimary else colors.onSurface,
        ),
        onClick = onClick,
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
internal fun TvPrivacySettings(enabled: Boolean, onToggle: () -> Unit, onChange: () -> Unit) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val label = stringResource(if (enabled) R.string.tv_privacy_on else R.string.tv_privacy_off)
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.tv_privacy), style = MaterialTheme.typography.headlineMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.focusRequester(focus).onFocusChanged { focused = it.isFocused }
                    .border(2.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(26.dp))
                    .semantics { contentDescription = label },
            )
        }
        if (enabled) TvButton(stringResource(R.string.tv_pin_change), onClick = onChange)
    }
}
