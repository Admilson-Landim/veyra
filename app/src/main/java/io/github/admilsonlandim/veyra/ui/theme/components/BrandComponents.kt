package io.github.admilsonlandim.veyra.ui.theme.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.admilsonlandim.veyra.R
import io.github.admilsonlandim.veyra.ui.theme.VeyraTheme
import io.github.admilsonlandim.veyra.ui.theme.VeyraWordmarkStyle

/** The spaced uppercase "VEYRA" wordmark in champagne. */
@Composable
fun VeyraWordmark(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.app_name).uppercase(),
        style = VeyraWordmarkStyle,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() },
    )
}

/** Brand header: wordmark with the slogan underneath. */
@Composable
fun VeyraHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        VeyraWordmark()
        Text(
            text = stringResource(R.string.brand_slogan),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Square brand tile with a centred icon, used as the illustration of empty states.
 * (Track covers use TrackArtwork.) Decorative: it carries no content description.
 */
@Composable
fun BrandTile(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    @DrawableRes icon: Int = R.drawable.ic_music_note,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.linearGradient(
                    listOf(colors.surfaceContainerHighest, colors.surfaceContainerLow),
                ),
            ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colors.primary.copy(alpha = 0.85f),
            modifier = Modifier.size(size * 0.4f),
        )
    }
}

/**
 * Centred empty state: brand tile with an icon, title and message, plus an optional action slot
 * (e.g. a permission button in a later phase).
 */
@Composable
fun VeyraEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ic_music_note,
    action: (@Composable () -> Unit)? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        ) {
            BrandTile(size = 112.dp, icon = icon)
            Spacer(Modifier.height(28.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (action != null) {
                Spacer(Modifier.height(24.dp))
                action()
            }
        }
    }
}

/**
 * A top-level section: a heading, with optional [actions] at its end (icon buttons), followed by
 * the section content filling the remaining space.
 */
@Composable
fun VeyraSection(
    title: String,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            actions?.invoke(this)
        }
        content()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF17181C)
@Composable
private fun BrandComponentsPreview() {
    VeyraTheme {
        Column(Modifier.padding(16.dp)) {
            VeyraHeader()
            Spacer(Modifier.height(16.dp))
            BrandTile()
        }
    }
}
