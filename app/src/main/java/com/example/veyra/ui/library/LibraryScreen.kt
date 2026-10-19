package com.example.veyra.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.veyra.R
import com.example.veyra.ui.theme.VeyraTheme
import com.example.veyra.ui.theme.components.VeyraEmptyState
import com.example.veyra.ui.theme.components.VeyraSection

// Library section. In this phase it only shows the empty state; the track list
// (with artwork) will replace the empty state inside the same VeyraSection.
@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    VeyraSection(title = stringResource(R.string.library_title), modifier = modifier) {
        VeyraEmptyState(
            title = stringResource(R.string.library_empty_title),
            message = stringResource(R.string.library_empty_message),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF17181C)
@Composable
private fun LibraryScreenPreview() {
    VeyraTheme { LibraryScreen() }
}
