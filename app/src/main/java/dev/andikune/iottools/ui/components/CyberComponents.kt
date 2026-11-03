package dev.andikune.iottools.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.iottools.ui.theme.*

/**
 * Cyberpunk neon border card container.
 */
@Composable
fun CyberCard(
    modifier: Modifier = Modifier,
    borderColor: Color = NeonRed,
    glow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "borderGlow")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val currentBorder = if (glow) borderColor.copy(alpha = alpha) else borderColor.copy(alpha = 0.4f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = currentBorder,
                shape = RoundedCornerShape(10.dp)
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = CyberCard
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

/**
 * Cyber Terminal Header with status LED indicator.
 */
@Composable
fun CyberTerminalHeader(
    title: String,
    subtitle: String? = null,
    statusText: String = "ONLINE",
    statusColor: Color = MatrixGreen
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val ledAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ledAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberBlack)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "> ",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonRed,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TerminalText,
                    fontWeight = FontWeight.Bold
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TerminalDim
                )
            }
        }

        // Cyber LED Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor.copy(alpha = ledAlpha))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Cyber Neon Action Button with glowing background.
 */
@Composable
fun CyberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = NeonRed,
    textColor: Color = CyberBlack,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = textColor,
            disabledContainerColor = color.copy(alpha = 0.2f),
            disabledContentColor = TerminalDim
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.8f)),
        modifier = modifier
            .height(48.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Cyber Secondary Outline Button.
 */
@Composable
fun CyberSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = NeonCyan
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = color
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
        modifier = modifier
            .height(44.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Cyber Styled Text Field.
 */
@Composable
fun CyberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodyMedium, color = TerminalDim) },
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = TerminalDim.copy(alpha = 0.5f)) },
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(8.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = TerminalText),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonRed,
            unfocusedBorderColor = CyberCardBorder,
            focusedContainerColor = CyberDark,
            unfocusedContainerColor = CyberDark,
            cursorColor = NeonRed
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Cyber Toggle Row for permission switches.
 */
@Composable
fun CyberToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color = NeonRed
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (checked) accentColor else TerminalDim)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (checked) TerminalText else TerminalDim,
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TerminalDim,
                fontSize = 11.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyberBlack,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = TerminalDim,
                uncheckedTrackColor = CyberDark,
                uncheckedBorderColor = CyberCardBorder
            )
        )
    }
}

/**
 * Cyber Monospace Code Box with copy button.
 */
@Composable
fun CyberCodeBox(
    code: String,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 200.dp
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CyberCardBorder, RoundedCornerShape(8.dp))
            .background(CyberDark, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[RAW BUFFER / SCRIPT]",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan
                )

                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Code", code))
                        Toast.makeText(context, "Disalin ke Clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Code",
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = code,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                ),
                color = MatrixGreen,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
