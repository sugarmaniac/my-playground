package com.sosyaloyun.app.hesap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sosyaloyun.app.tema.Renk
import com.sosyaloyun.shared.Avatar

val Avatar.emoji: String
    get() = when (this) {
        Avatar.KUMANDA -> "🎮"
        Avatar.UZAYLI -> "👾"
        Avatar.EJDERHA -> "🐉"
        Avatar.ROKET -> "🚀"
        Avatar.TILKI -> "🦊"
        Avatar.KEDI -> "🐱"
        Avatar.PANDA -> "🐼"
        Avatar.BUYUCU -> "🧙"
        Avatar.ROBOT -> "🤖"
        Avatar.KUPA -> "🏆"
        Avatar.KILIC -> "⚔️"
        Avatar.HAYALET -> "👻"
    }

@Composable
fun AvatarGorunumu(avatar: Avatar, modifier: Modifier = Modifier, boyut: Dp = 48.dp, secili: Boolean = false) {
    Box(
        modifier = modifier
            .size(boyut)
            .background(
                if (secili) MaterialTheme.colorScheme.primaryContainer else Renk.avatarZemini(avatar.ordinal),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(avatar.emoji, fontSize = (boyut.value * 0.5f).sp)
    }
}
